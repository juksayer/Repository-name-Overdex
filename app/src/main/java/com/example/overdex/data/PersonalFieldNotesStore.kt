package com.example.overdex.data

import android.content.Context
import android.util.AtomicFile
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.PersonalFieldNotesState
import com.example.overdex.model.SyllableCorrection
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface PersonalFieldNotesPersistence {
    fun read(): ByteArray?
    /** Return only after committing all bytes, or throw without claiming success. */
    fun write(bytes: ByteArray)
}

/** The user's drafts and notes stay outside Android cloud backup and the bundled catalogue. */
class PersonalFieldNotesStore internal constructor(private val persistence: PersonalFieldNotesPersistence) {
    constructor(context: Context) : this(File(context.applicationContext.noBackupFilesDir, "field_notes/journal.json"))
    internal constructor(file: File) : this(AtomicPersistence(file))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writes = Mutex()
    private val mutableState = MutableStateFlow(PersonalFieldNotesState())
    val state = mutableState.asStateFlow()
    private var loaded = false

    /** A failed read keeps the original file and previous state, and blocks writes until retry succeeds. */
    suspend fun load(): PersonalFieldNotesState = writes.withLock {
        loaded = false
        val restored = withContext(Dispatchers.IO) {
            persistence.read()?.let { json.decodeFromString<PersonalFieldNotesState>(it.decodeToString(throwOnInvalidSequence = true)) }
                ?: PersonalFieldNotesState()
        }
        validate(restored)
        mutableState.value = restored
        loaded = true
        restored
    }

    suspend fun saveDraft(draft: FieldNoteDraft) { update { it.copy(draft = draft.copy(lines = draft.lines.toList())) } }
    suspend fun discardDraft() { update { it.copy(draft = null) } }

    /** Stable note identity survives edits. The store, not the caller, advances the revision. */
    suspend fun saveNote(note: PersonalFieldNote): PersonalFieldNote {
        val saved = update { current ->
            val previous = current.notes.firstOrNull { it.id == note.id }
            require(!note.received && previous?.received != true) {
                "Received notes keep their original writing; use the reviewed import workflow."
            }
            if (previous != null) {
                require(previous.originTrainerId == note.originTrainerId) { "A note's original trainer cannot be changed." }
                require(previous.received == note.received) { "A received note cannot become a locally authored note." }
                require(previous.revision < Int.MAX_VALUE) { "This note cannot be revised further." }
            }
            val createdAt = previous?.createdAt ?: note.createdAt
            val normalized = note.copy(
                title = note.title.trim(), author = note.author.trim().ifBlank { "Unknown Author" },
                lines = note.lines.toList(),
                revision = previous?.revision?.plus(1) ?: 1,
                createdAt = createdAt, updatedAt = note.updatedAt.coerceAtLeast(createdAt),
            )
            val notes = if (previous == null) current.notes + normalized
                else current.notes.map { if (it.id == note.id) normalized else it }
            val matchingDraft = current.draft?.let { it.editingNoteId == note.id ||
                (it.editingNoteId == null && "personal-${it.id}" == note.id) } == true
            current.copy(notes = notes, draft = if (matchingDraft) null else current.draft)
        }
        return saved.notes.single { it.id == note.id }
    }

    suspend fun deleteNote(id: String) {
        update { current -> current.copy(notes = current.notes.filterNot { it.id == id },
            draft = current.draft?.takeUnless { it.editingNoteId == id || "personal-${it.id}" == id }) }
    }

    /** Availability is this device's choice, not a new revision of the author's writing. */
    suspend fun setShareable(id: String, shareable: Boolean) {
        update { current ->
            require(current.notes.any { it.id == id }) { "The saved note is no longer available." }
            current.copy(notes = current.notes.map { if (it.id == id) it.copy(shareable = shareable) else it })
        }
    }

    /** Apply explicit choices against current state, never against a stale review snapshot. */
    suspend fun importReviewed(
        value: FieldNoteTransferPackage,
        selectedNoteIds: Set<String>,
        includeCorrections: Boolean,
        localTrainerId: String,
        validBundledEntryIds: Set<String>,
    ) {
        update { current ->
            val candidates = reviewFieldNoteImport(value, current.notes, localTrainerId)
            fun key(id: String) = id.lowercase(java.util.Locale.ROOT)
            val selected = selectedNoteIds.map(::key).toSet()
            require(selected.all { selectedId -> candidates.any { key(it.note.id) == selectedId } }) {
                "A selected note is not present in this package. Review the package again."
            }
            val notes = current.notes.toMutableList()
            candidates.filter { key(it.note.id) in selected }.forEach { candidate ->
                when (candidate.decision) {
                    FieldNoteImportDecision.DUPLICATE -> Unit
                    FieldNoteImportDecision.NEW, FieldNoteImportDecision.NEWER -> {
                        val incoming = candidate.note
                        val draftTarget = current.draft?.let { it.editingNoteId ?: "personal-${it.id}" }
                        require(draftTarget == null || key(draftTarget) != key(incoming.id)) {
                            "Finish or discard the draft for this note before importing a revision."
                        }
                        val index = notes.indexOfFirst { key(it.id) == key(incoming.id) }
                        val received = incoming.copy(
                            id = notes.getOrNull(index)?.id ?: incoming.id,
                            received = true, shareable = false, lines = incoming.lines.toList(),
                        )
                        if (index < 0) notes += received else notes[index] = received
                    }
                    else -> throw IllegalArgumentException("A selected note is older or conflicts with saved writing. Review it again.")
                }
            }
            val corrections = current.corrections.toMutableList()
            if (includeCorrections) {
                val knownTargets = validBundledEntryIds.associateBy(::key) + notes.associate { key(it.id) to it.id }
                value.corrections.forEach { incoming ->
                    val knownTarget = knownTargets[key(incoming.entryId)]
                    val normalized = incoming.copy(entryId = knownTarget ?: incoming.entryId)
                    val previous = corrections.firstOrNull { key(it.id) == key(incoming.id) }
                    if (previous != null) {
                        require(previous == normalized.copy(id = previous.id)) {
                            "A syllable note with this ID already contains different writing."
                        }
                    } else {
                        require(!incoming.originTrainerId.equals(localTrainerId, ignoreCase = true)) {
                            "An import cannot create writing attributed to this device's trainer."
                        }
                        require(knownTarget != null) { "Save the original note before importing its syllable notes." }
                        corrections += normalized
                    }
                }
            }
            current.copy(notes = notes.toList(), corrections = corrections.toList())
        }
    }

