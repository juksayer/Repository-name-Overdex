package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.overdex.battle.debug.observatory.DecisionCompetitor
import com.example.overdex.battle.debug.observatory.DecisionEvaluatedPayload
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.IntegrityCheckedPayload
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.debug.observatory.ProgressUpdatedPayload
import com.example.overdex.battle.debug.observatory.SystemEventPayload
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.observation.AnchorObservation
import com.example.overdex.model.observation.AnchorType
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.DefaultObservationResolver
import com.example.overdex.model.observation.Observation
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.ObservationObjective
import com.example.overdex.model.observation.ObservationSession
import com.example.overdex.model.observation.SessionPhase
import kotlinx.coroutines.CancellationException

/**
 * Represents the various stages of the guided observation sequence.
 */
sealed class ObservationStage(val label: String) {

    object Species : ObservationStage("Species")
    object CombatPower : ObservationStage("Combat Power")
    object ShadowStatus : ObservationStage("Shadow Status")
    object FastMove : ObservationStage("Fast Move")
    object ChargedMoveA : ObservationStage("Charged Move A")
    object ChargedMoveB : ObservationStage("Charged Move B")
    object Complete : ObservationStage("Observation Complete")

    companion object {
        val ALL = listOf(
             Species, CombatPower, ShadowStatus,
            FastMove, ChargedMoveA, ChargedMoveB, Complete,
        )
    }
}

/**
 * Status snapshot of the observation pipeline for UI reporting.
 */
data class PipelineStatus(
    val currentStage: ObservationStage,
    val completedStages: Set<ObservationStage> = emptySet(),
    val results: Map<String, List<Observation>> = emptyMap(),
    val captures: List<CaptureObservation> = emptyList(),
    val captureId: String = "",
    val session: ObservationSession? = null
)

/**
 * Manages the sequential, stage-based observation process for a trainer.
 * 
 * This engine coordinates the interaction between an [ObservationInput], a [CaptureTemplate],
 * and the [ObservationRecognizer]. it maintains session state, handles coordinate 
 * refinement using anchors, and broadcasts progress updates to the UI.
 * 
 * It is responsible for the "Guided" experience where the system moves through stages
 * like Species -> CP -> Moves.
 */
object GuidedObservationPipeline {

    private const val ANCHOR_CONFIDENCE_THRESHOLD = 0.8f
    private const val ANCHOR_PADDING_PX = 8

    private val defaultResolver = DefaultObservationResolver()

