package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.AnchorObservation
import com.example.overdex.model.observation.AnchorType

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

    private const val ANCHOR_CONFIDENCE_THRESHOLD = 0.8f
    private const val ANCHOR_PADDING_PX = 8

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
        val detectedAnchors = SimpleAnchorDetector.detectAnchors(bitmap)
        completed.add(ObservationStage.LocatingAnchors)

        val regions = template.regions.associateBy { it.id }

        // 2. Species
        update(ObservationStage.Species)
        (regions["SpeciesName"] ?: regions["CandyPanel"])?.let { region ->
            processRegion(bitmap, region, allObservations, allResults, detectedAnchors)
        }
        completed.add(ObservationStage.Species)

        // 3. Combat Power
        update(ObservationStage.CombatPower)
        regions["CombatPower"]?.let { processRegion(bitmap, it, allObservations, allResults, detectedAnchors) }
        completed.add(ObservationStage.CombatPower)

        // 4. Shadow Status
        update(ObservationStage.ShadowStatus)
        val fastMoveRegion = regions["FastMoveRow"] ?: regions["SummaryFastMove"]
        fastMoveRegion?.let { processRegion(bitmap, it, allObservations, allResults, detectedAnchors) }
        completed.add(ObservationStage.ShadowStatus)

        // 5. Fast Move
        update(ObservationStage.FastMove)
        completed.add(ObservationStage.FastMove)

        // 6. Charged Move A
        update(ObservationStage.ChargedMoveA)
        regions["ChargedMoveRowA"]?.let { processRegion(bitmap, it, allObservations, allResults, detectedAnchors) }
        completed.add(ObservationStage.ChargedMoveA)

        // 7. Charged Move B
        update(ObservationStage.ChargedMoveB)
        regions["ChargedMoveRowB"]?.let { processRegion(bitmap, it, allObservations, allResults, detectedAnchors) }
        completed.add(ObservationStage.ChargedMoveB)

        update(ObservationStage.Complete)
    }

    private suspend fun processRegion(
        bitmap: Bitmap,
        region: com.example.overdex.model.CaptureRegion,
        obsList: MutableList<CaptureObservation>,
        resultsMap: MutableMap<String, List<RecognitionResult<*>>>,
        anchors: List<AnchorObservation>
    ) {
        if (resultsMap.containsKey(region.id)) return
        val obs = crop(bitmap, region, anchors)
        obsList.add(obs)
        resultsMap[obs.regionId] = ObservationRecognizer.recognize(obs)
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
