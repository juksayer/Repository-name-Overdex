package com.example.overdex.model

/**
 * Placeholder for future battle outcome and lifecycle analysis results.
 */
data class BattleLifecycleAnalysis(
    val battleId: String,
    val isComplete: Boolean = false,
    val result: BattleResult = BattleResult.UNKNOWN,
    val durationSeconds: Long = 0,
    val factsRecorded: Int = 0
)