    /**
     * Executes the guided observation sequence.
     * 
     * @param input The source of visual evidence.
     * @param template The region definitions used for cropping.
     * @param existingSession An optional existing session to resume.
     * @param objective The goal of the observation (e.g., Register vs Identify).
     * @param onUpdate Callback invoked with each progress update.
     */
    suspend fun run(
        input: ObservationInput,
        template: CaptureTemplate,
        existingSession: ObservationSession? = null,
        objective: ObservationObjective = ObservationObjective.RegisterSpecimen,
        onUpdate: (PipelineStatus) -> Unit
    ) {
        // Architecture: Establish the ObservationSession foundation.
        // If no session exists, start a new canonical session.
        var session = existingSession ?: ObservationSession(source = input.source, objective = objective)
        val captureId = session.sessionId

        val completed = mutableSetOf<ObservationStage>()
        val allHistory = session.history.toMutableMap()
        val allCaptures = session.captures.toMutableList()

        // Observatory Causality: Record session start
        ObservationRecorder.record(
            EvidenceSourceType.SYSTEM,
            SystemEventPayload("SessionStarted", "Objective: ${objective.label}")
        )

        fun recordSessionDecisions(currentStage: String) {
            allHistory.forEach { (field, observations) ->
                val absoluteWinner = defaultResolver.resolve(observations)
                if (absoluteWinner != null) {
                    val competitors = observations.map { 
                        DecisionCompetitor(it.observerId, it.toString(), it.confidence.score)
                    }

                    ObservationRecorder.record(
                        EvidenceSourceType.DECISION,
                        DecisionEvaluatedPayload(
                            field = field,
                            winningValue = absoluteWinner.toString(),
                            winningConfidence = absoluteWinner.confidence.score,
                            competitors = competitors,
                            observationStage = currentStage
                        )
                    )
                }
            }
        }

        fun update(stage: ObservationStage) {
            val observationCount = allHistory.values.sumOf { it.size }

            // Sync session state for future architectural growth
            session = session.copy(
                captures = allCaptures.toList(),
                history = allHistory.toMap()
            )

            // Observatory: Record Decisions & Progress
            recordSessionDecisions(stage.label)

            val progress = session.evaluateProgress()
            
            // Observatory: Record Progress
            ObservationRecorder.record(
                EvidenceSourceType.DECISION,
                ProgressUpdatedPayload(
                    percentComplete = progress.percentComplete,
                    isComplete = progress.isComplete,
                    observationStage = stage.label
                )
            )

            android.util.Log.d("PIPELINE_INSTRUMENTATION", "Stage: ${stage.label} | Observations: $observationCount | Captures: ${allCaptures.size} | Progress: ${(progress.percentComplete * 100).toInt()}%")
            onUpdate(PipelineStatus(stage, completed.toSet(), allHistory.toMap(), allCaptures.toList(), captureId, session))
        }

        try {
            // Transition to ACTIVE state
            session = session.copy(state = SessionPhase.ACTIVE)
            android.util.Log.d("ODX_TRACE", "[$captureId][Session State] Lifecycle: ACTIVE")

            input.supply { bitmap ->
                TraceLogger.logCaptureStart(captureId, template.regions.size)
                android.util.Log.d("ODX_TRACE", "[$captureId][Session Info] Source: ${session.source}")

                // 1. Locate Anchors
                update(ObservationStage.Species)
                val detectedAnchors = SimpleAnchorDetector.detectAnchors(bitmap, ObservationStage.Species.label)
                completed.add(ObservationStage.Species)

                val regions = template.regions.associateBy { it.id }

                // 2. Species & Family
                update(ObservationStage.Species)
                val speciesRegion = regions["SpeciesName"]
                if (speciesRegion != null) {
                    processRegion(captureId, bitmap, speciesRegion, allCaptures, allHistory, detectedAnchors, ObservationStage.Species.label)
                }
                
                val candyRegion = regions["CandyPanel"]
                if (candyRegion != null) {
                    processRegion(captureId, bitmap, candyRegion, allCaptures, allHistory, detectedAnchors, ObservationStage.Species.label)
                }
                completed.add(ObservationStage.Species)

                // 3. Combat Power
                update(ObservationStage.CombatPower)
                val cpRegion = regions["CombatPower"]
                if (cpRegion != null) {
                    processRegion(captureId, bitmap, cpRegion, allCaptures, allHistory, detectedAnchors, ObservationStage.CombatPower.label)
                }
                completed.add(ObservationStage.CombatPower)

                // 4. Shadow Status
                update(ObservationStage.ShadowStatus)
                val fastMoveRegion = regions["FastMoveRow"] ?: regions["SummaryFastMove"]
                if (fastMoveRegion != null) {
                    processRegion(captureId, bitmap, fastMoveRegion, allCaptures, allHistory, detectedAnchors, ObservationStage.ShadowStatus.label)
                }
                completed.add(ObservationStage.ShadowStatus)

                // 5. Fast Move
                update(ObservationStage.FastMove)
                completed.add(ObservationStage.FastMove)

                // 6. Charged Move A
                update(ObservationStage.ChargedMoveA)
                val chgARegion = regions["ChargedMoveRowA"]
                if (chgARegion != null) {
                    processRegion(captureId, bitmap, chgARegion, allCaptures, allHistory, detectedAnchors, ObservationStage.ChargedMoveA.label)
                }
                completed.add(ObservationStage.ChargedMoveA)

                // 7. Charged Move B
                update(ObservationStage.ChargedMoveB)
                val chgBRegion = regions["ChargedMoveRowB"]
                if (chgBRegion != null) {
                    processRegion(captureId, bitmap, chgBRegion, allCaptures, allHistory, detectedAnchors, ObservationStage.ChargedMoveB.label)
                }
                completed.add(ObservationStage.ChargedMoveB)

                // One final update to complete
                update(ObservationStage.Complete)
            }

            // Check if objective is complete before finalizing state
            val progress = session.evaluateProgress()
            val integrity = session.evaluateIntegrity(defaultResolver)

            // Observatory: Record Integrity
            ObservationRecorder.record(
                EvidenceSourceType.DECISION,
                IntegrityCheckedPayload(
                    status = integrity.status.name,
                    resolvedFields = integrity.resolvedFields,
                    missingFields = integrity.missingFields,
                    conflictingFields = integrity.conflictingFields
                )
            )

            if (progress.isComplete) {
                session = session.copy(
                    completedAt = System.currentTimeMillis(),
                    state = SessionPhase.COMPLETED
                )
                android.util.Log.d("ODX_TRACE", "[$captureId][Session State] Lifecycle: COMPLETED")

                // Observatory Causality: Record session completion
                ObservationRecorder.record(
                    EvidenceSourceType.SYSTEM,
                    SystemEventPayload("SessionCompleted", "Progress: 100%")
                )
            } else {
                android.util.Log.d("ODX_TRACE", "[$captureId][Session State] Lifecycle: ACTIVE (Awaiting more evidence)")
            }

            // One final update to broadcast the current state
            onUpdate(PipelineStatus(ObservationStage.Complete, ObservationStage.ALL.toSet(), session.history, session.captures, captureId, session))

        } catch (e: CancellationException) {
            session = session.copy(state = SessionPhase.CANCELLED)
            android.util.Log.d("ODX_TRACE", "[$captureId][Session State] Lifecycle: CANCELLED")

            // Observatory Causality: Record session cancellation
            ObservationRecorder.record(
                EvidenceSourceType.SYSTEM,
                SystemEventPayload("SessionCancelled", "Reason: CancellationException")
            )

            onUpdate(PipelineStatus(ObservationStage.Complete, emptySet(), session.history, session.captures, captureId, session))
            throw e
        }
    }

