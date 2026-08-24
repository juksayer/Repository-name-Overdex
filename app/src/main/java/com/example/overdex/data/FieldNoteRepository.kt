package com.example.overdex.data

import android.content.Context
import android.util.Log
import com.example.overdex.model.BattleHaiku
import com.example.overdex.model.FieldNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Repository responsible for loading and providing Battle Haikus (Field Notes)
 * from the bundled `battle_haiku.json` asset.
 */
class FieldNoteRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedHaikus: Map<Int, BattleHaiku>? = null

    /**
     * Retrieves all field notes for a given Pokemon species ID.
     */
    suspend fun getFieldNotesForPokemon(pokemonId: Int): List<FieldNote> {
        val notes = loadHaikus()[pokemonId]?.fieldNote ?: emptyList()
        Log.d("FieldNoteRepo", "Lookup ID $pokemonId: found ${notes.size} notes")
        return notes
    }

    private suspend fun loadHaikus(): Map<Int, BattleHaiku> = withContext(Dispatchers.IO) {
        cachedHaikus ?: try {
            Log.d("FieldNoteRepo", "Loading haikus from assets...")
            val jsonText = context.assets.open("battle_haiku.json")
                .bufferedReader().use { it.readText() }
            
            val haikus = json.decodeFromString<List<BattleHaiku>>(jsonText)
            Log.d("FieldNoteRepo", "Successfully loaded ${haikus.size} species with haikus")
            
            haikus.associateBy { it.id }.also { cachedHaikus = it }
        } catch (e: Exception) {
            Log.e("FieldNoteRepo", "Failed to load haikus", e)
            emptyMap()
        }
    }
}
