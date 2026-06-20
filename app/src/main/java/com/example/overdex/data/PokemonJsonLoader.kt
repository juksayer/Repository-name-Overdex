package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.PokemonImportList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PokemonJsonLoader(
    private val context: Context
) {

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