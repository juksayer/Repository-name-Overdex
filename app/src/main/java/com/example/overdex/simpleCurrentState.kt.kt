package com.example.overdex

/**
 * A wrapper for a value that includes metadata about how it was derived.
 */
data class ExplainedValue<T>(
    val value: T,
    val derivation: DerivationStatus,
    val lastAnchorTime: Long
)

/**
 * Defines the provenance of a value in the intelligence layer.
 */
enum class DerivationStatus {
    /** The value was directly observed in the most recent capture. */
    ANCHORED,
    /** The value was estimated based on historical trends or rules. */
    SPECULATIVE,
    /** a previous guess was corrected by new evidence. */
    RECONCILED
}
