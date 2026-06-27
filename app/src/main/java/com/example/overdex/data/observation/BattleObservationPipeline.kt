package com.example.overdex.data.observation

import com.example.overdex.BattleMemory
import com.example.overdex.BattleMemoryUpdater
import com.example.overdex.model.observation.Observation

class BattleObservationPipeline(
    private val memory: BattleMemory
) {
    fun onObservationReceived(observation: Observation) {
        // Validation could occur here (e.g. ignoring low confidence observations)
        if (observation.confidence.score < 0.3f) return
        
        // Forward to updater
        BattleMemoryUpdater.processObservation(observation, memory)
    }
}
