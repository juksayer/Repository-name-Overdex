package com.example.overdex.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FieldNoteFavoritesStoreTest {
    @Test fun `individual note IDs persist independently across reopening and editorial reordering`() = runBlocking {
        val persistence = MemoryFavorites()
        val store = FieldNoteFavoritesStore(persistence)
        // Sibling notes can share a title and author; those are never persistence keys.
        store.toggle("181-001")
        store.toggle("181-002")
        store.toggle("1026-004")
        store.toggle("181-001")
        assertEquals(setOf("181-002", "1026-004"), store.favorites.value)
        assertEquals(store.favorites.value, FieldNoteFavoritesStore(persistence).favorites.value)
        // A catalogue refresh must not clear older stable IDs or change another note's favorite.
        val reversedCatalogue = listOf("1026-004", "181-002", "181-001")
        assertEquals(listOf("1026-004", "181-002"), reversedCatalogue.filter { it in store.favorites.value })
    }

    @Test fun `rapid concurrent toggles are serialized without losing other notes or repeated toggles`() = runBlocking {
        val persistence = MemoryFavorites()
        val store = FieldNoteFavoritesStore(persistence)
        coroutineScope {
            (1..80).map { index -> async(Dispatchers.Default) {
                store.toggle("shared")
                store.toggle("note-$index")
            } }.awaitAll()
        }
        val expected = (1..80).map { "note-$it" }.toSet()
        assertEquals(expected, store.favorites.value)
        assertEquals(expected, FieldNoteFavoritesStore(persistence).favorites.value)
        assertEquals(160, persistence.writes)
    }

    @Test fun `failed commit stays unsaved and visible while another queued note can still be saved`() = runBlocking {
        val persistence = MemoryFavorites(rejectId = "failed-note")
        val store = FieldNoteFavoritesStore(persistence)
        store.toggle("181-002")
        val outcomes = coroutineScope {
            listOf("failed-note", "1026-004").map { id -> async(Dispatchers.Default) {
                id to runCatching { store.toggle(id) }
            } }.awaitAll().toMap()
        }
        assertTrue(outcomes.getValue("failed-note").exceptionOrNull() is IOException)
        assertTrue(outcomes.getValue("1026-004").isSuccess)
        assertEquals(setOf("181-002", "1026-004"), store.favorites.value)
        assertEquals(store.favorites.value, FieldNoteFavoritesStore(persistence).favorites.value)
        persistence.rejectId = null
        store.toggle("failed-note")
        assertEquals(setOf("181-002", "1026-004", "failed-note"), FieldNoteFavoritesStore(persistence).favorites.value)
    }

    @Test fun `cancelling a caller during commit still publishes persisted state before the next toggle`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var saved = emptySet<String>()
        val persistence = object : FieldNoteFavoritePersistence {
            @Synchronized override fun read() = saved.toSet()
            @Synchronized override fun write(noteIds: Set<String>): Boolean {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release the blocked commit" }
                saved = noteIds.toSet()
                return true
            }
        }
        val store = FieldNoteFavoritesStore(persistence)
        val writer = launch(Dispatchers.Default) { store.toggle("181-001") }
        try {
            assertTrue("The test must cancel during an actual in-flight write", started.await(5, TimeUnit.SECONDS))
            writer.cancel()
        } finally {
            release.countDown()
        }
        writer.join()
        assertTrue(writer.isCancelled)
        assertEquals(setOf("181-001"), FieldNoteFavoritesStore(persistence).favorites.value)
        assertEquals("Disk and published state must agree after a cancelled caller", setOf("181-001"), store.favorites.value)
        store.toggle("181-001")
        assertTrue("The next toggle must remove the committed favorite", store.favorites.value.isEmpty())
        assertTrue(FieldNoteFavoritesStore(persistence).favorites.value.isEmpty())
    }

    private class MemoryFavorites(var rejectId: String? = null) : FieldNoteFavoritePersistence {
        private var saved = emptySet<String>()
        var writes = 0
            private set
        @Synchronized override fun read(): Set<String> = saved.toSet()
        @Synchronized override fun write(noteIds: Set<String>): Boolean {
            writes++
            if (rejectId?.let { it in noteIds } == true) return false
            saved = noteIds.toSet()
            return true
        }
    }
}
