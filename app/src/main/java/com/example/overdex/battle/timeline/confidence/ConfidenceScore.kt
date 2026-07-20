package com.example.overdex.battle.timeline.confidence

/**
 * Models the numerical confidence level of an observation or event.
 * 
 * @property value The raw confidence score from 0.0 to 1.0.
 * @property level A semantic classification of the confidence (e.g., INFERRED, OBSERVED).
 */
data class ConfidenceScore(
    val value: Float,
    val level: ConfidenceLevel
)
