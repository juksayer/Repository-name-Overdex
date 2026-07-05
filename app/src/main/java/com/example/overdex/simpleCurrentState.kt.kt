package com.example.overdex

data class ExplainedValue<T>(
    val value: T,
    val derivation: DerivationStatus,
    val lastAnchorTime: Long
)

enum class DerivationStatus {
    ANCHORED,    // I see it right now.
    SPECULATIVE, // I'm guessing based on the last time I saw it.
    RECONCILED   // I just fixed a guess that was wrong.
}