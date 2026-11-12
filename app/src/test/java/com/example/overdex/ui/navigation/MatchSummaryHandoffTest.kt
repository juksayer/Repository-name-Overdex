package com.example.overdex.ui.navigation

import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.MatchArchive
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MatchSummaryHandoffTest {
    @Test fun `consecutive matches accumulate once and starting the next match never discards them`() {
        val handoff = MatchSummaryHandoff()
        assertTrue(handoff.offer("first"))
        handoff.clearForNewMatch()
        assertTrue(handoff.offer("second"))
        handoff.clearForNewMatch()
        assertTrue(handoff.offer("third"))
        assertFalse(handoff.offer("first"))
        assertEquals(listOf("first", "second", "third"), handoff.pending.value)
    }

    @Test fun `acknowledging displayed matches preserves a completion that arrived during navigation`() {
        val handoff = MatchSummaryHandoff()
        handoff.offer("first")
        handoff.offer("second")
        val displayed = handoff.pending.value.toList()
        handoff.offer("new-arrival")
        assertTrue(handoff.acknowledge(displayed))
        assertEquals(listOf("new-arrival"), handoff.pending.value)
        assertEquals(displayed, handoff.delivered.value)
        assertFalse(handoff.acknowledge(displayed))
        assertFalse(handoff.offer("first"))
        assertEquals("Back/reopen keeps the presented comparison", displayed, handoff.delivered.value)
    }

    @Test fun `a delayed restore merges disk pending before matches already offered in this process`() {
        val handoff = MatchSummaryHandoff()
        handoff.offer("new-local")
        assertTrue(handoff.restore(MatchSummaryInboxState(
            pendingIds = listOf("old-first", "old-second", "old-first"),
            deliveredIds = listOf("previous-review"),
            seenIds = listOf("previous-review", "old-first", "old-second"), revision = 40,
        )))
        assertEquals(listOf("old-first", "old-second", "new-local"), handoff.pending.value)
        assertEquals(listOf("previous-review"), handoff.delivered.value)
        assertFalse(handoff.offer("previous-review"))
        assertTrue(handoff.snapshotState().revision > 40)
    }

    @Test fun `restore preserves local acknowledgments and does not reopen a refreshed old delivered match`() {
        val handoff = MatchSummaryHandoff()
        handoff.offer("first")
        handoff.acknowledge(listOf("first"))
        handoff.offer("new-local")
        handoff.offer("old-refresh") // Offered before the slower disk read revealed it was already seen.
        handoff.restore(MatchSummaryInboxState(
            pendingIds = listOf("first", "second"), deliveredIds = listOf("old-refresh"),
            seenIds = listOf("old-refresh", "first", "second"), revision = 20,
        ))
        assertEquals(listOf("second", "new-local"), handoff.pending.value)
        assertEquals(listOf("first"), handoff.delivered.value)
        assertFalse(handoff.offer("old-refresh"))
        assertFalse(handoff.offer("first"))
    }

    @Test fun `process recreation restores the complete pending batch and reference only snapshots without media files`() {
        val root = Files.createTempDirectory("match-summary-inbox-").toFile()
        try {
            val store = MatchSummaryInboxStore(root)
            val handoff = MatchSummaryHandoff()
            val archives = listOf(archive("first"), archive("second"))
            archives.forEach { archive ->
                handoff.offer(archive.matchId)
                assertTrue(store.upsert(archive))
            }
            assertTrue(store.saveState(handoff.snapshotState()))

            val recreatedStore = MatchSummaryInboxStore(root)
            val recreated = MatchSummaryHandoff(recreatedStore.readState())
            assertEquals(listOf("first", "second"), recreated.pending.value)
            archives.forEach { original ->
                val restored = recreatedStore.readArchive(original.matchId)!!
                assertEquals(original.copy(mediaIncluded = false), restored)
                val crop = restored.articles.single { it.payload is ArchivedCropCaptured }.payload as ArchivedCropCaptured
                assertEquals("artifacts/crops/not-present-on-disk.png", crop.artifactPath)
                assertFalse(File(root, crop.artifactPath).exists())
            }
            assertTrue(recreated.acknowledge(recreated.pending.value))
            assertTrue(recreatedStore.saveState(recreated.snapshotState()))
            val afterAnotherRestart = MatchSummaryHandoff(recreatedStore.readState())
            assertTrue(afterAnotherRestart.pending.value.isEmpty())
            assertEquals(listOf("first", "second"), afterAnotherRestart.delivered.value)
            assertFalse(afterAnotherRestart.offer("first"))
        } finally { root.deleteRecursively() }
    }

    @Test fun `late state writes and archive refreshes cannot erase an acknowledgment or a new arrival`() {
        val root = Files.createTempDirectory("match-summary-inbox-order-").toFile()
        try {
            val store = MatchSummaryInboxStore(root)
            val handoff = MatchSummaryHandoff()
            handoff.offer("first")
            val older = handoff.snapshotState()
            store.saveState(older)
            handoff.acknowledge(listOf("first"))
            handoff.offer("new-arrival")
            val current = handoff.snapshotState()
            assertTrue(store.saveState(current))
            assertFalse(store.saveState(older))
            store.upsert(archive("first"))
            assertEquals(current, store.readState())
            val restored = MatchSummaryHandoff(store.readState())
            assertEquals(listOf("new-arrival"), restored.pending.value)
            assertEquals(listOf("first"), restored.delivered.value)
            assertFalse(restored.offer("first"))
        } finally { root.deleteRecursively() }
    }

    @Test fun `a smaller late snapshot cannot replace a richer recorded comparison`() {
        val root = Files.createTempDirectory("match-summary-inbox-refresh-").toFile()
        try {
            val store = MatchSummaryInboxStore(root)
            val complete = archive("first")
            assertTrue(store.upsert(complete))
            assertFalse(store.upsert(complete.copy(articles = complete.articles.takeLast(1))))
            assertEquals(complete.copy(mediaIncluded = false), store.readArchive("first"))
            assertTrue(store.readState().pendingIds.isEmpty())
            assertNull(store.readArchive("never-recorded"))
        } finally { root.deleteRecursively() }
    }

    private fun archive(id: String): MatchArchive {
        fun article(suffix: String, payload: ArchivedTestimonyPayload, refs: List<String> = emptyList()) =
            ArchivedRealityArticle("$id-$suffix", id, 1000L, 2000L, "TEST", payload,
                emptyList(), null, null, refs, 1_000_000_000L)
        val crop = article("crop", ArchivedCropCaptured("artifacts/crops/not-present-on-disk.png", "a".repeat(64),
            1234L, "image/png", "MatchOutcomeCrop", 1080, 2400, 100, 900, 980, 1300))
        val raw = article("raw", ArchivedRawText("YOU WIN!"), listOf(crop.articleId))
        val result = article("end", ArchivedMatchEnded("WIN"), listOf(raw.articleId))
        return MatchArchive(matchId = id, articles = listOf(crop, raw, result))
    }
}
