package com.example.overdex.data

import com.example.overdex.model.Pokemon

interface PokemonKnowledge {
    suspend fun getPokemonByName(name: String): Pokemon?
    suspend fun getPokemonById(id: Int): Pokemon?
    suspend fun getAllSpeciesNames(): Set<String>
}