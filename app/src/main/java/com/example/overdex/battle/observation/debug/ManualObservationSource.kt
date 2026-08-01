package com.example.overdex.battle.observation.debug

import com.example.overdex.battle.observation.Observation
import com.example.overdex.battle.observation.Match

/**
 * Developer utility responsible for generating observations and injecting them
 * into an active [Match].
 *
 * This is a test producer for validating the pipeline, not a mock implementation
 * of future sensors.
 */
class ManualObservationSource {

    /**
     * Emits an observation into the provided match.
     */
    fun emit(observation: Observation, match: Match) {
        match.submit(observation)
    }
}
