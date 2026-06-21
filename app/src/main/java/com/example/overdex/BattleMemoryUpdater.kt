package com.example.overdex

object BattleMemoryUpdater {

    fun update(
        state: BattleState,
        memory: BattleMemory
    ) {

        state.enemyPokemon?.let { pokemon ->

            if (!memory.enemyTeam.containsKey(pokemon)) {
                memory.enemyTeam[pokemon] =
                    EnemyPokemonMemory(species = pokemon)
            }
            memory.enemyTeam[pokemon]?.timesSeen++
            android.util.Log.d(
                "OVERMON_MEMORY",
                "Enemy team: ${memory.enemyTeam.keys}"
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