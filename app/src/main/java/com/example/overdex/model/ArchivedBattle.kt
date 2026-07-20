package com.example.overdex.model

import java.util.UUID

/**
 * The permanent record of a completed engagement.
 * 
 * An [ArchivedBattle] serves as the "source of truth" for history and replays. It 
 * stores the raw factual timeline captured during the session.
 */
data class ArchivedBattle(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long = startTime,
    val timeline: List<BattleEvent>
)

/**
 * Derivation Engine: Factual analysis of the archived timeline.
 */
fun ArchivedBattle.getEnemyLeadId(): Int? =
    timeline.firstOrNull {
        it.actor == BattleActor.ENEMY &&
        (it.type == BattleEventType.POKEMON_IDENTIFIED ||
         it.type == BattleEventType.POKEMON_SWITCHED)
    }?.pokemonId
