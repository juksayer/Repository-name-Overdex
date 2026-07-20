package com.example.overdex.battle.timeline.confidence

/**
 * Qualitative classification of confidence scores used to categorize the 
 * reliability of an event.
 */
enum class ConfidenceLevel {
    /** The system is unsure and the event may be a false positive. */
    UNCERTAIN,
    /** The event was directly detected from raw evidence (e.g., OCR result). */
    OBSERVED,
    /** The event has been confirmed by a high-confidence secondary source or trainer. */
    CONFIRMED,
    /** The event was derived by reconciling multiple competing observations. */
    RECONCILED
}
