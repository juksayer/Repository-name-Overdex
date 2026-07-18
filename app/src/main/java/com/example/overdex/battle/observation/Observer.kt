package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Common contract for producing observations.
 * Future recognition systems (OCR, Audio, etc.) will implement this interface.
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
     * Starts the observer for the given session.
     */
    fun start(session: ObservationSession)

    /**
     * Stops the observer.
     */
    fun stop()
}
