package com.example.overdex.data.observation

import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult

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
            "FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB" -> {
                results.add(MoveNameRecognizer.recognize(observation.crop))
                results.addAll(ShadowBonusRecognizer.recognize(observation.crop))
            }
        }
        
        return results
    }
}
