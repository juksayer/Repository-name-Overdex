package com.example.overdex.model

import com.example.overdex.EnemyPokemonMemory
import com.example.overdex.model.observation.ObservationSource
import java.util.UUID

/**
 * Defines the possible outcomes of a battle session.
 */
enum class BattleResult {
    /** The player achieved victory. */
    WIN,
    /** The player was defeated. */
    LOSS,
    /** The battle ended in a tie. */
    DRAW,
    /** The outcome could not be determined. */
    UNKNOWN
}

/**
 * A standardized snapshot of a completed battle.
 * 
 * This is the primary domain model for the History and Archive layers. It encapsulates
 * all essential facts, events, and results of a battle for long-term storage and analysis.
 */
data class BattleLog(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    
    // Core data reuse from existing models
    /** The species names of the player's team members. */
    val playerTeam: List<String>,
    /** The remembered state of the opponent's team. */
    val enemyTeam: List<EnemyPokemonMemory>,
    /** The chronological list of events that occurred during the battle. */
    val timeline: List<BattleEvent>,
    
    /** The species name of the player's starting Pokémon. */
    val playerLead: String?,
    /** The species name of the opponent's starting Pokémon. */
    val enemyLead: String?,

    /** Total number of shields used by the player. */
    val playerShieldsUsed: Int,
    /** Total number of shields used by the opponent. */
    val enemyShieldsUsed: Int,

    /** The determined outcome of the battle. */
    val result: BattleResult = BattleResult.UNKNOWN,
    /** Overall confidence score in the accuracy of this log. */
    val overallConfidence: Float,
    /** The set of observation sources that contributed to this log. */
    val sourcesUsed: Set<ObservationSource>,
    
    /** Map of opponent species to the set of fast moves observed for them. */
    val seenFastMoves: Map<String, Set<String>>,
    /** Map of opponent species to the set of charged moves observed for them. */
    val seenChargedMoves: Map<String, Set<String>>
)
