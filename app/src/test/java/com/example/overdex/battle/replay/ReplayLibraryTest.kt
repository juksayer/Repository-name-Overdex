package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReplayLibraryTest {
    @Test fun `clips survive archive cleanup and retain complete state before their start`() = inLibrary { root, store ->
        val archive = recordedArchive()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val first = store.saveRange(archive, 3_000_000_000, 6_000_000_000, "First impact", "Check this hit")
        val second = store.saveRange(archive, 6_000_000_000, 8_000_000_000, "Next impact")
        assertEquals(first.archiveSha256, second.archiveSha256)
        assertEquals(1, File(root, "replay_sources").listFiles()!!.size)
        // Only source snapshots and clip metadata are needed: no archive/media directory.
        assertFalse(File(root, "match_archives").exists())
        val restored = store.loadSource(first)
        assertEquals(archive, restored)
        assertEquals(model.sceneAt(first.startMonotonicTimeNanos),
            MatchReplayModel(restored).sceneAt(first.startMonotonicTimeNanos))
        assertNotNull(MatchReplayModel(restored).sceneAt(first.startMonotonicTimeNanos).opponentHp)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        val reopened = ReplayExcerptStore(root).list()
        assertEquals(2, reopened.size)
        assertEquals("Check this hit", reopened.first { it.excerptId == first.excerptId }.notes)
        assertTrue(store.unprotectedSourceMatchIds().isEmpty())
    }

    @Test fun `compilations preserve ordering repeated clips and separate source clocks`() = inLibrary { root, store ->
        val a = simpleArchive("first", 0)
        val b = simpleArchive("second", 20_000_000_000)
        val one = store.saveRange(a, 1_000_000_000, 3_000_000_000, "One")
        val two = store.saveRange(b, 21_000_000_000, 24_000_000_000, "Two")
        val compilation = ReplayCompilation(title = "Alternating attacks", entries = listOf(
            ReplayCompilationEntry(excerptId = one.excerptId),
            ReplayCompilationEntry(excerptId = two.excerptId),
            ReplayCompilationEntry(excerptId = one.excerptId),
        ))
        val moved = compilation.moveEntry(1, 0)
        assertEquals(listOf(two.excerptId, one.excerptId, one.excerptId), moved.entries.map { it.excerptId })
        assertEquals(compilation, compilation.moveEntry(-1, 1))
        val saved = store.saveCompilation(moved)
        assertEquals(saved, ReplayExcerptStore(root).compilations().single())
        assertEquals("second", store.loadSource(two).matchId)
        assertEquals("first", store.loadSource(one).matchId)
        assertFalse(File(root, "match_archives").exists()) // A compilation cannot create battle records.
        assertEquals(2, File(root, "replay_sources").listFiles()!!.size)
    }

    @Test fun `invalid intervals and missing clips do not publish broken documents`() = inLibrary { _, store ->
        val archive = simpleArchive("match", 0)
        assertThrows(IllegalArgumentException::class.java) { store.saveRange(archive, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { store.saveRange(archive, 4, 3) }
        assertThrows(IllegalArgumentException::class.java) { store.saveRange(archive, -1, 3) }
        assertThrows(IllegalArgumentException::class.java) { store.saveRange(archive, 0, 11_000_000_000) }
        assertTrue(store.list().isEmpty())
        val clip = store.saveRange(archive, 0, 1_000_000_000, "Valid")
        val saved = store.saveCompilation(ReplayCompilation(title = "Keep me",
            entries = listOf(ReplayCompilationEntry(excerptId = clip.excerptId))))
        assertThrows(IllegalArgumentException::class.java) {
            store.saveCompilation(saved.copy(entries = listOf(ReplayCompilationEntry(excerptId = "missing"))))
        }
        assertEquals(saved, store.compilations().single())
    }

    @Test fun `legacy excerpt is protected and recovers only its exact original source`() = inLibrary { root, store ->
        val archive = simpleArchive("legacy", 0)
        val hash = sha(MatchArchiveSerializer.serialize(archive).toByteArray())
        val clip = ReplayExcerpt(excerptId = "legacy-clip", archiveMatchId = archive.matchId, archiveSha256 = hash,
            startMonotonicTimeNanos = 0, endMonotonicTimeNanos = 1_000_000_000)
        File(root, "replay_excerpts").mkdirs()
        File(root, "replay_excerpts/legacy-clip.odxexcerpt.json").writeText(Json.encodeToString(clip))
        assertEquals(setOf("legacy"), store.unprotectedSourceMatchIds())
        val directory = File(root, "match_archives").apply { mkdirs() }
        val file = File(directory, "legacy.odxmatch.zip")
        file.outputStream().use { MatchArchivePackageWriter.write(archive.copy(articles = archive.articles.take(1)), it) }
        assertThrows(IllegalStateException::class.java) { store.loadSource(clip) }
        file.outputStream().use { MatchArchivePackageWriter.write(archive, it) }
        assertEquals(archive, store.loadSource(clip))
        assertTrue(store.unprotectedSourceMatchIds().isEmpty())
        file.delete()
        assertEquals(archive, store.loadSource(clip))
    }

    @Test fun `tampered snapshots fail without substituting another match`() = inLibrary { root, store ->
        val clip = store.saveRange(simpleArchive("original", 0), 0, 1_000_000_000)
        val file = File(root, "replay_sources/${clip.archiveSha256}.json.gz")
        java.util.zip.GZIPOutputStream(file.outputStream()).use {
            it.write(MatchArchiveSerializer.serialize(simpleArchive("different", 0)).toByteArray())
        }
        assertThrows(IllegalStateException::class.java) { store.loadSource(clip) }
        assertThrows(IllegalArgumentException::class.java) { store.loadSource(clip.copy(archiveSha256 = "../outside")) }
    }

    @Test fun `renaming a clip retains identity provenance and recovered presentation`() = inLibrary { _, store ->
        val recovery = ReplayIdentityObservation("OPPONENT", "Sneasel", 215, 0, "ARCHIVED_CROP", articleId = "evidence")
        val clip = store.saveRange(simpleArchive("match", 0), 0, 1_000_000_000, "Before",
            recoveredIdentities = listOf(recovery))
        val changed = store.updateDetails(clip, "After", "A coaching note")
        assertEquals(clip.excerptId, changed.excerptId)
        assertEquals(clip.archiveSha256, changed.archiveSha256)
        assertEquals(listOf(recovery), store.list().single().recoveredIdentities)
        assertEquals("A coaching note", store.list().single().notes)
    }

    @Test fun `draft survives opening another match and remains distinct from a saved compilation`() = inLibrary { root, store ->
        val draft = ReplayCompilation(title = "In progress")
        store.saveDraft(draft)
        assertEquals(draft, ReplayExcerptStore(root).loadDraft())
        assertTrue(store.compilations().isEmpty())
        store.clearDraft()
        assertNull(store.loadDraft())
    }

    @Test fun `clip clock clamps both ends without retiming original source events`() {
        val range = ReplayWindow(20_000_000_000, 23_000_000_000)
        assertEquals(3_000_000_000, range.durationNanos)
        assertEquals(range.startNanos, range.clamp(0))
        assertEquals(range.endNanos, range.clamp(Long.MAX_VALUE))
        val clock = ReplayPlaybackClock(range.startNanos, range.endNanos, 5f)
        clock.positionAt(100)
        assertEquals(range.endNanos, clock.positionAt(1_000_000_100))
    }

    private fun simpleArchive(id: String, base: Long) = MatchArchive(matchId = id, articles = listOf(0L, 10_000_000_000L).mapIndexed { index, time ->
        ArchivedRealityArticle("$id-$index", id, time / 1_000_000, time / 1_000_000, "test",
            ArchivedRawText("source $index"), emptyList(), null, index.toLong(), null, base + time)
    })
    private fun recordedArchive() = MatchArchiveSerializer.deserialize(checkNotNull(
        javaClass.getResource("/replay/incinerate-opening-hp-20261013.json")).readText())
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun inLibrary(block: (File, ReplayExcerptStore) -> Unit) {
        val root = Files.createTempDirectory("replay-library-test").toFile()
        try { block(root, ReplayExcerptStore(root)) } finally { root.deleteRecursively() }
    }
}
