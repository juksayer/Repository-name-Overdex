package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.SpeciesImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loader responsible for reading species metadata from the bundled `pokemon_species.json` asset.
 */
class SpeciesJsonLoader(
    private val context: Context
) {

    /**
     * Loads and parses species metadata from assets.
     */
    suspend fun loadSpecies(): List<SpeciesImport> =
        withContext(Dispatchers.IO) {
            val jsonText = context.assets
                .open("pokemon_species.json")
                .bufferedReader()
                .use { it.readText() }

            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<List<SpeciesImport>>(jsonText)
        }
}