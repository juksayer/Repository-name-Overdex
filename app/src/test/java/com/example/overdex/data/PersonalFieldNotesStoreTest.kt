package com.example.overdex.data

import com.example.overdex.model.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PersonalFieldNotesStoreTest {
    @Test fun `unfinished drafts reopen and saving their note atomically clears only that draft`() = runBlocking {
        val disk = MemoryPersistence()
        val first = PersonalFieldNotesStore(disk)
        first.load()
        val draft = FieldNoteDraft(speciesId = 10001, speciesName = "Droidball")
        first.saveDraft(draft)
        val reopened = PersonalFieldNotesStore(disk)
        assertEquals(draft, reopened.load().draft)
        val input = note(draft).copy(author = " ", lines = listOf("Two lines remain", "Exactly as written."))
        val saved = reopened.saveNote(input)
        assertEquals("personal-${draft.id}", saved.id)
        assertEquals("Unknown Author", saved.author)
        assertEquals(1, saved.revision)
        assertFalse(saved.shareable)
        assertNull(reopened.state.value.draft)
        assertEquals(saved, PersonalFieldNotesStore(disk).load().notes.single())
        val journal = saved.toJournalEntry()
        assertEquals(10001, journal.speciesId)
        assertEquals(1026, journal.sourceSpeciesId)
        assertEquals(saved.lines, journal.note.lines)
        assertEquals(saved.id, journal.note.id)
        assertFalse("Personal writing is never marked editorially verified", journal.note.verified)
    }

    @Test fun `edits retain identity creation time and origin while advancing revision once`() = runBlocking {
        val store = PersonalFieldNotesStore(MemoryPersistence())
        store.load()
        val original = store.saveNote(note())
        val editing = FieldNoteDraft(editingNoteId = original.id, speciesId = 1, speciesName = "Bulbasaur", title = "Edited")
        store.saveDraft(editing)
        val changedOrigin = original.copy(originTrainerId = "another-trainer")
        assertTrue(runCatching { store.saveNote(changedOrigin) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(editing, store.state.value.draft)
        assertEquals(original, store.state.value.notes.single())
        val saved = store.saveNote(original.copy(title = "Edited", revision = 99, createdAt = 999, updatedAt = 30, shareable = true))
        assertEquals(original.id, saved.id)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(original.originTrainerId, saved.originTrainerId)
        assertEquals(2, saved.revision)
        assertEquals(30L, saved.updatedAt)
        assertTrue(saved.shareable)
        assertNull(store.state.value.draft)
    }

    @Test fun `saving unrelated notes preserves work and deletion cannot remove another note or its draft`() = runBlocking {
        val store = PersonalFieldNotesStore(MemoryPersistence())
        store.load()
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Still writing")
        store.saveDraft(draft)
        val unrelated = store.saveNote(note())
        assertEquals(draft, store.state.value.draft)
        store.deleteNote(unrelated.id)
        assertEquals(draft, store.state.value.draft)
        assertTrue(store.state.value.notes.isEmpty())
        store.discardDraft()
        assertNull(store.state.value.draft)
    }

    @Test fun `corrupt reads preserve bytes and previous state then block changes until a successful retry`() = runBlocking {
        val disk = MemoryPersistence("not JSON".encodeToByteArray())
        val store = PersonalFieldNotesStore(disk)
        assertTrue(runCatching { store.load() }.isFailure)
        assertTrue(runCatching { store.saveDraft(FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur")) }.isFailure)
        assertEquals("not JSON", disk.bytes!!.decodeToString())
        assertEquals(0, disk.writes)
        disk.bytes = "{}".encodeToByteArray()
        store.load()
        store.saveDraft(FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Preserved work"))
        val goodBytes = disk.bytes!!.copyOf()
        val goodState = store.state.value
        disk.bytes = "broken later".encodeToByteArray()
        assertTrue(runCatching { store.load() }.isFailure)
        assertEquals(goodState, store.state.value)
        assertTrue(runCatching { store.discardDraft() }.isFailure)
        assertEquals("broken later", disk.bytes!!.decodeToString())
        disk.bytes = goodBytes
        assertEquals(goodState, store.load())
    }

    @Test fun `invalid notes and failed durable writes keep the saved draft available for recovery`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Ready to save")
        store.saveDraft(draft)
        val before = store.state.value
        val beforeBytes = disk.bytes!!.copyOf()
        val valid = note(draft)
        val invalid = listOf(valid.copy(title = " "), valid.copy(lines = listOf("", " ")),
            valid.copy(title = "x".repeat(201)), valid.copy(lines = listOf("x".repeat(2001))),
            valid.copy(lesson = "x".repeat(4001)), valid.copy(speciesId = 0))
        invalid.forEach { value -> assertTrue(runCatching { store.saveNote(value) }.exceptionOrNull() is IllegalArgumentException) }
        disk.failWrites = true
        assertTrue(runCatching { store.saveNote(valid) }.exceptionOrNull() is IOException)
        assertEquals(before, store.state.value)
        assertArrayEquals(beforeBytes, disk.bytes)
        assertEquals(before, PersonalFieldNotesStore(disk).load())
        disk.failWrites = false
        store.load()
        store.saveNote(valid)
        assertNull(store.state.value.draft)
    }

    @Test fun `a write that commits then reports failure blocks stale mutations until reload recovers committed data`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Save with verification uncertainty")
        store.saveDraft(draft)
        val before = store.state.value
        val saved = note(draft)
        disk.failAfterCommit = true
        assertTrue(runCatching { store.saveNote(saved) }.exceptionOrNull() is IOException)
        assertEquals("A failed acknowledgment cannot publish success", before, store.state.value)
        val committedBytes = disk.bytes!!.copyOf()
        disk.failAfterCommit = false
        assertTrue(runCatching { store.discardDraft() }.exceptionOrNull() is IllegalStateException)
        assertArrayEquals("Stale cached state must not overwrite the possibly committed note", committedBytes, disk.bytes)
        val recovered = store.load()
        assertEquals(saved, recovered.notes.single())
        assertNull(recovered.draft)
        assertEquals(recovered, store.state.value)
    }

    @Test fun `corrections preserve targeted revision original text and attribution without changing notes`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val saved = store.saveNote(note())
        val correction = SyllableCorrection(UUID.randomUUID().toString(), saved.id, saved.revision, 1, 7,
            "A pronunciation hint", "A proposed line", 40, originalLine = saved.lines[1],
            author = "Unknown Author", originTrainerId = "trainer-original")
        store.saveCorrection(correction)
        assertEquals(correction, PersonalFieldNotesStore(disk).load().corrections.single())
        assertEquals(listOf(saved), store.state.value.notes)
        val invalid = listOf(correction.copy(proposedCount = 100), correction.copy(lineIndex = -1),
            correction.copy(entryId = ""), correction.copy(proposedCount = null, pronunciation = "", replacementLine = ""))
        invalid.forEach { assertTrue(runCatching { store.saveCorrection(it) }.exceptionOrNull() is IllegalArgumentException) }
        assertEquals(listOf(correction), store.state.value.corrections)
        store.deleteCorrection(correction.id)
        assertTrue(store.state.value.corrections.isEmpty())
        assertEquals(listOf(saved), store.state.value.notes)
    }

    @Test fun `serialized concurrent saves and cancellation cannot diverge durable and published state`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val notes = (1..24).map { note().copy(title = "Note $it") }
        coroutineScope { notes.map { async(Dispatchers.Default) { store.saveNote(it) } }.awaitAll() }
        assertEquals(notes.map { it.id }.toSet(), store.state.value.notes.map { it.id }.toSet())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        disk.beforeWrite = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "The test did not release the commit" }
        }
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Persist despite cancellation")
        val writer = launch(Dispatchers.Default) { store.saveDraft(draft) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            writer.cancel()
        } finally { release.countDown() }
        writer.join()
        assertTrue(writer.isCancelled)
        assertEquals(draft, store.state.value.draft)
        assertEquals(store.state.value, PersonalFieldNotesStore(disk).load())
    }

    @Test fun `the journal capacity allows edits but rejects an additional note without losing data`() = runBlocking {
        val notes = (1..10_000).map { index -> note().copy(id = "personal-${UUID(0, index.toLong())}") }
        val disk = MemoryPersistence(Json.encodeToString(PersonalFieldNotesState(notes)).encodeToByteArray())
        val store = PersonalFieldNotesStore(disk)
        store.load()
        assertTrue(runCatching { store.saveNote(note()) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(10_000, store.state.value.notes.size)
        val changed = store.saveNote(notes.first().copy(title = "A revised note"))
        assertEquals(2, changed.revision)
        assertEquals(10_000, store.state.value.notes.size)
    }

    private fun note(draft: FieldNoteDraft? = null) = PersonalFieldNote(
        id = draft?.let { "personal-${it.id}" } ?: "personal-${UUID.randomUUID()}",
        speciesId = draft?.speciesId ?: 1, speciesName = draft?.speciesName ?: "Bulbasaur",
        title = draft?.title?.takeIf { it.isNotBlank() } ?: "A personal note",
        lines = listOf("A first poem line", "The second line remains", "A third poem line"),
        createdAt = 10, updatedAt = 20, originTrainerId = "trainer-original")

    private class MemoryPersistence(initial: ByteArray? = null) : PersonalFieldNotesPersistence {
        @Volatile var bytes: ByteArray? = initial
        @Volatile var failWrites = false
        @Volatile var failAfterCommit = false
        @Volatile var beforeWrite: (() -> Unit)? = null
        var writes = 0
            private set
        @Synchronized override fun read() = bytes?.copyOf()
        @Synchronized override fun write(bytes: ByteArray) {
            writes++
            beforeWrite?.invoke()
            if (failWrites) throw IOException("Test commit failed")
            this.bytes = bytes.copyOf()
            if (failAfterCommit) throw IOException("Test verification failed after committing")
        }
    }
}
