package com.example.overdex.model

/**
 * Qualitative classification of certainty levels for data in the model package.
 */
enum class ConfidenceLevel {
    /** Data predicted by logic (e.g. backline prediction, energy estimation). */
    INFERRED,
    /** Data directly extracted from visual or audio evidence (e.g. OCR). */
    OBSERVED
}

/**
 * Represents the system's certainty regarding a specific piece of data.
 * 
 * @property level The semantic classification of the confidence.
 * @property score The numerical certainty from 0.0 (none) to 1.0 (absolute).
 */
data class Confidence(
    val level: ConfidenceLevel,
    val score: Float = if (level == ConfidenceLevel.OBSERVED) 1.0f else 0.5f
)
