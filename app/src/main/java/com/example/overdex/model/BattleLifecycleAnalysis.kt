package com.example.overdex.model

/**
 * Assessment of a completed battle's technical and competitive lifecycle.
 */
data class BattleLifecycleAnalysis(
    val battleId: String,
    val isComplete: Boolean = false,
    val result: BattleResult = BattleResult.UNKNOWN,
    val durationSeconds: Long = 0,
    val factsRecorded: Int = 0
)
