package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult

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
    val observations: List<CaptureObservation> = emptyList()
)

object GuidedObservationPipeline {

    suspend fun run(
        bitmap: Bitmap,
        template: CaptureTemplate,
        onUpdate: (PipelineStatus) -> Unit
    ) {
        val completed = mutableSetOf<ObservationStage>()
        val allResults = mutableMapOf<String, List<RecognitionResult<*>>>()
        val allObservations = mutableListOf<CaptureObservation>()

        fun update(stage: ObservationStage) {
            onUpdate(PipelineStatus(stage, completed.toSet(), allResults.toMap(), allObservations.toList()))
        }

        // 1. Locate Anchors
        update(ObservationStage.LocatingAnchors)
        SimpleAnchorDetector.detectAnchors(bitmap)
        completed.add(ObservationStage.LocatingAnchors)

        val regions = template.regions.associateBy { it.id }

        // 2. Species
        update(ObservationStage.Species)
        (regions["SpeciesName"] ?: regions["CandyPanel"])?.let { region ->
            processRegion(bitmap, region, allObservations, allResults)
        }
        completed.add(ObservationStage.Species)

        // 3. Combat Power
        update(ObservationStage.CombatPower)
        regions["CombatPower"]?.let { processRegion(bitmap, it, allObservations, allResults) }
        completed.add(ObservationStage.CombatPower)

        // 4. Shadow Status
        update(ObservationStage.ShadowStatus)
        val fastMoveRegion = regions["FastMoveRow"] ?: regions["SummaryFastMove"]
        fastMoveRegion?.let { processRegion(bitmap, it, allObservations, allResults) }
        completed.add(ObservationStage.ShadowStatus)

        // 5. Fast Move
        update(ObservationStage.FastMove)
        completed.add(ObservationStage.FastMove)

        // 6. Charged Move A
        update(ObservationStage.ChargedMoveA)
        regions["ChargedMoveRowA"]?.let { processRegion(bitmap, it, allObservations, allResults) }
        completed.add(ObservationStage.ChargedMoveA)

        // 7. Charged Move B
        update(ObservationStage.ChargedMoveB)
        regions["ChargedMoveRowB"]?.let { processRegion(bitmap, it, allObservations, allResults) }
        completed.add(ObservationStage.ChargedMoveB)

        update(ObservationStage.Complete)
    }

    private suspend fun processRegion(
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        obsList: MutableList<CaptureObservation>,
        resultsMap: MutableMap<String, List<RecognitionResult<*>>>
    ) {
        if (resultsMap.containsKey(region.id)) return
        val obs = crop(bitmap, region)
        obsList.add(obs)
        resultsMap[obs.regionId] = ObservationRecognizer.recognize(obs)
    }

    private fun crop(bitmap: Bitmap, region: com.example.overdex.model.CaptureRegion): CaptureObservation {
        val left = (region.x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (region.y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = (region.width * bitmap.width).toInt().coerceAtMost(bitmap.width - left)
        val h = (region.height * bitmap.height).toInt().coerceAtMost(bitmap.height - top)
        return CaptureObservation(region.id, Bitmap.createBitmap(bitmap, left, top, w, h))
    }
}
