package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.PokemonImportList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loader responsible for reading Pokémon data from the bundled `pokemon_go_data.json` asset.
 */
class PokemonJsonLoader(
    private val context: Context
) {

    /**
     * Loads and parses the Pokémon list from assets.
     */
    suspend fun loadPokemon(): PokemonImportList =
        withContext(Dispatchers.IO) {
            val jsonText = context.assets
                .open("pokemon_go_data.json")
                .bufferedReader()
                .use { it.readText() }

            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<PokemonImportList>(jsonText)
        }
}