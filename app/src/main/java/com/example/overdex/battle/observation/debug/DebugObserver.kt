package com.example.overdex.battle.observation.debug

import com.example.overdex.battle.observation.ObservationSession
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Simple [Observer] implementation for developer testing.
 * Emits a set of synthetic observations when started.
 */
class DebugObserver(
    override val observerId: ObserverId = ObserverId("DEBUG_OBSERVER", ObservationSource.SYSTEM),
    override val name: String = "Debug Observer"
) : Observer {

    override fun start(session: ObservationSession) {
        // Emit visual observation
        session.submit(
            ObservationFactory.createVisualObservation(
                sourceId = observerId.id,
                frameUri = "uri://frames/debug_001"
            )
        )

        // Emit state observation
        session.submit(
            ObservationFactory.createStateObservation(
                sourceId = observerId.id,
                key = "BATTLE_STATE",
                value = "STARTED"
            )
        )
    }

    override fun stop() {
        // No-op for debug observer
    }
}
