package com.example.overdex.data

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Small persistence boundary so failed and concurrent writes can be exercised without Android. */
internal interface FieldNoteFavoritePersistence {
    fun read(): Set<String>
    fun write(noteIds: Set<String>): Boolean
}

/** Favorites refer to stable FieldNote.id values, independently of specimens and battle history. */
class FieldNoteFavoritesStore internal constructor(private val persistence: FieldNoteFavoritePersistence) {
    constructor(context: Context, preferencesName: String = "field_note_favorites") : this(
        PreferenceFavorites(context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)),
    )

    private val writes = Mutex()
    private val state = MutableStateFlow(persistence.read().toSet())
    val favorites = state.asStateFlow()

    suspend fun toggle(noteId: String) {
        require(noteId.isNotBlank()) { "A field note needs a stable ID." }
        writes.withLock {
            val next = if (noteId in state.value) state.value - noteId else state.value + noteId
            withContext(Dispatchers.IO + NonCancellable) {
                if (!persistence.write(next.toSet())) throw IOException("Your field-note favorite could not be saved. Please try again.")
                // Keep commit and publication together, even if the caller is cancelled
                // while the blocking commit finishes on the I/O dispatcher.
                state.value = next.toSet()
            }
        }
    }

    private class PreferenceFavorites(private val preferences: SharedPreferences) : FieldNoteFavoritePersistence {
        override fun read(): Set<String> = preferences.getStringSet("note_ids", emptySet()).orEmpty().toSet()

        override fun write(noteIds: Set<String>): Boolean {
            val before = read()
            if (preferences.edit().putStringSet("note_ids", noteIds.toSet()).commit()) return true
            // SharedPreferences can update its memory cache even when its disk commit fails.
            // Restore that cache as well; the caller still receives the original failure.
            preferences.edit().putStringSet("note_ids", before).commit()
            return false
        }
    }
}
