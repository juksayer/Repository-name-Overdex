package com.example.overdex.battle.timeline.confidence

/**
 * Models the numerical confidence level of an observation or event.
 */
data class ConfidenceScore(
    val value: Float,
    val level: ConfidenceLevel
)
