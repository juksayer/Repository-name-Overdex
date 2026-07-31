package com.example.overdex

import com.example.overdex.model.observation.Observation
import com.example.overdex.model.observation.PokemonNameObservation
import com.example.overdex.model.observation.FastMoveObservation
import com.example.overdex.model.observation.ChargedMoveObservation
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel

/**
 * Responsible for applying [Observation] facts and [BattleState] snapshots
 * to a [BattleMemory] instance.
 */
object BattleMemoryUpdater {

    fun processObservation(observation: Observation, memory: BattleMemory) {
        when (observation) {
            is PokemonNameObservation -> {
                updateEnemyPokemon(observation.species, memory, observation.confidence)
            }
            is FastMoveObservation -> {
                memory.seenFastMoves
                    .getOrPut(observation.species) { mutableSetOf() }
                    .add(observation.moveName)
            }
            is ChargedMoveObservation -> {
                memory.seenChargedMoves
                    .getOrPut(observation.species) { mutableSetOf() }
                    .add(observation.moveName)
            }
            else -> {
                // Ignore CP and Shadow observations for battle memory for now
            }
        }
    }

    private fun updateEnemyPokemon(species: String, memory: BattleMemory, confidence: Confidence) {
        val existingIndex = memory.enemyTeam.indexOfFirst { it.species == species }
        if (existingIndex == -1) {
            memory.enemyTeam.add(
                EnemyPokemonMemory(
                    species = species,
                    speciesConfidence = confidence
                )
            )
        } else {
            val updated = memory.enemyTeam[existingIndex].copy(speciesConfidence = confidence)
            updated.timesSeen++
            memory.enemyTeam[existingIndex] = updated
        }
    }

    fun update(
        state: BattleState,
        memory: BattleMemory
    ) {
        state.enemyPokemon?.let { pokemon ->
            updateEnemyPokemon(pokemon, memory, Confidence(ConfidenceLevel.OBSERVED))

            memory.seenPokemon.add(pokemon)

            if (!memory.seenPokemon.contains(pokemon)) {
                memory.seenPokemon.add(pokemon)
            }

            state.enemyFastMove?.let { move ->
                memory.seenFastMoves
                    .getOrPut(pokemon) { mutableSetOf() }
                    .add(move)
            }

            state.enemyChargedMoves.forEach { move ->
                memory.seenChargedMoves
                    .getOrPut(pokemon) { mutableSetOf() }
                    .add(move)
            }
        }
    }
}