    private suspend fun processRegion(
        captureId: String,
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        capturesList: MutableList<CaptureObservation>,
        historyMap: MutableMap<String, List<Observation>>,
        anchors: List<AnchorObservation>,
        stage: String = "UNKNOWN"
    ) {
        android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: ${region.id} | Processing...")
        
        val cap = crop(bitmap, region, anchors)
        capturesList.add(cap)
        val recognitionResults = ObservationRecognizer.recognize(cap, stage)
        
        // Map RecognitionResults to domain Observations
        val domainObservations = recognitionResults.mapNotNull { 
            RecognitionObservationMapper.map(region.id, it)
        }
        
        // Append to history
        val currentHistory = historyMap[region.id] ?: emptyList()
        historyMap[region.id] = currentHistory + domainObservations

        // Logging remains the same for recognizers
        val recognizers = recognitionResults.map { it.recognizer }.distinct()
        TraceLogger.logRegionComplete(captureId, region.id, recognizers, recognitionResults)
    }

    private fun crop(
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        anchors: List<AnchorObservation>
    ): CaptureObservation {
        val width = bitmap.width
        val height = bitmap.height

        val calLeft = (region.x * width).toInt().coerceIn(0, width - 1)
        val calTop = (region.y * height).toInt().coerceIn(0, height - 1)
        val calWidth = (region.width * width).toInt().coerceAtMost(width - calLeft)
        val calHeight = (region.height * height).toInt().coerceAtMost(height - calTop)

        var leftPx = calLeft
        val topPx = calTop
        val rightPx = calLeft + calWidth
        val bottomPx = calTop + calHeight

        val moveRegionIds = setOf("FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB", "SummaryFastMove")
        if (region.id in moveRegionIds) {
            val regionBounds = Rect(calLeft, calTop, rightPx, bottomPx)
            val moveAnchors = anchors.filter { anchor ->
                (anchor.confidence >= ANCHOR_CONFIDENCE_THRESHOLD) &&
                (anchor.type == AnchorType.MoveIcon || 
                 anchor.type == AnchorType.FastMoveIcon || 
                 anchor.type == AnchorType.ChargedMoveIcon) &&
                Rect.intersects(regionBounds, anchor.bounds)
            }
            val rightmostAnchorEdge = moveAnchors.maxOfOrNull { it.bounds.right }
            if (rightmostAnchorEdge != null) {
                val refinedLeft = rightmostAnchorEdge + ANCHOR_PADDING_PX
                if (refinedLeft > leftPx && refinedLeft < rightPx - 20) {
                    leftPx = refinedLeft
                }
            }
        }

        val cropWidth = (rightPx - leftPx).coerceAtMost(width - leftPx)
        val cropHeight = (bottomPx - topPx).coerceAtMost(height - topPx)

        return if (cropWidth > 0 && cropHeight > 0) {
            try {
                CaptureObservation(region.id, Bitmap.createBitmap(bitmap, leftPx, topPx, cropWidth, cropHeight))
            } catch (e: Exception) {
                createFallbackCrop(bitmap, region.id, calLeft, calTop, calWidth, calHeight)
            }
        } else {
            createFallbackCrop(bitmap, region.id, calLeft, calTop, calWidth, calHeight)
        }
    }

    private fun createFallbackCrop(bitmap: Bitmap, regionId: String, left: Int, top: Int, w: Int, h: Int): CaptureObservation {
        return if (w > 0 && h > 0) {
            try {
                CaptureObservation(regionId, Bitmap.createBitmap(bitmap, left, top, w, h))
            } catch (e: Exception) {
                CaptureObservation(regionId, bitmap)
            }
        } else {
            CaptureObservation(regionId, bitmap)
        }
    }
}
