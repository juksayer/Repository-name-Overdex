package com.example.overdex.battle.timeline.observer

/**
 * Uniquely identifies an observer within the battle session.
 */
data class ObserverId(
    val id: String,
    val source: ObservationSource
)