    suspend fun saveCorrection(correction: SyllableCorrection) {
        update { current ->
            val normalized = correction.copy(author = correction.author.trim().ifBlank { "Unknown Author" })
            current.copy(corrections = current.corrections.filterNot { it.id == correction.id } + normalized)
        }
    }

    suspend fun deleteCorrection(id: String) {
        update { it.copy(corrections = it.corrections.filterNot { correction -> correction.id == id }) }
    }

    private suspend fun update(transform: (PersonalFieldNotesState) -> PersonalFieldNotesState): PersonalFieldNotesState = writes.withLock {
        check(loaded) { "Open the saved field notes successfully before making changes." }
        val next = transform(mutableState.value)
        validate(next)
        if (next == mutableState.value) return@withLock next
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                persistence.write(json.encodeToString(next).encodeToByteArray())
            } catch (error: Exception) {
                // A write can reach disk before a verification/read-back failure.
                // Reload before another edit can overwrite that possibly newer state.
                loaded = false
                throw error
            }
            mutableState.value = next
        }
        next
    }

    private fun validate(value: PersonalFieldNotesState) {
        require(value.notes.size <= 10_000) { "The personal journal can contain at most 10,000 notes." }
        require(value.notes.map { it.id }.distinct().size == value.notes.size) { "Saved note IDs must be unique." }
        value.notes.forEach { note ->
            require(note.id.startsWith("personal-") && note.id.length in 10..200) { "A personal note needs its original stable ID." }
            validateText(note.speciesId, note.speciesName, note.title, note.lines, note.lesson, note.author)
            require(note.title.isNotBlank() && note.lines.any { it.isNotBlank() }) { "Give the note a title and at least one poem line." }
            require(note.originTrainerId.isNotBlank() && note.originTrainerId.length <= 200) { "Keep the note's original trainer identity." }
            require(note.revision >= 1 && note.createdAt >= 0 && note.updatedAt >= note.createdAt) { "The saved note has invalid revision or timestamps." }
        }
        value.draft?.let { draft ->
            require(draft.id.isNotBlank() && draft.id.length <= 200) { "The draft needs a stable ID." }
            require(draft.editingNoteId == null || draft.editingNoteId.isNotBlank()) { "The edited note needs a stable ID." }
            validateText(draft.speciesId, draft.speciesName, draft.title, draft.lines, draft.lesson, draft.author)
        }
        require(value.corrections.size <= 10_000 && value.corrections.map { it.id }.distinct().size == value.corrections.size) {
            "Saved corrections must have unique IDs and number at most 10,000."
        }
        value.corrections.forEach { correction ->
            require(correction.id.isNotBlank() && correction.id.length <= 200 && correction.entryId.isNotBlank() && correction.entryId.length <= 200) {
                "A correction needs its own ID and the original note ID."
            }
            require(correction.entryRevision >= 1 && correction.lineIndex >= 0 && correction.createdAt >= 0) { "Choose the original note revision and line." }
            require(correction.proposedCount == null || correction.proposedCount in 0..99) { "Use a syllable count from 0 to 99." }
            require(correction.proposedCount != null || correction.pronunciation.isNotBlank() || correction.replacementLine.isNotBlank()) {
                "Add a syllable count, pronunciation, or suggested wording."
            }
            require(correction.pronunciation.length <= 2_000 && correction.replacementLine.length <= 2_000 && correction.originalLine.length <= 2_000 &&
                correction.author.length <= 200 && correction.originTrainerId.length <= 200) { "The correction text is too long." }
        }
    }

    private fun validateText(speciesId: Int, speciesName: String, title: String, lines: List<String>, lesson: String, author: String) {
        require(speciesId > 0 && speciesName.isNotBlank() && speciesName.length <= 200) { "Choose a Pokémon species." }
        require(title.length <= 200) { "Use a title of at most 200 characters." }
        require(lines.size <= 100 && lines.all { it.length <= 2_000 }) { "Use at most 100 poem lines of 2,000 characters each." }
        require(lesson.length <= 4_000) { "Use a lesson of at most 4,000 characters." }
        require(author.length <= 200) { "Use an author name of at most 200 characters." }
    }

    private class AtomicPersistence(private val file: File) : PersonalFieldNotesPersistence {
        private val atomic = AtomicFile(file)
        override fun read(): ByteArray? {
            if (!file.exists() && !File(file.path + ".bak").exists() && !File(file.path + ".new").exists()) return null
            return atomic.openRead().use { it.readBytes() }
        }
        override fun write(bytes: ByteArray) {
            check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "The journal folder could not be created." }
            var stream: FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(bytes)
                atomic.finishWrite(stream)
                stream = null
                if (!atomic.openRead().use { it.readBytes() }.contentEquals(bytes)) throw IOException("The journal write could not be verified.")
            } catch (error: Exception) {
                stream?.let { atomic.failWrite(it) }
                throw error
            }
        }
    }
}
