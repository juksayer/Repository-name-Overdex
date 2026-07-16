package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.observation.*

/**
 * Represents the various stages of the guided observation sequence.
 */
sealed class ObservationStage(val label: String) {
    object LocatingAnchors : ObservationStage("Locating Anchors")
    object Species : ObservationStage("Species")
    object CombatPower : ObservationStage("Combat Power")
    object ShadowStatus : ObservationStage("Shadow Status")
    object FastMove : ObservationStage("Fast Move")
    object ChargedMoveA : ObservationStage("Charged Move A")
    object ChargedMoveB : ObservationStage("Charged Move B")
    object Complete : ObservationStage("Observation Complete")

    companion object {
        val ALL = listOf(
            LocatingAnchors, Species, CombatPower, ShadowStatus,
            FastMove, ChargedMoveA, ChargedMoveB, Complete
        )
    }
}

/**
 * Status snapshot of the observation pipeline for UI reporting.
 */
data class PipelineStatus(
    val currentStage: ObservationStage,
    val completedStages: Set<ObservationStage> = emptySet(),
    val results: Map<String, List<RecognitionResult<*>>> = emptyMap(),
    val observations: List<CaptureObservation> = emptyList(),
    val captureId: String = "",
    val session: ObservationSession? = null
)

object GuidedObservationPipeline {

    private const val ANCHOR_CONFIDENCE_THRESHOLD = 0.8f
    private const val ANCHOR_PADDING_PX = 8

