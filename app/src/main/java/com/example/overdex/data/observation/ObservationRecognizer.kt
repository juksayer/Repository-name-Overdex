package com.example.overdex.data.observation

import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.AnchorObservation

/**
 * Entry point for the recognition pipeline.
 * Routes captured observations to specialized recognizers based on their region ID.
 */
object ObservationRecognizer {
    
    /**
     * Recognizes structured data from a capture observation.
     * @return A list of RecognitionResults containing the extracted values.
     */
    suspend fun recognize(observation: CaptureObservation): List<RecognitionResult<*>> {
        val results = mutableListOf<RecognitionResult<*>>()
        
        // Brick #121: Anchor Detection
        // Detect anchors within the current crop and expose them to the pipeline.
        val anchors = SimpleAnchorDetector.detectAnchors(observation.crop)
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
                results.add(SpeciesNameRecognizer.recognize(observation.crop))
            }
            "CombatPower" -> {
                results.add(CombatPowerRecognizer.recognize(observation.crop))
            }
            "CandyPanel" -> {
                results.add(CandyPanelSpeciesRecognizer.recognize(observation.crop))
            }
            "FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB", "SummaryFastMove" -> {
                results.add(MoveNameRecognizer.recognize(observation.crop))
                results.addAll(ShadowBonusRecognizer.recognize(observation.crop))
            }
        }
        
        return results.filter { it.confidence > 0 }
    }
}
