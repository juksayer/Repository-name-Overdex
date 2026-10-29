package com.example.overdex.battle.preview

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectoryCropPreviewsTest {
    @Test fun `newest captured crop wins even when older evidence arrives later`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(crop("fresh", "a", "PlayerActiveSpeciesTextCrop", 200, 2_000).copy(recordedAt = 201))
        selected.accept(crop("late-old", "a", "PlayerActiveSpeciesTextCrop", 300, 1_000).copy(recordedAt = 900))
        assertEquals("fresh", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        // Legacy evidence without monotonic time uses capture wall time, not receipt time.
        selected.accept(crop("legacy", "a", "AnnouncementCrop", 500, null))
        selected.accept(crop("old-legacy", "a", "AnnouncementCrop", 400, null).copy(recordedAt = 999))
        assertEquals("legacy", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
    }

    @Test fun `seven slots remain independent and foreign match or registration crops are ignored`() {
        val selected = DirectoryCropSelection("a")
        val names = listOf("PlayerActiveSpeciesTextCrop", "OpponentActiveSpeciesTextCrop", "PlayerHpEvidenceCrop",
            "OpponentHpEvidenceCrop", "PlayerPokeBallsCrop", "OpponentPokeBallsCrop", "AnnouncementCrop")
        names.forEachIndexed { i, name -> selected.accept(crop("$i", "a", name, i.toLong(), i.toLong())) }
        selected.accept(crop("foreign", "b", "AnnouncementCrop", 100, 100))
        selected.accept(crop("registration", "a", "RegistrationSpeciesNameCrop", 100, 100))
        assertEquals(DirectoryCropSlot.entries.toSet(), selected.crops.keys)
        assertEquals("6", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
        assertEquals(BattleCropBounds(10, 20, 110, 60), selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).provenance.bounds)
    }

    @Test fun `archive bootstrap excludes menu scans but accepts late captured battles without GO`() {
        val image = archived(crop("crop", "a", "AnnouncementCrop", 1, 1))
        assertNull(DirectoryCropSelection.fromBattleArchive(MatchArchive(matchId = "a", articles = listOf(image))))
        val ended = image.copy(articleId = "end", payload = ArchivedMatchEnded("WIN"))
        assertEquals("a", DirectoryCropSelection.fromBattleArchive(
            MatchArchive(matchId = "a", articles = listOf(image, ended)))?.matchId)
    }

    @Test fun `ZIP and local fallback verify only requested image bytes and never import files`() {
        withRoot { root ->
            val wanted = byteArrayOf(1, 2, 3)
            val reference = reference(wanted)
            val archive = File(root, "test.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(reference.relativePath)); zip.write(wanted); zip.closeEntry()
                zip.putNextEntry(ZipEntry("unrequested.wav")); zip.write(ByteArray(100_000)); zip.closeEntry()
            }
            val decoded = DirectoryCropArchiveBytes.read(archive, listOf(reference))
            assertEquals(setOf(reference.relativePath), decoded.keys)
            assertArrayEquals(wanted, decoded.getValue(reference.relativePath))
            assertFalse(File(root, "artifacts").exists())
            assertTrue(DirectoryCropArchiveBytes.read(archive, listOf(reference.copy(byteCount = 4))).isEmpty())
            assertTrue(DirectoryCropArchiveBytes.read(archive, listOf(reference.copy(relativePath = "../escape.png"))).isEmpty())
            val local = File(root, reference.relativePath).also { requireNotNull(it.parentFile).mkdirs() }
            local.writeBytes(wanted)
            assertArrayEquals(wanted, DirectoryCropArchiveBytes.readLocal(root, reference))
            local.writeBytes(byteArrayOf(3, 2, 1))
            assertNull(DirectoryCropArchiveBytes.readLocal(root, reference))
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(reference.relativePath)); zip.write(byteArrayOf(3, 2, 1)); zip.closeEntry()
            }
            assertTrue(DirectoryCropArchiveBytes.read(archive, listOf(reference)).isEmpty())
        }
    }

    @Test fun `feed is lazy reuses an unavailable identical artifact and clears slots at new match`() = runBlocking {
        withRoot { root ->
            val a = InMemoryRealityTimeline().also { it.append(crop("a1", "a", "PlayerHpEvidenceCrop", 1, 1)) }
            val b = InMemoryRealityTimeline().also { it.append(crop("b1", "b", "OpponentHpEvidenceCrop", 3, 3)) }
            var source = MatchArchiveSource(MatchId("a"), a)
            var polls = 0
            var decodes = 0
            val feed = DirectoryCropPreviewFeed(root, sources = { polls++; listOf(source) }, decodeLocal = { decodes++; null })
            assertEquals(0, polls)
            val emitted = mutableListOf<DirectoryCropPreviewState>()
            feed.states().drop(1).take(3).onEach { state ->
                emitted += state
                if (emitted.size == 1) a.append(crop("a2", "a", "PlayerHpEvidenceCrop", 2, 2))
                if (emitted.size == 2) source = MatchArchiveSource(MatchId("b"), b)
            }.toList()
            assertEquals(2, decodes)
            assertEquals(listOf(1L, 2L, 3L), emitted.map { it.latestCaptureMillis })
            assertEquals("a2", emitted[1].crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
            assertEquals("b", emitted.last().matchId)
            assertEquals(setOf(DirectoryCropSlot.OPPONENT_HP), emitted.last().crops.keys)
            assertTrue(emitted.flatMap { it.crops.values }.all { it.bitmap == null })
        }
    }

    @Test fun `cancelled initial collection leaves no ongoing archive or timeline work`() = runBlocking {
        withRoot { root ->
            var polls = 0
            val feed = DirectoryCropPreviewFeed(root, sources = { polls++; emptyList() })
            feed.states().take(1).toList()
            // flowOn may begin the next iteration before downstream cancellation,
            // so verify the unsubscribed feed itself does not keep polling.
            val stoppedAt = polls
            kotlinx.coroutines.delay(1_050)
            assertEquals(stoppedAt, polls)
        }
    }

    @Test fun `newly recovered battle is discovered after an initially empty archive directory`() = runBlocking {
        withRoot { root ->
            val feed = DirectoryCropPreviewFeed(root, sources = { emptyList() }, decodeLocal = { null })
            var count = 0
            val results = feed.states().drop(1).take(2).onEach {
                if (++count == 1) {
                    saveArchive(root, "2026-10-29_01_battle.odxmatch.zip", "battle", true)
                    saveArchive(root, "2026-10-29_02_menu.odxmatch.zip", "menu", false)
                }
            }.toList()
            assertNull(results.first().matchId)
            assertEquals("battle", results.last().matchId)
            assertNull(results.last().crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).bitmap)
        }
    }

    @Test fun `a cold archive cannot overwrite a warm match arriving during image loading`() = runBlocking {
        withRoot { root ->
            saveArchive(root, "2026-10-29_cold.odxmatch.zip", "cold", true)
            val warm = InMemoryRealityTimeline().also { it.append(crop("warm", "warm", "PlayerHpEvidenceCrop", 5, 5)) }
            var source: MatchArchiveSource? = null
            val feed = DirectoryCropPreviewFeed(root, sources = { listOfNotNull(source) }, decodeLocal = {
                source = MatchArchiveSource(MatchId("warm"), warm)
                null
            })
            val results = feed.states().drop(1).take(2).toList()
            assertTrue(results.none { it.matchId == "cold" })
            assertEquals("warm", results.last().matchId)
            assertEquals(setOf(DirectoryCropSlot.PLAYER_HP), results.last().crops.keys)
        }
    }

    private fun crop(id: String, match: String, name: String, wall: Long, mono: Long?) = RealityArticle(
        ArticleId(id), wall, wall, SourceId("TEST"),
        CropCaptured(reference(byteArrayOf(1, 2, 3)), BattleCropProvenance(name, 1080, 2400, BattleCropBounds(10, 20, 110, 60))),
        matchId = MatchId(match), monotonicTimeNanos = mono)

    private fun archived(article: RealityArticle): ArchivedRealityArticle =
        RealityArticleArchiveMapper.createArchive(article.matchId!!, listOf(article)).articles.single()

    private fun reference(bytes: ByteArray): CropArtifactReference {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return CropArtifactReference("artifacts/crops/sha256/$hash.png", hash, bytes.size.toLong())
    }

    private fun saveArchive(root: File, name: String, id: String, battle: Boolean) {
        val image = archived(crop("crop-$id", id, "AnnouncementCrop", 1, 1))
        val archive = MatchArchive(matchId = id, articles = listOf(image) +
            if (battle) listOf(image.copy(articleId = "start-$id", payload = ArchivedMatchStarted)) else emptyList(),
            mediaIncluded = false)
        val file = File(root, "match_archives/$name").also { requireNotNull(it.parentFile).mkdirs() }
        file.outputStream().use { MatchArchivePackageWriter.write(archive, it) }
    }

    private inline fun <T> withRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("directory-crop-test-").toFile()
        return try { block(root) } finally { root.deleteRecursively() }
    }
}
