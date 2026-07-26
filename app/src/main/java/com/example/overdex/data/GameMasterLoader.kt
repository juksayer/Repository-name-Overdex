package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.GameMaster
import com.example.overdex.model.GameMasterPokemon
import kotlinx.serialization.json.Json
import com.example.overdex.model.GameMasterMove

/**
 * Responsible for loading and parsing the static GameMaster data from assets.
 * 
 * The GameMaster is the primary source of truth for base stats, move pools,
 * and elemental types for all Pokémon species.
 */
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

}