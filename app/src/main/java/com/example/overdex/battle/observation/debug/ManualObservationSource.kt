package com.example.overdex.battle.observation.debug

import com.example.overdex.battle.observation.Observation
import com.example.overdex.battle.observation.ObservationSession

/**
 * Developer utility responsible for generating observations and injecting them
 * into an active [ObservationSession].
 *
 * This is a test producer for validating the pipeline, not a mock implementation
 * of future sensors.
 */
class ManualObservationSource {

    /**
     * Emits an observation into the provided session.
     */
    fun emit(observation: Observation, session: ObservationSession) {
        session.submit(observation)
    }
}
