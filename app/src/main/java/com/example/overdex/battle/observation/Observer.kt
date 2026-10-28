package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Common contract for producing observations from a specific sensing technology.
 * 
 * Implementations of [Observer] (e.g., OCR, Audio) are responsible for monitoring
 * the battle environment and submitting [Observation]s to the active match.
 */
interface Observer {
    /**
     * Unique identifier for this observer instance.
     */
    val observerId: ObserverId

    /**
     * Human-readable job or output-signal name, without a redundant Witness suffix.
     * The stable observerId remains separate for routing and archive compatibility.
     */
    val name: String

    /** True when this observer publishes its own phase-aware availability transitions. */
    val managesAvailability: Boolean get() = false

    /**
     * Starts the observer and directs its output to the given match.
     */
    fun start(match: Match)

    /**
     * Stops the observer and releases its resources.
     */
    fun stop()
}
