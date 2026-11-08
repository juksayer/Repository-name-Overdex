package com.example.overdex.data

import com.example.overdex.model.BattleHaiku
import com.example.overdex.model.FieldNote
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FieldNoteRepositoryTest {
    @Test fun `journal exposes all canonical notes and preserves source order text attribution and mascot mapping`() = runBlocking {
        val text = File("src/main/assets/battle_haiku.json").readText()
        val canonical = Json.decodeFromString<List<BattleHaiku>>(text)
        val repository = FieldNoteRepository { text }
        val entries = repository.getJournalEntries()
        assertEquals(1_026, canonical.size)
        assertEquals((1..1_026).toSet(), canonical.map { it.id }.toSet())
        assertEquals(1_030, entries.size)
        assertEquals(1_030, entries.map { it.note.id }.distinct().size)
        assertEquals(canonical.sortedBy { it.id }.flatMap { it.fieldNote }, entries.map { it.note })
        assertEquals(setOf("Unknown Author"), entries.map { it.note.author }.toSet())
        val mascot = entries.filter { it.sourceSpeciesId == 1_026 }
        assertEquals(4, mascot.size)
        assertTrue(mascot.all { it.speciesId == DroidballDexEntry.ID && it.speciesName == "Droidball" })
        assertEquals(mascot.map { it.note }, repository.getFieldNotesForPokemon(DroidballDexEntry.ID))
        assertEquals(listOf("181-001", "181-002"), entries.filter { it.sourceSpeciesId == 181 }.map { it.note.id })
        assertEquals(emptyList<FieldNote>(), repository.getFieldNotesForPokemon(-1))

        // Source records may move, but sibling notes retain their intentional editorial order.
        val reordered = FieldNoteRepository { Json.encodeToString(listOf(
            BattleHaiku(2, "Ivysaur", listOf(note("002-002"), note("002-001"))),
            BattleHaiku(1, "Bulbasaur", listOf(note("001-001"))),
        )) }.getJournalEntries()
        assertEquals(listOf("001-001", "002-002", "002-001"), reordered.map { it.note.id })
    }

    @Test fun `duplicate species or note IDs fail instead of dropping notes or merging favorites`() = runBlocking {
        val cases = listOf(
            listOf(BattleHaiku(1, "First", listOf(note("001-001"))), BattleHaiku(1, "Second", listOf(note("001-002")))),
            listOf(BattleHaiku(1, "First", listOf(note("001-001"))), BattleHaiku(2, "Second", listOf(note("001-001")))),
        )
        cases.forEach { source ->
            val repository = FieldNoteRepository { Json.encodeToString(source) }
            assertTrue(runCatching { repository.getJournalEntries() }.exceptionOrNull() is IllegalArgumentException)
            assertTrue("The optional Pokédex section must remain graceful", repository.getFieldNotesForPokemon(1).isEmpty())
        }
    }

    @Test fun `journal read errors stay visible and retryable while optional Pokedex lookup remains graceful`() = runBlocking {
        var available = false
        var reads = 0
        val expected = note("001-001")
        val repository = FieldNoteRepository {
            reads++
            if (!available) throw IOException("Unreadable source")
            Json.encodeToString(listOf(BattleHaiku(1, "Bulbasaur", listOf(expected))))
        }
        assertTrue(repository.getFieldNotesForPokemon(1).isEmpty())
        assertTrue(runCatching { repository.getJournalEntries() }.exceptionOrNull() is IOException)
        available = true
        assertEquals(expected, repository.getJournalEntries().single().note)
        assertEquals(listOf(expected), repository.getFieldNotesForPokemon(1))
        assertEquals("Only successful loads may populate the shared cache", 3, reads)
    }

    @Test fun `cancellation is never converted to an empty catalogue or optional note list`() = runBlocking {
        val repository = FieldNoteRepository { throw CancellationException("Reader cancelled") }
        assertTrue(runCatching { repository.getJournalEntries() }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { repository.getFieldNotesForPokemon(1) }.exceptionOrNull() is CancellationException)
    }

    private fun note(id: String) = FieldNote(id, "Field Note", listOf("First", "Second", "Third"),
        author = "Unknown Author")
}
