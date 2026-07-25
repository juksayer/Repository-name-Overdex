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
    var playerActivePokemon: String? = "Charizard", // Prototype default
    val playerTeam: MutableList<String> = mutableStateListOf("Charizard", "Venusaur", "Blastoise"),
    val battleHistory: MutableList<BattleEvent> = mutableStateListOf<BattleEvent>(),
    var startTime: Long = System.currentTimeMillis(),
    var playerLead: String? = null,
    var enemyLead: String? = null,
    var playerShieldsUsed: Int = 0,
    var enemyShieldsUsed: Int = 0,
    val timeline: BattleTimeline = BattleTimeline()
) {


    private fun recordEvent(
        type: BattleEventType,
        actor: BattleActor = BattleActor.SYSTEM,
        pokemonId: Int? = null,
        value: Int? = null,
        message: String? = null,
        confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
    ) {
        val event = BattleEvent(
            type = type,
            actor = actor,
            pokemonId = pokemonId,
            value = value, 
            message = message,
            confidence = confidence
        )
        timeline.record(event)
        battleHistory.add(event) // Keeping for BattleLog until it's refactored
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
            result = if (enemyTeam.all { !it.alive }) BattleResult.WIN else BattleResult.UNKNOWN,
            overallConfidence = 0.9f,
            sourcesUsed = setOf(ObservationSource.PROTOTYPE),
            seenFastMoves = seenFastMoves.toMap(),
            seenChargedMoves = seenChargedMoves.toMap()
        )
    }
}
