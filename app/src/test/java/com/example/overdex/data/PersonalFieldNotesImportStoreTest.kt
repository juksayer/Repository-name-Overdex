package com.example.overdex.data

import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.PersonalFieldNotesState
import com.example.overdex.model.SyllableCorrection
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalFieldNotesImportStoreTest {
    private val localTrainer = UUID(0, 1).toString()
    private val otherTrainer = UUID(0, 2).toString()

    @Test fun `availability changes only the local flag and survives reopening for owned and received notes`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val owned = store.saveNote(note(origin = localTrainer))
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Keep this draft")
        store.saveDraft(draft)
        store.setShareable(owned.id, true)
        assertEquals(owned.copy(shareable = true), store.state.value.notes.single())
        assertEquals(draft, store.state.value.draft)
        assertEquals(canonicalNoteHash(owned), canonicalNoteHash(store.state.value.notes.single()))
        val incoming = note(revision = 4, origin = otherTrainer)
        store.importReviewed(packet(listOf(incoming)), setOf(incoming.id), false, localTrainer, emptySet())
        val received = store.state.value.notes.single { it.id == incoming.id }
        val beforeRejectedEdits = store.state.value
        val beforeRejectedBytes = disk.bytes!!.copyOf()
        listOf(received.copy(title = "An unauthorized edit"),
            received.copy(received = false, title = "An attempted ownership change"),
            note(origin = otherTrainer).copy(received = true)).forEach { attempted ->
            assertTrue(runCatching { store.saveNote(attempted) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals("Direct saves cannot create or revise received writing", beforeRejectedEdits, store.state.value)
        assertArrayEquals(beforeRejectedBytes, disk.bytes)
        store.setShareable(received.id, true)
        val reopened = PersonalFieldNotesStore(disk).load()
        assertEquals(received.copy(shareable = true), reopened.notes.single { it.id == received.id })
        assertEquals(draft, reopened.draft)
        val before = disk.bytes!!.copyOf()
        assertTrue(runCatching { store.setShareable("personal-${UUID.randomUUID()}", true) }.isFailure)
        assertArrayEquals(before, disk.bytes)
    }

    @Test fun `selected new notes retain all author metadata while imports never publish local sharing permission`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val owned = store.saveNote(note(origin = localTrainer))
        val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "Unrelated draft")
        store.saveDraft(draft)
        val incoming = note(revision = 6, origin = otherTrainer).copy(shareable = true)
        val unselected = note(origin = otherTrainer)
        store.importReviewed(packet(listOf(incoming, unselected)), setOf(incoming.id), false, localTrainer, emptySet())
        val state = PersonalFieldNotesStore(disk).load()
        assertEquals(listOf(owned, incoming.copy(received = true, shareable = false)), state.notes)
        assertEquals(draft, state.draft)
        assertTrue(state.corrections.isEmpty())
        assertFalse(state.notes.any { it.id == unselected.id })
        assertFalse(state.notes.last().toJournalEntry().note.verified)
    }

    @Test fun `explicit newer import preserves the saved literal ID and duplicate import preserves forwarding choice`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val original = note(revision = 2, origin = otherTrainer).copy(id = "personal-${UUID(0, 0xabcd)}")
        store.importReviewed(packet(listOf(original)), setOf(original.id), false, localTrainer, emptySet())
        val newer = original.copy(id = "personal-" + original.id.removePrefix("personal-").uppercase(Locale.ROOT),
            title = "The author's next revision", revision = 5, updatedAt = 50, shareable = true)
        store.importReviewed(packet(listOf(newer)), setOf(newer.id), false, localTrainer, emptySet())
        val saved = store.state.value.notes.single()
        assertEquals(newer.copy(id = original.id, received = true, shareable = false), saved)
        store.setShareable(saved.id, true)
        val writesBeforeDuplicate = disk.writes
        store.importReviewed(packet(listOf(saved)), setOf(saved.id), false, localTrainer, emptySet())
        assertEquals(writesBeforeDuplicate, disk.writes)
        assertTrue(store.state.value.notes.single().shareable)
        assertEquals(5, store.state.value.notes.single().revision)
    }

    @Test fun `rechecked older conflicting owned and unknown choices reject the entire transaction`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val original = note(revision = 2, origin = otherTrainer)
        store.importReviewed(packet(listOf(original)), setOf(original.id), false, localTrainer, emptySet())
        val staleReview = original.copy(revision = 3, title = "A reviewed revision", updatedAt = 30)
        assertEquals(FieldNoteImportDecision.NEWER,
            reviewFieldNoteImport(packet(listOf(staleReview)), store.state.value.notes, localTrainer).single().decision)
        val latest = original.copy(revision = 4, title = "Already saved in the meantime", updatedAt = 40)
        store.importReviewed(packet(listOf(latest)), setOf(latest.id), false, localTrainer, emptySet())
        val badChoices = listOf(staleReview, latest.copy(title = "Conflicting same revision"),
            latest.copy(originTrainerId = UUID(0, 3).toString()), note(origin = localTrainer))
        badChoices.forEach { bad ->
            val unrelatedNew = note(origin = otherTrainer)
            val before = store.state.value
            val bytes = disk.bytes!!.copyOf()
            assertTrue(runCatching {
                store.importReviewed(packet(listOf(unrelatedNew, bad)), setOf(unrelatedNew.id, bad.id),
                    false, localTrainer, emptySet())
            }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, store.state.value)
            assertArrayEquals(bytes, disk.bytes)
        }
        assertTrue(runCatching {
            store.importReviewed(packet(listOf(note(origin = otherTrainer))), setOf("personal-${UUID.randomUUID()}"),
                false, localTrainer, emptySet())
        }.isFailure)
    }

    @Test fun `newer import cannot replace a target with an open draft`() = runBlocking {
        val store = PersonalFieldNotesStore(MemoryPersistence())
        store.load()
        val incoming = note(revision = 2, origin = otherTrainer)
        store.importReviewed(packet(listOf(incoming)), setOf(incoming.id), false, localTrainer, emptySet())
        val draft = FieldNoteDraft(editingNoteId = incoming.id, speciesId = 1, speciesName = "Bulbasaur", title = "In progress")
        store.saveDraft(draft)
        val before = store.state.value
        val newer = incoming.copy(revision = 3, updatedAt = 30)
        assertTrue(runCatching {
            store.importReviewed(packet(listOf(newer)), setOf(newer.id), false, localTrainer, emptySet())
        }.isFailure)
        assertEquals(before, store.state.value)
    }

    @Test fun `optional corrections preserve provenance normalize known targets and protect collisions and local authorship`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val incoming = note(revision = 2, origin = otherTrainer).copy(id = "personal-${UUID(0, 0xbeef)}")
        val sourceCorrection = correction(incoming.id).copy(
            entryId = "personal-" + incoming.id.removePrefix("personal-").uppercase(Locale.ROOT))
        val bundledCorrection = correction("001-001")
        val packageWithCorrections = packet(listOf(incoming), listOf(sourceCorrection, bundledCorrection))
        store.importReviewed(packageWithCorrections, setOf(incoming.id), false, localTrainer, setOf("001-001"))
        assertTrue(store.state.value.corrections.isEmpty())
        store.importReviewed(packageWithCorrections, setOf(incoming.id), true, localTrainer, setOf("001-001"))
        assertEquals(listOf(sourceCorrection.copy(entryId = incoming.id), bundledCorrection),
            PersonalFieldNotesStore(disk).load().corrections)
        val unchanged = store.state.value
        val writes = disk.writes
        store.importReviewed(packageWithCorrections, setOf(incoming.id), true, localTrainer, setOf("001-001"))
        assertEquals(writes, disk.writes)

        val badCorrections = listOf(sourceCorrection.copy(replacementLine = "An attempted collision"),
            correction("002-001"), correction(incoming.id).copy(originTrainerId = localTrainer))
        badCorrections.forEach { bad ->
            assertTrue(runCatching {
                store.importReviewed(packet(emptyList(), listOf(bad)), emptySet(), true, localTrainer, setOf("001-001"))
            }.isFailure)
            assertEquals(unchanged, store.state.value)
        }
        val localCorrection = correction("001-001").copy(originTrainerId = localTrainer)
        store.saveCorrection(localCorrection)
        val writesWithLocal = disk.writes
        store.importReviewed(packet(emptyList(), listOf(localCorrection)), emptySet(), true, localTrainer, setOf("001-001"))
        assertEquals("An identical local annotation is a duplicate, never a newly forged record", writesWithLocal, disk.writes)
    }

    @Test fun `a correction for an unselected new note or failed commit cannot partially change saved writing`() = runBlocking {
        val disk = MemoryPersistence()
        val store = PersonalFieldNotesStore(disk)
        store.load()
        val original = store.saveNote(note(origin = localTrainer))
        val incoming = note(origin = otherTrainer)
        val packageWithCorrection = packet(listOf(incoming), listOf(correction(incoming.id)))
        val bytes = disk.bytes!!.copyOf()
        assertTrue(runCatching {
            store.importReviewed(packageWithCorrection, emptySet(), true, localTrainer, emptySet())
        }.isFailure)
        assertEquals(listOf(original), store.state.value.notes)
        assertArrayEquals(bytes, disk.bytes)
        disk.failWrites = true
        assertTrue(runCatching {
            store.importReviewed(packageWithCorrection, setOf(incoming.id), true, localTrainer, emptySet())
        }.exceptionOrNull() is IOException)
        assertEquals(PersonalFieldNotesState(notes = listOf(original)), store.state.value)
        assertArrayEquals(bytes, disk.bytes)
        disk.failWrites = false
        assertTrue(runCatching { store.setShareable(original.id, true) }.isFailure)
        store.load()
        store.importReviewed(packageWithCorrection, setOf(incoming.id), true, localTrainer, emptySet())
        assertEquals(2, store.state.value.notes.size)
        assertEquals(1, store.state.value.corrections.size)
    }

    private fun note(revision: Int = 1, origin: String) = PersonalFieldNote(
        id = "personal-${UUID.randomUUID()}", speciesId = 1, speciesName = "Bulbasaur", title = "An author's note",
        lines = listOf("One original line", "Two original lines"), lesson = "A retained lesson", author = "Unknown Author",
        revision = revision, createdAt = 10, updatedAt = 20, originTrainerId = origin,
    )

    private fun correction(entryId: String) = SyllableCorrection(
        id = UUID.randomUUID().toString(), entryId = entryId, entryRevision = 1, lineIndex = 0,
        proposedCount = 5, pronunciation = "Original pronunciation", replacementLine = "", createdAt = 30,
        originalLine = "The original source line", originTrainerId = otherTrainer,
    )

    private fun packet(notes: List<PersonalFieldNote>, corrections: List<SyllableCorrection> = emptyList()) =
        FieldNoteTransferPackage(packageId = UUID.randomUUID().toString(), senderTrainerId = otherTrainer,
            createdAt = 100, notes = notes, corrections = corrections)

    private class MemoryPersistence : PersonalFieldNotesPersistence {
        var bytes: ByteArray? = null
        var failWrites = false
        var writes = 0
        override fun read() = bytes?.copyOf()
        override fun write(bytes: ByteArray) {
            if (failWrites) throw IOException("Test commit failed")
            this.bytes = bytes.copyOf()
            writes++
        }
    }
}
