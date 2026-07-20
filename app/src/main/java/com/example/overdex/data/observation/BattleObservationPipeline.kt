package com.example.overdex.data.observation

import com.example.overdex.BattleMemory
import com.example.overdex.BattleMemoryUpdater
import com.example.overdex.model.observation.Observation

/**
 * An event-driven pipeline that routes observations into [BattleMemory].
 * 
 * This component acts as the bridge between the Observation Layer and the Memory Layer.
 * It performs basic filtering (e.g., confidence thresholds) before updating the 
 * active battle state.
 */
class BattleObservationPipeline(
    private val memory: BattleMemory
) {
    /**
     * Processes an incoming observation and updates the battle memory if it passes validation.
     */
    fun onObservationReceived(observation: Observation) {
        // Validation could occur here (e.g. ignoring low confidence observations)
        if (observation.confidence.score < 0.3f) return
        
        // Forward to updater
        BattleMemoryUpdater.processObservation(observation, memory)
    }
}
