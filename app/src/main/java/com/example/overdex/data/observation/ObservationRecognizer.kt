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
     * @return A RecognitionResult containing the extracted value and confidence, or null if no recognizer exists.
     */
    suspend fun recognize(observation: CaptureObservation): RecognitionResult<*>? {
        return when (observation.regionId) {
            "CombatPower" -> CombatPowerRecognizer.recognize(observation.crop)
            "CandyPanel" -> CandyPanelSpeciesRecognizer.recognize(observation.crop)
            else -> null
        }
    }
}
