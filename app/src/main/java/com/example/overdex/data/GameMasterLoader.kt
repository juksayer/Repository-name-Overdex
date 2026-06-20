package com.example.overdex.data

import android.content.Context
import android.util.Log
import com.example.overdex.model.GameMaster
import com.example.overdex.model.GameMasterPokemon
import kotlinx.serialization.json.Json
import com.example.overdex.model.GameMasterMove

class GameMasterLoader(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val gameMaster by lazy {
        val rawJson = loadRawJson()
        json.decodeFromString<GameMaster>(rawJson)
    }

    fun loadRawJson(): String {
        return context.assets
            .open("gamemaster.json")
            .bufferedReader()
            .use { it.readText() }
    }

    fun getPokemon(speciesId: String): GameMasterPokemon? {
        return gameMaster.pokemon.find {
            it.speciesId == speciesId
        }
    }
    fun getPokemonByDex(dex: Int): GameMasterPokemon? {
        return gameMaster.pokemon.find {
            it.dex == dex
        }
    }
    fun getMove(moveId: String): GameMasterMove? {
        return gameMaster.moves.find {
            it.moveId == moveId
        }
    }
    fun testParse() {
        try {
            val charizard = getPokemon("charizard")

            if (charizard != null) {
                Log.d("BULBASAUR", "Name: ${charizard.speciesName}")
                Log.d("BULBASAUR", "Fast: ${charizard.fastMoves}")
                Log.d("BULBASAUR", "Charged: ${charizard.chargedMoves}")
            } else {
                Log.d("BULBASAUR", "Charizard not found")
            }
        } catch (e: Exception) {
            Log.e("BULBASAUR", "Parse failed", e)
        }
    }
}