    suspend fun run(
        bitmap: Bitmap,
        template: CaptureTemplate,
        existingSession: ObservationSession? = null,
        onUpdate: (PipelineStatus) -> Unit
    ) {
        // Architecture: Establish the ObservationSession foundation.
        // If no session exists, start a new canonical session.
        var session = existingSession ?: ObservationSession(source = SessionSource.SCREENSHOT)
        val captureId = session.sessionId

        val completed = mutableSetOf<ObservationStage>()
        val allResults = mutableMapOf<String, List<RecognitionResult<*>>>()
        val allObservations = mutableListOf<CaptureObservation>()

        TraceLogger.logCaptureStart(captureId, template.regions.size)
        android.util.Log.d("ODX_TRACE", "[$captureId][Session Info] Source: ${session.source}")

        fun update(stage: ObservationStage) {
            val resultsCount = allResults.values.sumOf { it.size }
            
            // Sync session state for future architectural growth
            session = session.copy(
                observations = allObservations.toList(),
                recognitionResults = allResults.toMap()
            )

            android.util.Log.d("PIPELINE_INSTRUMENTATION", "Stage: ${stage.label} | Results: $resultsCount | Observations: ${allObservations.size}")
            onUpdate(PipelineStatus(stage, completed.toSet(), allResults.toMap(), allObservations.toList(), captureId, session))
        }

        // 1. Locate Anchors
        update(ObservationStage.LocatingAnchors)
        val detectedAnchors = SimpleAnchorDetector.detectAnchors(bitmap)
        completed.add(ObservationStage.LocatingAnchors)

        val regions = template.regions.associateBy { it.id }

        // 2. Species & Family
        update(ObservationStage.Species)
        val speciesRegion = regions["SpeciesName"]
        if (speciesRegion != null) {
            processRegion(captureId, bitmap, speciesRegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: SpeciesName | MISSING (Not in template)")
        }
        
        val candyRegion = regions["CandyPanel"]
        if (candyRegion != null) {
            processRegion(captureId, bitmap, candyRegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: CandyPanel | MISSING (Not in template)")
        }
        completed.add(ObservationStage.Species)

        // 3. Combat Power
        update(ObservationStage.CombatPower)
        val cpRegion = regions["CombatPower"]
        if (cpRegion != null) {
            processRegion(captureId, bitmap, cpRegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: CombatPower | MISSING (Not in template)")
        }
        completed.add(ObservationStage.CombatPower)

        // 4. Shadow Status
        update(ObservationStage.ShadowStatus)
        val fastMoveRegion = regions["FastMoveRow"] ?: regions["SummaryFastMove"]
        if (fastMoveRegion != null) {
            processRegion(captureId, bitmap, fastMoveRegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: FastMoveRow/SummaryFastMove | MISSING (Not in template)")
        }
        completed.add(ObservationStage.ShadowStatus)

        // 5. Fast Move
        update(ObservationStage.FastMove)
        completed.add(ObservationStage.FastMove)

        // 6. Charged Move A
        update(ObservationStage.ChargedMoveA)
        val chgARegion = regions["ChargedMoveRowA"]
        if (chgARegion != null) {
            processRegion(captureId, bitmap, chgARegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: ChargedMoveRowA | MISSING (Not in template)")
        }
        completed.add(ObservationStage.ChargedMoveA)

        // 7. Charged Move B
        update(ObservationStage.ChargedMoveB)
        val chgBRegion = regions["ChargedMoveRowB"]
        if (chgBRegion != null) {
            processRegion(captureId, bitmap, chgBRegion, allObservations, allResults, detectedAnchors)
        } else {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: ChargedMoveRowB | MISSING (Not in template)")
        }
        completed.add(ObservationStage.ChargedMoveB)

        // LOG: RecognitionResults
        val res_spec = allResults["SpeciesName"]?.firstOrNull()?.value?.toString() ?: "MISSING"
        val res_fam = allResults["CandyPanel"]?.firstOrNull()?.value?.toString() ?: "MISSING"
        val res_cp = allResults["CombatPower"]?.firstOrNull()?.value?.toString() ?: "MISSING"
        val res_fm = (allResults["FastMoveRow"] ?: allResults["SummaryFastMove"])?.firstOrNull()?.value?.toString() ?: "MISSING"
        val res_cma = allResults["ChargedMoveRowA"]?.firstOrNull()?.value?.toString() ?: "MISSING"
        val res_cmb = allResults["ChargedMoveRowB"]?.firstOrNull()?.value?.toString() ?: "MISSING"
        
        TraceLogger.logStage(
            captureId = captureId,
            stage = "RecognitionResults",
            species = res_spec,
            family = res_fam,
            cp = res_cp,
            fast = res_fm,
            chgA = res_cma,
            chgB = res_cmb
        )

        // LOG: Observations
        TraceLogger.logStage(
            captureId = captureId,
            stage = "Observations",
            species = res_spec,
            family = res_fam,
            cp = res_cp,
            fast = res_fm,
            chgA = res_cma,
            chgB = res_cmb
        )

        // Finalize session state
        session = session.copy(
            completedAt = System.currentTimeMillis(),
            completionState = SessionCompletionState.COMPLETED
        )

        update(ObservationStage.Complete)
    }

    private suspend fun processRegion(
        captureId: String,
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        obsList: MutableList<CaptureObservation>,
        resultsMap: MutableMap<String, List<RecognitionResult<*>>>,
        anchors: List<AnchorObservation>
    ) {
        if (resultsMap.containsKey(region.id)) {
            android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: ${region.id} | SKIPPED (Already processed)")
            return
        }
        
        android.util.Log.d("ODX_TRACE", "[$captureId][OCR Region] ID: ${region.id} | Processing...")
        
        val obs = crop(bitmap, region, anchors)
        obsList.add(obs)
        val results = ObservationRecognizer.recognize(obs)
        resultsMap[obs.regionId] = results

        val recognizers = when (region.id) {
            "SpeciesName" -> listOf("SpeciesNameRecognizer")
            "CombatPower" -> listOf("CombatPowerRecognizer")
            "CandyPanel" -> listOf("CandyPanelFamilyRecognizer")
            "FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB", "SummaryFastMove" -> listOf("MoveNameRecognizer", "ShadowBonusRecognizer")
            else -> listOf("Unknown")
        }
        TraceLogger.logRegionComplete(captureId, region.id, recognizers, results)
    }

    private fun crop(
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        anchors: List<AnchorObservation>
    ): CaptureObservation {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Base calibrated bounds (Fallback target)
        val calLeft = (region.x * width).toInt().coerceIn(0, width - 1)
        val calTop = (region.y * height).toInt().coerceIn(0, height - 1)
        val calWidth = (region.width * width).toInt().coerceAtMost(width - calLeft)
        val calHeight = (region.height * height).toInt().coerceAtMost(height - calTop)

        // Target bounds for cropping (initially matching calibration)
        var leftPx = calLeft
        val topPx = calTop
        var rightPx = calLeft + calWidth
        val bottomPx = calTop + calHeight

        // 2. Brick #121.5: Anchor-Based Refinement for Move Regions
        val moveRegionIds = setOf("FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB", "SummaryFastMove")
        if (region.id in moveRegionIds) {
            val regionBounds = Rect(calLeft, calTop, rightPx, bottomPx)
            
            // Find high-confidence move-related anchors that intersect with this region
            val moveAnchors = anchors.filter { anchor ->
                anchor.confidence >= ANCHOR_CONFIDENCE_THRESHOLD &&
                (anchor.type == AnchorType.MoveIcon || 
                 anchor.type == AnchorType.FastMoveIcon || 
                 anchor.type == AnchorType.ChargedMoveIcon) &&
                Rect.intersects(regionBounds, anchor.bounds)
            }

            // Shift left edge to immediately after the rightmost relevant anchor + padding
            val rightmostAnchorEdge = moveAnchors.maxOfOrNull { it.bounds.right }
            if (rightmostAnchorEdge != null) {
                val refinedLeft = rightmostAnchorEdge + ANCHOR_PADDING_PX
                // Shift if it actually moves us forward but leaves enough room for text
                if (refinedLeft > leftPx && refinedLeft < rightPx - 20) {
                    leftPx = refinedLeft
                }
            }
        }

        val cropWidth = (rightPx - leftPx).coerceAtMost(width - leftPx)
        val cropHeight = (bottomPx - topPx).coerceAtMost(height - topPx)

        // 3. Execute Crop with Fallback
        return if (cropWidth > 0 && cropHeight > 0) {
            try {
                CaptureObservation(region.id, Bitmap.createBitmap(bitmap, leftPx, topPx, cropWidth, cropHeight))
            } catch (e: Exception) {
                android.util.Log.e("GUIDED_PIPELINE", "Refined crop failed for ${region.id}, falling back", e)
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
                android.util.Log.e("GUIDED_PIPELINE", "Total crop failure for $regionId", e)
                CaptureObservation(regionId, bitmap) // Emergency fallback to original
            }
        } else {
            CaptureObservation(regionId, bitmap)
        }
    }
}
