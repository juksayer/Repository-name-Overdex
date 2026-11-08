package com.example.overdex.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overdex.data.FieldNoteFavoritesStore
import com.example.overdex.data.FieldNoteRepository
import com.example.overdex.data.PersonalFieldNotesStore
import com.example.overdex.data.TrainerRepository
import com.example.overdex.data.FieldNoteTransferCodec
import com.example.overdex.data.FieldNoteTransferPackage
import com.example.overdex.data.reviewFieldNoteImport
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.UUID
import java.io.ByteArrayOutputStream

class FieldNotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FieldNoteRepository(application)
    private val favoriteStore = FieldNoteFavoritesStore(application)
    private val personalStore = PersonalFieldNotesStore(application)
    val localState = personalStore.state
    private val trainerId = TrainerRepository(application).getIdentity().trainerId.toString()
    private val _incoming = MutableStateFlow<FieldNoteTransferPackage?>(null)
    val incoming = _incoming.asStateFlow()
    private val _exchangeMessage = MutableStateFlow<String?>(null)
    val exchangeMessage = _exchangeMessage.asStateFlow()
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

    fun setShareable(id: String, value: Boolean) = mutate("The sharing choice could not be saved. Please retry loading.") {
        personalStore.setShareable(id, value)
    }

    fun importCandidates() = _incoming.value?.let {
        reviewFieldNoteImport(it, personalStore.state.value.notes, trainerId)
    }.orEmpty()

    /** Only explicit sharing choices leave this phone; drafts and private-note annotations stay here. */
    fun preparePackage(includeCorrections: Boolean): ByteArray {
        check(personalReady) { "Wait for the saved journal to finish loading." }
        val shared = localState.value.notes.filter { it.shareable }
        val targets = bundledEntries.map { it.note.id }.toSet() + shared.map { it.id }
        val annotations = if (includeCorrections) localState.value.corrections.filter { it.entryId in targets } else emptyList()
        require(shared.isNotEmpty() || annotations.isNotEmpty()) { "Mark a saved note to share, or include syllable notes first." }
        return FieldNoteTransferCodec.encode(FieldNoteTransferPackage(
            packageId = UUID.randomUUID().toString(), senderTrainerId = trainerId,
            createdAt = System.currentTimeMillis(), notes = shared, corrections = annotations,
        ))
    }

    fun exportFile(uri: Uri, includeCorrections: Boolean) = mutate("The file could not be exported. Choose export again to retry.") {
        val bytes = preparePackage(includeCorrections)
        withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) {
                "The selected file could not be opened."
            }.use { it.write(bytes); it.flush() }
        }
        _exchangeMessage.value = "Exported selected notes. No trainer has received them yet."
    }

    fun importFile(uri: Uri) = mutate("This file could not be opened as an Overdex note package. The saved journal is unchanged.") {
        val bytes = withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (output.size() <= FieldNoteTransferCodec.MAX_PACKAGE_BYTES) {
                    val count = stream.read(buffer, 0, minOf(buffer.size,
                        FieldNoteTransferCodec.MAX_PACKAGE_BYTES + 1 - output.size()))
                    if (count < 0) break
                    check(count > 0) { "The selected file could not be read." }
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= FieldNoteTransferCodec.MAX_PACKAGE_BYTES) { "The package exceeds 256 KiB." }
                bytes
            }
        }
        previewPackage(bytes)
    }

    fun receivePackage(bytes: ByteArray) = mutate("The received note package is invalid. Nothing was saved.") {
        previewPackage(bytes)
    }

    private suspend fun previewPackage(bytes: ByteArray) {
        val decoded = withContext(Dispatchers.Default) { FieldNoteTransferCodec.decode(bytes) }
        _incoming.value = decoded
        _exchangeMessage.value = "Preview only. Choose which notes to save."
    }

    fun clearIncoming() { if (!_busy.value) { _incoming.value = null; _exchangeMessage.value = null } }
    fun exchangeStatus(message: String?) { _exchangeMessage.value = message }

    fun saveIncoming(ids: Set<String>, includeCorrections: Boolean) =
        mutate("The selected writing could not be imported. Review its originals and try again; saved entries were not partially changed.") {
            val packet = requireNotNull(_incoming.value)
            // Saving one entry includes its own annotations. A correction-only action reviews all targets.
            val selectedPacket = if (ids.isNotEmpty()) packet.copy(corrections = packet.corrections.filter { it.entryId in ids }) else packet
            personalStore.importReviewed(selectedPacket, ids, includeCorrections, trainerId,
                bundledEntries.map { it.note.id }.toSet())
            refreshEntries()
            _exchangeMessage.value = "Saved the selected received writing on this phone."
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
