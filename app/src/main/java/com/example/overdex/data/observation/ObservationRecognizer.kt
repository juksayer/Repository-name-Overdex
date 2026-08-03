package com.example.overdex.data.observation

import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.AnchorObservation
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.debug.observatory.RecognitionAttemptPayload
import com.example.overdex.battle.debug.observatory.VisionCapturePayload

/**
 * Entry point for the recognition pipeline.
 * 
 * Routes captured [CaptureObservation]s to specialized recognizers based on their region ID.
 * It also handles the recording of recognition attempts to the [ObservationRecorder].
 */
object ObservationRecognizer {
    
    /**
     * Recognizes structured data from a capture observation.
     * 
     * @param observation The raw visual evidence captured from a screen region.
     * @param stage The current stage of the observation pipeline (for logging/debugging).
     * @return A list of [RecognitionResult]s containing the extracted values.
     */
    suspend fun recognize(observation: CaptureObservation, stage: String = "UNKNOWN"): List<RecognitionResult<*>> {
        val results = mutableListOf<RecognitionResult<*>>()
        
        // observatory causality: Record the capture attempt
        ObservationRecorder.record(
            EvidenceSourceType.VISION,
            VisionCapturePayload(
                regionId = observation.regionId,
                width = observation.crop.width,
                height = observation.crop.height,
                observationStage = stage
            )
        )

        // Brick #121: Anchor Detection
        // Detect anchors within the current crop and expose them to the pipeline.
        val anchors = SimpleAnchorDetector.detectAnchors(observation.crop, stage)
        anchors.forEach { anchor ->
            results.add(
                RecognitionResult(
                    value = anchor,
                    confidence = anchor.confidence,
                    recognizer = "SimpleAnchorDetector"
                )
            )
        }
        
        when (observation.regionId) {
            "SpeciesName" -> {
                recordAttempt(observation.regionId, stage, SpeciesNameRecognizer.recognize(observation.crop))?.let { results.add(it) }
            }
            "CombatPower" -> {
                recordAttempt(observation.regionId, stage, CombatPowerRecognizer.recognize(observation.crop))?.let { results.add(it) }
            }
            "CandyPanel" -> {
                recordAttempt(observation.regionId, stage, CandyPanelFamilyRecognizer.recognize(observation.crop))?.let { results.add(it) }
            }
            "FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB", "SummaryFastMove" -> {
                recordAttempt(observation.regionId, stage, MoveNameRecognizer.recognize(observation.crop))?.let { results.add(it) }
                // Note: ShadowBonusRecognizer returns a list
                val shadowResults = ShadowBonusRecognizer.recognize(observation.crop)
                shadowResults.forEach { res ->
                    recordAttempt(observation.regionId, stage, res)?.let { results.add(it) }
                }
            }
        }
        
        return results.filter { it.confidence > 0 }
    }

    private fun recordAttempt(regionId: String, stage: String, result: RecognitionResult<*>): RecognitionResult<*>? {
        val success = result.confidence > 0
        
        ObservationRecorder.record(
            EvidenceSourceType.VISION,
            RecognitionAttemptPayload(
                regionId = regionId,
                recognizerName = result.recognizer,
                success = success,
                resultValue = result.value?.toString(),
                confidence = result.confidence,
                observationStage = stage
            )
        )
        
        return if (success) result else null
    }
}
