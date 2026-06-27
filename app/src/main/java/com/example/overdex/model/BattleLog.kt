package com.example.overdex.model

import com.example.overdex.EnemyPokemonMemory
import com.example.overdex.model.observation.ObservationSource
import java.util.UUID

enum class BattleResult {
    WIN,
    LOSS,
    DRAW,
    UNKNOWN
}

/**
 * A standardized snapshot of a completed battle.
 * This is the primary domain model for post-battle analysis, statistics, and history.
 */
data class BattleLog(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    
    // Core data reuse from existing models
    val enemyTeam: List<EnemyPokemonMemory>,
    val timeline: List<BattleEvent>,
    
    // Metadata for analysis
    val result: BattleResult = BattleResult.UNKNOWN,
    val overallConfidence: Float,
    val sourcesUsed: Set<ObservationSource>,
    
    // Captured technical stats
    val seenFastMoves: Map<String, Set<String>>,
    val seenChargedMoves: Map<String, Set<String>>
)
