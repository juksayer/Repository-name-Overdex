package com.example.overdex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overdex.data.FieldNoteFavoritesStore
import com.example.overdex.data.FieldNoteRepository
import com.example.overdex.data.PersonalFieldNotesStore
import com.example.overdex.data.TrainerRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
    private val personalStore = PersonalFieldNotesStore(application)
    val localState = personalStore.state
    private val trainerId = TrainerRepository(application).getIdentity().trainerId.toString()
    private val _draft = MutableStateFlow<FieldNoteDraft?>(null)
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _editorMessage = MutableStateFlow<String?>(null)
    val editorMessage = _editorMessage.asStateFlow()
    private var personalReady = false
    private var bundledEntries = emptyList<FieldNoteJournalEntry>()
    private var draftWrite: Job? = null
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

    init {
        viewModelScope.launch { personalStore.state.collect { refreshEntries() } }
        retry()
    }

    private fun refreshEntries() {
        _entries.value = bundledEntries + personalStore.state.value.notes.map { it.toJournalEntry() }
    }

    fun retry() {
        if (_busy.value) return
        val generation = ++loadGeneration
        loadJob?.cancel()
        personalReady = false
        _loading.value = true
        _loadError.value = null
        loadJob = viewModelScope.launch {
            try {
                draftWrite?.cancelAndJoin()
                val loaded = repository.getJournalEntries()
                val local = personalStore.load()
                if (generation == loadGeneration) {
                    val recoveredDraft = _draft.value ?: local.draft
                    // Complete recovery before announcing that the journal is ready again.
                    if (recoveredDraft != null && recoveredDraft != local.draft) personalStore.saveDraft(recoveredDraft)
                    bundledEntries = loaded
                    personalReady = true
                    _draft.value = recoveredDraft
                    refreshEntries()
                }
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
    fun clearEditorStatus() { _editorMessage.value = null }

    fun beginDraft(entry: FieldNoteJournalEntry): Boolean {
        if (!personalReady || _busy.value) return false
        if (_draft.value == null) {
            updateDraft(FieldNoteDraft(speciesId = entry.speciesId, speciesName = entry.speciesName))
        }
        return true
    }

    fun editNote(id: String): Boolean {
        if (!personalReady || _busy.value) return false
        val note = personalStore.state.value.notes.firstOrNull { it.id == id && !it.received } ?: return false
        if (_draft.value?.editingNoteId == id) return true
        if (_draft.value != null) {
            _saveError.value = "Finish or discard your current draft before editing another entry."
            return false
        }
        updateDraft(FieldNoteDraft(editingNoteId = id, speciesId = note.speciesId, speciesName = note.speciesName,
            title = note.title, lines = note.lines, lesson = note.lesson, author = note.author))
        return true
    }

    fun updateDraft(value: FieldNoteDraft) {
        if (!personalReady || _busy.value) return
        _draft.value = value
        _editorMessage.value = "Saving draft…"
        draftWrite?.cancel()
        draftWrite = viewModelScope.launch {
            delay(250)
            try {
                personalStore.saveDraft(value)
                if (_draft.value == value) _editorMessage.value = "Draft saved on this phone."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _editorMessage.value = "Draft could not be saved. Return to the journal and retry loading."
                _saveError.value = _editorMessage.value
            }
        }
    }

    /** Complete the last keystroke's write before allowing the editor to leave. */
    fun keepDraft(onDone: () -> Unit) = mutate("Draft could not be saved. Please try again.") {
        draftWrite?.cancelAndJoin()
        _draft.value?.let { personalStore.saveDraft(it) }
        _editorMessage.value = "Draft saved on this phone."
        onDone()
    }

    fun discardDraft(onDone: () -> Unit) = mutate("Draft could not be discarded. Please try again.") {
        draftWrite?.cancelAndJoin()
        personalStore.discardDraft()
        _draft.value = null
        _editorMessage.value = null
        onDone()
    }

    fun saveDraftAsNote(onSaved: (String) -> Unit) = mutate("Your note could not be saved. Please try again.") {
        draftWrite?.cancelAndJoin()
        val current = requireNotNull(_draft.value) { "No draft is open." }
        val previous = personalStore.state.value.notes.firstOrNull { it.id == current.editingNoteId }
        check(current.editingNoteId == null || previous != null) { "The entry being edited no longer exists." }
        check(previous?.received != true) { "Received notes keep their original authorship." }
        val now = System.currentTimeMillis()
        val saved = personalStore.saveNote(PersonalFieldNote(
            id = previous?.id ?: "personal-${current.id}", speciesId = current.speciesId,
            speciesName = current.speciesName, title = current.title.trim(),
            lines = current.lines.map { it.trimEnd() }.dropLastWhile { it.isBlank() },
            lesson = current.lesson.trim(), author = current.author.trim().ifBlank { "Unknown Author" },
            revision = previous?.revision ?: 1, createdAt = previous?.createdAt ?: now, updatedAt = now,
            shareable = previous?.shareable ?: false, originTrainerId = previous?.originTrainerId ?: trainerId,
        ))
        _draft.value = null
        _editorMessage.value = null
        refreshEntries()
        onSaved(saved.id)
    }

    fun deleteNote(id: String, onDeleted: () -> Unit) = mutate("Your note could not be deleted. Please try again.") {
        draftWrite?.cancelAndJoin()
        personalStore.deleteNote(id)
        if (_draft.value?.let { it.editingNoteId == id || "personal-${it.id}" == id } == true) _draft.value = null
        else _draft.value?.let { personalStore.saveDraft(it) }
        refreshEntries()
        onDeleted()
    }

    fun saveCorrection(correction: SyllableCorrection, onSaved: () -> Unit) =
        mutate("Your syllable note could not be saved. Please try again.") {
            val entry = _entries.value.firstOrNull { it.note.id == correction.entryId }
                ?: error("The original entry is no longer available.")
            require(correction.entryRevision == entry.note.revision && correction.lineIndex in entry.note.lines.indices)
            personalStore.saveCorrection(correction.copy(originalLine = entry.note.lines[correction.lineIndex],
                originTrainerId = trainerId))
            onSaved()
        }

    fun deleteCorrection(id: String) = mutate("Your syllable note could not be deleted. Please try again.") {
        personalStore.deleteCorrection(id)
    }

    private fun mutate(failure: String, operation: suspend () -> Unit) {
        if (!personalReady || _busy.value) return
        _busy.value = true
        _editorMessage.value = null
        _saveError.value = null
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _editorMessage.value = failure; _saveError.value = failure }
            finally { _busy.value = false }
        }
    }
}
