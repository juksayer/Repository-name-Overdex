package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.BattleHaiku
import com.example.overdex.model.FieldNote
import com.example.overdex.model.FieldNoteJournalEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Repository responsible for loading and providing Battle Haikus (Field Notes)
 * from the bundled `battle_haiku.json` asset.
 */
class FieldNoteRepository(private val readAsset: () -> String) {
    constructor(context: Context) : this({
        context.applicationContext.assets.open("battle_haiku.json").bufferedReader().use { it.readText() }
    })

    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var cachedHaikus: List<BattleHaiku>? = null

    /**
     * Retrieves all field notes for a given Pokemon species ID.
     */
    suspend fun getFieldNotesForPokemon(pokemonId: Int): List<FieldNote> {
        val sourceId = if (pokemonId == DroidballDexEntry.ID) DroidballDexEntry.FIELD_NOTE_SOURCE_ID else pokemonId
        return try {
            loadHaikus().firstOrNull { it.id == sourceId }?.fieldNote.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve the existing optional Pokédex section when the asset cannot be read.
            emptyList()
        }
    }

    /** The standalone journal exposes failures so an unreadable catalogue is not an empty book. */
    suspend fun getJournalEntries(): List<FieldNoteJournalEntry> = loadHaikus().flatMap { species ->
        species.fieldNote.map { note ->
            FieldNoteJournalEntry(
                speciesId = if (species.id == DroidballDexEntry.FIELD_NOTE_SOURCE_ID) DroidballDexEntry.ID else species.id,
                sourceSpeciesId = species.id,
                speciesName = species.species,
                note = note,
            )
        }
    }

    private suspend fun loadHaikus(): List<BattleHaiku> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            cachedHaikus ?: json.decodeFromString<List<BattleHaiku>>(readAsset()).also { haikus ->
                require(haikus.isNotEmpty()) { "The field-note catalogue is empty." }
                require(haikus.all { it.id > 0 } && haikus.map { it.id }.distinct().size == haikus.size) {
                    "The field-note catalogue contains duplicate or invalid species IDs."
                }
                val noteIds = haikus.flatMap { it.fieldNote }.map { it.id }
                require(noteIds.isNotEmpty() && noteIds.all { it.isNotBlank() } && noteIds.distinct().size == noteIds.size) {
                    "The field-note catalogue contains duplicate or empty note IDs."
                }
            }.sortedBy { it.id }.also { cachedHaikus = it }
        }
    }
}
