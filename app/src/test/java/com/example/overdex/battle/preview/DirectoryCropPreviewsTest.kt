package com.example.overdex.battle.preview

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.model.BattleResult
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
        selected.accept(event("start", "a", MatchStarted, 0, 0))
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
        selected.accept(event("start", "a", MatchStarted, 0, 0))
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
        val ended = image.copy(articleId = "end", perceivedAt = 2, monotonicTimeNanos = 2, payload = ArchivedMatchEnded("WIN"))
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
            val a = InMemoryRealityTimeline().also { it.append(event("start-a", "a", MatchStarted, 0, 0)); it.append(crop("a1", "a", "PlayerHpEvidenceCrop", 1, 1)) }
            val b = InMemoryRealityTimeline().also { it.append(event("start-b", "b", MatchStarted, 0, 0)); it.append(crop("b1", "b", "OpponentHpEvidenceCrop", 3, 3)) }
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
            val warm = InMemoryRealityTimeline().also { it.append(event("start-warm", "warm", MatchStarted, 0, 0)); it.append(crop("warm", "warm", "PlayerHpEvidenceCrop", 5, 5)) }
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

    @Test fun `navigation crops configuration species and one stray bar cannot qualify a battle`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(crop("menu", "a", "PlayerActiveSpeciesTextCrop", 10_000, ns(10)))
        selected.accept(event("configured", "a", PlayerTeamSlotConfigured(1, "Golbat", 42, "Wing Attack", listOf("Poison Fang")), 1, ns(1)))
        selected.accept(event("name", "a", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat", 42), 10_000, ns(10)))
        selected.accept(hp("stray", "a", 10))
        selected.accept(hp("duplicate-frame", "a", 10))
        assertTrue(selected.crops.isEmpty())
    }

    @Test fun `paired or repeated HP frames qualify raw bad calibration without GO or OCR success`() {
        for (paired in listOf(true, false)) {
            val selected = DirectoryCropSelection("a")
            selected.accept(crop("raw-wrong-box", "a", "PlayerActiveSpeciesTextCrop", 10_000, ns(10)))
            selected.accept(hp("first", "a", 10))
            selected.accept(if (paired) hp("second", "a", 10, ActivePokemonSide.OPPONENT)
                else hp("second", "a", 10).copy(perceivedAt = 10_200, monotonicTimeNanos = ns(10) + 200_000_000))
            assertEquals("raw-wrong-box", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        }
    }

    @Test fun `late combat evidence selects older battle pixels instead of newer menu scenery`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(crop("prebattle", "a", "AnnouncementCrop", 1_000, ns(1)))
        selected.accept(crop("battle", "a", "AnnouncementCrop", 10_000, ns(10)))
        selected.accept(crop("newer-menu", "a", "AnnouncementCrop", 50_000, ns(50)))
        assertTrue(selected.crops.isEmpty())
        selected.accept(event("damage", "a", ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, 1f, .9f, .1f), 10_000, ns(10)))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
    }

    @Test fun `delayed result rolls back already selected post-result evidence by source time`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
        selected.accept(crop("battle", "a", "PlayerPokeBallsCrop", 11_000, ns(11)))
        selected.accept(crop("result-scenery", "a", "PlayerPokeBallsCrop", 12_000, ns(12)))
        assertEquals("result-scenery", selected.crops.getValue(DirectoryCropSlot.PLAYER_BALLS).articleId)
        selected.accept(event("end", "a", MatchEnded(BattleResult.WIN), 12_000, ns(12)).copy(recordedAt = 100_000))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_BALLS).articleId)
    }

    @Test fun `post-KO blank crops cannot win without continued battle measurements`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(hp("left", "a", 10))
        selected.accept(hp("right", "a", 10, ActivePokemonSide.OPPONENT))
        selected.accept(crop("battle", "a", "PlayerActiveSpeciesTextCrop", 10_000, ns(10)))
        selected.accept(crop("fade", "a", "PlayerActiveSpeciesTextCrop", 15_000, ns(15)))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
    }

    @Test fun `HP preview stays on the closest measured frame instead of a later camera transition`() {
        val rows = listOf(
            event("start", "a", MatchStarted, 9_000, ns(9)),
            hp("player-bar", "a", 10),
            hp("opponent-bar", "a", 10, ActivePokemonSide.OPPONENT),
            crop("actual-bar", "a", "PlayerHpEvidenceCrop", 10_005, ns(10) + 5_000_000),
            crop("one-frame-later", "a", "PlayerHpEvidenceCrop", 10_120, ns(10) + 120_000_000),
            crop("bar-free-camera", "a", "PlayerHpEvidenceCrop", 10_564, ns(10) + 564_000_000),
            crop("latest-species", "a", "PlayerActiveSpeciesTextCrop", 10_564, ns(10) + 564_000_000))
        for (order in listOf(rows, rows.reversed())) {
            val selected = DirectoryCropSelection("a").also { order.forEach(it::accept) }
            assertEquals("actual-bar", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
            // The correction is HP-only: other fields retain their battle window.
            assertEquals("latest-species", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        }
    }

    @Test fun `opposite HP frames qualify a raw miscalibrated own box without own recognition`() {
        val wrong = crop("wrong-own-box", "a", "PlayerHpEvidenceCrop", 10_100, ns(10) + 100_000_000)
            .let { it.copy(payload = (it.payload as CropCaptured).let { captured ->
                captured.copy(cropProvenance = captured.cropProvenance.copy(bounds = BattleCropBounds(400, 500, 500, 600)))
            }) }
        val rows = listOf(hp("opponent-1", "a", 10, ActivePokemonSide.OPPONENT),
            hp("opponent-2", "a", 10, ActivePokemonSide.OPPONENT)
                .copy(perceivedAt = 10_100, monotonicTimeNanos = ns(10) + 100_000_000), wrong)
        val live = DirectoryCropSelection("a").also { rows.forEach(it::accept) }
        assertEquals("wrong-own-box", live.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        assertEquals(BattleCropBounds(400, 500, 500, 600), live.crops.getValue(DirectoryCropSlot.PLAYER_HP).provenance.bounds)
        val archive = RealityArticleArchiveMapper.createArchive(MatchId("a"), rows)
        val compact = MatchArchiveArticleSelector.select(archive.articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(live.crops, DirectoryCropSelection.fromArchivedArticles("a", compact)?.crops)
    }

    @Test fun `distant HP pixels stay absent when measured UI frames exist but start only fallback remains raw`() {
        val start = event("start", "a", MatchStarted, 9_000, ns(9))
        val distant = crop("camera", "a", "PlayerHpEvidenceCrop", 10_564, ns(10) + 564_000_000)
        val selected = DirectoryCropSelection("a")
        selected.accept(start)
        selected.accept(distant)
        assertEquals("camera", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        selected.accept(hp("player", "a", 10))
        selected.accept(hp("opponent", "a", 10, ActivePokemonSide.OPPONENT))
        assertFalse(selected.crops.containsKey(DirectoryCropSlot.PLAYER_HP))
        val archive = RealityArticleArchiveMapper.createArchive(MatchId("a"), listOf(start, distant,
            hp("player", "a", 10), hp("opponent", "a", 10, ActivePokemonSide.OPPONENT)))
        assertFalse(MatchArchiveArticleSelector.select(archive.articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            .any { it.payload is ArchivedCropCaptured })
    }

    @Test fun `same frame HP geometry resolves a tie without requiring it as a gate`() {
        val contained = crop("a-contained", "a", "PlayerHpEvidenceCrop", 10_000, ns(10))
        val outside = crop("z-outside", "a", "PlayerHpEvidenceCrop", 10_000, ns(10))
            .let { it.copy(payload = (it.payload as CropCaptured).let { captured ->
                captured.copy(cropProvenance = captured.cropProvenance.copy(bounds = BattleCropBounds(400, 500, 500, 600)))
            }) }
        val rows = listOf(hp("player", "a", 10), hp("opponent", "a", 10, ActivePokemonSide.OPPONENT), contained, outside)
        val selected = DirectoryCropSelection("a").also { rows.forEach(it::accept) }
        assertEquals("a-contained", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        val otherSourceSize = contained.let { it.copy(payload = (it.payload as CropCaptured).let { captured ->
            captured.copy(cropProvenance = captured.cropProvenance.copy(sourceWidth = 540, sourceHeight = 1200))
        }) }
        val mismatched = DirectoryCropSelection("a").also {
            (rows.take(2) + otherSourceSize).forEach(it::accept)
        }
        assertFalse(mismatched.crops.containsKey(DirectoryCropSlot.PLAYER_HP))
    }

    @Test fun `navigation boundary and terminal stop exclude later raw evidence`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
        selected.accept(crop("battle", "a", "PlayerHpEvidenceCrop", 10_000, ns(10)))
        selected.accept(event("menu", "a", OutOfBattleMenuWitnessed, 11_000, ns(11)))
        selected.accept(crop("navigation", "a", "PlayerHpEvidenceCrop", 12_000, ns(12)))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        selected.accept(event("stop", "a", ObservationSessionStoppedByUser, 13_000, ns(13)))
        selected.accept(hp("late-false-left", "a", 14))
        selected.accept(hp("late-false-right", "a", 14, ActivePokemonSide.OPPONENT))
        selected.accept(crop("after-stop", "a", "PlayerHpEvidenceCrop", 14_000, ns(14)))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
    }

    @Test fun `announcement prefers raw charge phase pixels over later combat scenery`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
        selected.accept(crop("charge-box-even-if-unreadable", "a", "AnnouncementCrop", 11_000, ns(11)))
        selected.accept(event("qte", "a", GetReadyWitnessed, 11_000, ns(11)))
        selected.accept(crop("scenery", "a", "AnnouncementCrop", 12_000, ns(12)))
        assertEquals("charge-box-even-if-unreadable", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
        selected.accept(crop("entry", "a", "AnnouncementCrop", 13_000, ns(13)))
        selected.accept(event("entry-cue", "a", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Muk", 89), 13_000, ns(13))
            .copy(sourceId = SourceId("ENTRY_ANNOUNCEMENT_SPECIES_WITNESS")))
        selected.accept(event("late-qte-after-result", "a", GetReadyWitnessed, 15_000, ns(15)))
        selected.accept(event("end", "a", MatchEnded(BattleResult.WIN), 14_000, ns(14)))
        assertEquals("entry", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
    }

    @Test fun `ordinary repeated species readings do not favor an older announcement crop`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
        selected.accept(crop("earlier", "a", "AnnouncementCrop", 11_000, ns(11)))
        selected.accept(event("badge", "a", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat", 42), 11_000, ns(11)))
        selected.accept(crop("latest", "a", "AnnouncementCrop", 12_000, ns(12)))
        assertEquals("latest", selected.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT).articleId)
    }

    @Test fun `legacy result and navigation timestamps bound otherwise monotonic crops consistently`() {
        for (boundary in listOf<TestimonyPayload>(MatchEnded(BattleResult.WIN), OutOfBattleMenuWitnessed)) {
            val articles = listOf(
                event("start", "a", MatchStarted, 10_000, ns(10)),
                crop("battle", "a", "PlayerPokeBallsCrop", 11_000, ns(11)),
                crop("after", "a", "PlayerPokeBallsCrop", 12_000, ns(12)),
                event("legacy-boundary", "a", boundary, 12_000, null))
            for (rows in listOf(articles, articles.reversed())) {
                val selected = DirectoryCropSelection("a")
                rows.forEach(selected::accept)
                assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_BALLS).articleId)
            }
        }
    }

    @Test fun `mixed clock candidate ordering uses capture wall time as a stable whole-set fallback`() {
        val rows = listOf(
            event("start", "a", MatchStarted, 10_000, ns(10)),
            crop("mono-disagrees", "a", "PlayerActiveSpeciesTextCrop", 10_100, ns(12)),
            crop("wall-latest", "a", "PlayerActiveSpeciesTextCrop", 10_300, null),
            crop("between", "a", "PlayerActiveSpeciesTextCrop", 10_200, ns(11)))
        for (order in listOf(rows, rows.reversed(), listOf(rows[2], rows[0], rows[3], rows[1]))) {
            val selected = DirectoryCropSelection("a")
            order.forEach(selected::accept)
            assertEquals("wall-latest", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        }
    }

    @Test fun `live and archived selection agree for out of order source evidence`() {
        val rows = listOf(
            crop("menu", "a", "OpponentPokeBallsCrop", 50_000, ns(50)),
            event("end", "a", MatchEnded(BattleResult.WIN), 12_000, ns(12)),
            crop("battle", "a", "OpponentPokeBallsCrop", 10_000, ns(10)),
            event("damage", "a", ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), 10_000, ns(10)))
        val selected = DirectoryCropSelection("a").also { rows.forEach(it::accept) }
        val archive = RealityArticleArchiveMapper.createArchive(MatchId("a"), rows)
        val archived = requireNotNull(DirectoryCropSelection.fromBattleArchive(archive))
        assertEquals(selected.crops, archived.crops)
        assertEquals("battle", archived.crops.getValue(DirectoryCropSlot.OPPONENT_BALLS).articleId)
    }

    @Test fun `bounded pending history retains the last actual battle against a long navigation tail`() {
        val selected = DirectoryCropSelection("a")
        selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
        selected.accept(crop("battle", "a", "PlayerActiveSpeciesTextCrop", 10_000, ns(10)))
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        repeat(2_100) { index ->
            selected.accept(crop("menu-$index", "a", "PlayerActiveSpeciesTextCrop", 100_000L + index, ns(100) + index))
        }
        assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
    }

    @Test fun `terminal result and stop survive more than the rolling context limit`() {
        for (terminal in listOf<TestimonyPayload>(MatchEnded(BattleResult.WIN), ObservationSessionStoppedByUser)) {
            val selected = DirectoryCropSelection("a")
            selected.accept(event("start", "a", MatchStarted, 10_000, ns(10)))
            selected.accept(crop("battle", "a", "PlayerActiveSpeciesTextCrop", 10_000, ns(10)))
            selected.accept(event("terminal", "a", terminal, 11_000, ns(11)))
            repeat(8_300) { index ->
                selected.accept(hp("noise-$index", "a", 20L + index,
                    if (index % 2 == 0) ActivePokemonSide.PLAYER else ActivePokemonSide.OPPONENT))
            }
            selected.accept(crop("after-terminal", "a", "PlayerActiveSpeciesTextCrop", 8_319_000, ns(8_319)))
            assertEquals("battle", selected.crops.getValue(DirectoryCropSlot.PLAYER_SPECIES).articleId)
        }
    }

    @Test fun `cold archive and unpolled live batch preserve battle pixels through a long navigation tail`() {
        val rows = mutableListOf(event("start", "a", MatchStarted, 10_000, ns(10)),
            crop("battle", "a", "PlayerHpEvidenceCrop", 10_000, ns(10)),
            event("menu", "a", OutOfBattleMenuWitnessed, 11_000, ns(11)))
        repeat(2_100) { index -> rows += crop("menu-$index", "a", "PlayerHpEvidenceCrop", 100_000L + index, ns(100) + index) }
        val live = DirectoryCropSelection("a").also { rows.forEach(it::accept) }
        assertEquals("battle", live.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        val archive = RealityArticleArchiveMapper.createArchive(MatchId("a"), rows)
        val cold = requireNotNull(DirectoryCropSelection.fromBattleArchive(archive))
        assertEquals(live.crops, cold.crops)
    }

    @Test fun `equal source timestamps choose the same reference independent of delivery order`() {
        val rows = listOf(event("start", "a", MatchStarted, 10_000, ns(10)),
            crop("a", "a", "PlayerHpEvidenceCrop", 10_000, ns(10)),
            crop("z", "a", "PlayerHpEvidenceCrop", 10_000, ns(10)))
        for (order in listOf(rows, rows.reversed())) {
            val selected = DirectoryCropSelection("a")
            order.forEach(selected::accept)
            assertEquals("z", selected.crops.getValue(DirectoryCropSlot.PLAYER_HP).articleId)
        }
    }

    @Test fun `new live navigation-only session cannot replace the previous battle preview`() = runBlocking {
        withRoot { root ->
            val battle = InMemoryRealityTimeline().also {
                it.append(event("start", "battle", MatchStarted, 1_000, ns(1)))
                it.append(crop("battle", "battle", "PlayerHpEvidenceCrop", 1_000, ns(1)))
            }
            val menu = InMemoryRealityTimeline().also { it.append(crop("menu", "menu", "AnnouncementCrop", 20_000, ns(20))) }
            var source = MatchArchiveSource(MatchId("battle"), battle)
            var count = 0
            val feed = DirectoryCropPreviewFeed(root, sources = { listOf(source) }, decodeLocal = { null })
            val states = feed.states().drop(1).take(2).onEach {
                if (++count == 1) source = MatchArchiveSource(MatchId("menu"), menu)
            }.toList()
            assertEquals(listOf("battle", "battle"), states.map { it.matchId })
            assertTrue(states.all { it.crops.keys == setOf(DirectoryCropSlot.PLAYER_HP) })
        }
    }

    private fun ns(seconds: Long) = seconds * 1_000_000_000L

    private fun event(id: String, match: String, payload: TestimonyPayload, wall: Long, mono: Long?) = RealityArticle(
        ArticleId(id), wall, wall, SourceId("TEST"), payload, matchId = MatchId(match), monotonicTimeNanos = mono)

    private fun hp(id: String, match: String, seconds: Long, side: ActivePokemonSide = ActivePokemonSide.PLAYER) =
        event(id, match, ActiveHpBarMeasured(side, 10, 20, 110, 25, .5f, 1080, 2400), seconds * 1000, ns(seconds))

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
