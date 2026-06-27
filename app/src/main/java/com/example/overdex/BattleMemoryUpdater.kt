package com.example.overdex

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel

object BattleMemoryUpdater {

    fun update(
        state: BattleState,
        memory: BattleMemory
    ) {

        state.enemyPokemon?.let { pokemon ->

            val existingIndex = memory.enemyTeam.indexOfFirst { it.species == pokemon }
            if (existingIndex == -1) {
                memory.enemyTeam.add(
                    EnemyPokemonMemory(
                        species = pokemon,
                        speciesConfidence = Confidence(ConfidenceLevel.OBSERVED)
                    )
                )
            }
            
            val indexToUpdate = memory.enemyTeam.indexOfFirst { it.species == pokemon }
            if (indexToUpdate != -1) {
                val updated = memory.enemyTeam[indexToUpdate].copy()
                updated.timesSeen++
                memory.enemyTeam[indexToUpdate] = updated
            }

            android.util.Log.d(
                "OVERMON_MEMORY",
                "Enemy team: ${memory.enemyTeam.map { it.species }}"
            )

            memory.seenPokemon.add(pokemon)

            if (!memory.observedEnemyPokemon.contains(pokemon)) {
                memory.observedEnemyPokemon.add(pokemon)
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
