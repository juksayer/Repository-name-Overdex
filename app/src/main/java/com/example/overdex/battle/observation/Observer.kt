package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Common contract for producing observations from a specific sensing technology.
 * 
 * Implementations of [Observer] (e.g., OCR, Audio) are responsible for monitoring
 * the battle environment and submitting [Observation]s to the active session.
 */
interface Observer {
    /**
     * Unique identifier for this observer instance.
     */
    val observerId: ObserverId

    /**
     * Human-readable name of the observer.
     */
    val name: String

    /**
     * Starts the observer and directs its output to the given session.
     */
    fun start(session: ObservationSession)

    /**
     * Stops the observer and releases its resources.
     */
    fun stop()
}
