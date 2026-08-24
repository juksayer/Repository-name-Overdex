package com.example.overdex.battle.observation

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon

open class FakePokemonKnowledge : PokemonKnowledge {
    override suspend fun getPokemonByName(name: String): Pokemon? =
        when (name) {
            "Bulbasaur" -> Pokemon(id = 1, name = "Bulbasaur", types = emptyList(), region = "Kanto", fastMoves = emptyList(), chargedMoves = emptyList())
            "Pikachu" -> Pokemon(id = 25, name = "Pikachu", types = emptyList(), region = "Kanto", fastMoves = emptyList(), chargedMoves = emptyList())
            "Charizard" -> Pokemon(id = 6, name = "Charizard", types = emptyList(), region = "Kanto", fastMoves = emptyList(), chargedMoves = emptyList())
            else -> null
        }

    override suspend fun getPokemonById(id: Int): Pokemon? =
        when (id) {
            1 -> getPokemonByName("Bulbasaur")
            25 -> getPokemonByName("Pikachu")
            6 -> getPokemonByName("Charizard")
            else -> null
        }

    override suspend fun getAllSpeciesNames(): Set<String> = setOf("BULBASAUR", "PIKACHU", "CHARIZARD")
}
