package com.example.overdex

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.data.observation.BattleObservationPipeline
import com.example.overdex.model.*
import com.example.overdex.model.observation.ObservationSource
import com.example.overdex.model.observation.PokemonNameObservation
import kotlinx.coroutines.delay

/**
 * The mutable runtime memory of an active battle session.
 * 
 * BattleMemory accumulates observations, tracks estimated energy levels, and 
 * maintains the state of both the player's and the opponent's teams during 
 * an engagement.
 */
data class BattleMemory(
    val seenPokemon: MutableSet<String> = mutableSetOf(),
    val seenFastMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val seenChargedMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val observedEnemyPokemon: MutableList<String> = mutableListOf(),
    val enemyTeam: MutableList<EnemyPokemonMemory> = mutableStateListOf<EnemyPokemonMemory>(),
    var enemyRemainingPokemon: Int? = null,
    val battleHistory: MutableList<BattleEvent> = mutableListOf<BattleEvent>(),
    var playerActivePokemon: String? = null,
    val playerTeam: MutableList<String> = mutableStateListOf<String>(),
    var startTime: Long = System.currentTimeMillis(),
    var playerLead: String? = null,
    var enemyLead: String? = null,
    var playerShieldsUsed: Int = 0,
    var enemyShieldsUsed: Int = 0,
    val timeline: BattleTimeline = BattleTimeline()
) {


    fun recordEvent(event: BattleEvent) {
        timeline.record(event)
        battleHistory.add(event) // Preserves current battle event history
        deriveState(event)
    }

    private fun deriveState(event: BattleEvent) {
        when (event.type) {
            BattleEventType.SHIELD_USED -> {
                if (event.actor == BattleActor.PLAYER) {
                    playerShieldsUsed++
                } else if (event.actor == BattleActor.ENEMY) {
                    enemyShieldsUsed++
                }
            }
            BattleEventType.POKEMON_FAINTED -> {
                if (event.actor == BattleActor.ENEMY) {
                    val activeIndex = enemyTeam.indexOfFirst { it.isActive }
                    if (activeIndex != -1) {
                        enemyTeam[activeIndex] = enemyTeam[activeIndex].copy(
                            alive = false,
                            isActive = false
                        )
                    }
                }
            }
            BattleEventType.POKEMON_SWITCHED -> {
                if (event.actor == BattleActor.PLAYER) {
                    playerActivePokemon = event.message
                } else if (event.actor == BattleActor.ENEMY) {
                    val species = event.message
                    for (i in enemyTeam.indices) {
                        val isTarget = enemyTeam[i].species == species
                        if (enemyTeam[i].isActive != isTarget) {
                            enemyTeam[i] = enemyTeam[i].copy(isActive = isTarget)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun recordEvent(
        type: BattleEventType,
        actor: BattleActor = BattleActor.SYSTEM,
        pokemonId: Int? = null,
        value: Int? = null,
        message: String? = null,
        confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
    ) {
        recordEvent(
            BattleEvent(
                type = type,
                actor = actor,
                pokemonId = pokemonId,
                value = value,
                message = message,
                confidence = confidence
            )
        )
    }

    private fun updateSpecies(species: String, block: (EnemyPokemonMemory) -> Unit) {
        val index = enemyTeam.indexOfFirst { it.species == species }
        if (index != -1) {
            val updated = enemyTeam[index].copy()
            block(updated)
            enemyTeam[index] = updated
        }
    }

    /**
     * Converts current battle memory into a finalized BattleLog snapshot.
     */
    fun toBattleLog(): BattleLog {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        return BattleLog(
            startTime = startTime,
            endTime = endTime,
            durationMs = duration,
            playerTeam = playerTeam.toList(),
            enemyTeam = enemyTeam.toList(),
            timeline = timeline.events.toList(), // Now using the new timeline
            playerLead = playerLead,
            enemyLead = enemyLead,
            playerShieldsUsed = playerShieldsUsed,
            enemyShieldsUsed = enemyShieldsUsed,
            result = if (enemyTeam.isNotEmpty() && enemyTeam.all { !it.alive }) {
                BattleResult.WIN
            } else {
                BattleResult.UNKNOWN
            },
            overallConfidence = 0.0f,
            sourcesUsed = emptySet(),
            seenFastMoves = seenFastMoves.toMap(),
            seenChargedMoves = seenChargedMoves.toMap()
        )
    }
}
