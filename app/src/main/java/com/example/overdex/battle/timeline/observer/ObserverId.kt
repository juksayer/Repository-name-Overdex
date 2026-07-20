package com.example.overdex.battle.timeline.observer

/**
 * Uniquely identifies an observer within a battle session.
 * 
 * @property id The specific instance ID of the observer.
 * @property source The type of technology used by this observer (e.g., OCR, AUDIO).
 */
data class ObserverId(
    val id: String,
    val source: ObservationSource
)
