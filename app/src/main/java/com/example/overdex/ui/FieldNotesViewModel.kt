package com.example.overdex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overdex.data.FieldNoteFavoritesStore
import com.example.overdex.data.FieldNoteRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FieldNotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FieldNoteRepository(application)
    private val favoriteStore = FieldNoteFavoritesStore(application)
    private val _entries = MutableStateFlow<List<FieldNoteJournalEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()
    val favorites = favoriteStore.favorites
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    /** Deliberately bypasses the collection screen's search/filter state. Every card remains distinct. */
    val ownedPokemon: StateFlow<List<OwnedPokemon>> = PokedexDatabase.getDatabase(application)
        .ownedPokemonDao().getAllOwnedPokemonWithSpecies()
        .map { rows -> rows.map { it.owned.toDomain() } }
        .catch { error ->
            if (error is CancellationException) throw error
            _saveError.value = "Your owned Pokémon could not be loaded. Reopen field notes to try again."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init { retry() }

    fun retry() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _loading.value = true
        _loadError.value = null
        loadJob = viewModelScope.launch {
            try {
                val loaded = repository.getJournalEntries()
                if (generation == loadGeneration) _entries.value = loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration) _loadError.value = "Field notes could not be loaded. Please try again."
            } finally {
                if (generation == loadGeneration) _loading.value = false
            }
        }
    }

    fun toggleFavorite(noteId: String) {
        if (_entries.value.none { it.note.id == noteId }) {
            _saveError.value = "This field note is not available to favorite."
            return
        }
        viewModelScope.launch {
            try {
                favoriteStore.toggle(noteId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveError.value = "Your field-note favorite could not be saved. Please try again."
            }
        }
    }

    fun dismissSaveError() { _saveError.value = null }
}
