package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs

/** Optional, byte-identified field cases. Counterexamples are made on in-memory copies only. */
class RecordedPixelIncinerateFinishesTest {
    private class Fixture(
        val archive: MatchArchive,
        val audio: ReplayAudioRecovery,
        val ids: Map<String, Int>,
        val fast: Map<String, List<Move>>,
        val charged: Map<String, List<Move>>,
    ) {
        fun replay(source: MatchArchive = archive, sounds: ReplayAudioRecovery = audio) = MatchReplayModel(
            source, ids, fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged, audioRecovery = sounds)
    }

    private fun review(env: String, expectedDigest: String, verify: (Fixture) -> Unit) = runBlocking<Unit> {
        val path = System.getenv(env)
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        fun digest() = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals(expectedDigest, digest())
        val archive = ZipFile(file).use { zip ->
            zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().use { MatchArchiveSerializer.deserialize(it.readText()) }
        }
        val before = MatchArchiveSerializer.serialize(archive)
        val assets = File("src/main/assets")
        val catalog = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File(assets, "gamemaster.json").readText())
        val moves = catalog.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let {
            Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
                if (fast) it.energyGain else abs(it.energy), fast, it.turns.takeIf { fast })
        }
        val pokemon = catalog.pokemon.filter { it.speciesName in MatchReplayModel.referencedSpeciesNames(archive) }
        val fast = pokemon.associate { it.speciesName to it.fastMoves.mapNotNull { id -> move(id, true) } }
        val charged = pokemon.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> move(id, false) } }
        val ids = pokemon.associate { it.speciesName to it.dex }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, fast.values.flatten() + charged.values.flatten(),
            ids.values.toSet(), file::inputStream) { File(assets, it).readBytes() }
        verify(Fixture(archive, audio, ids, fast, charged))
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(expectedDigest, digest())
    }

    @Test fun pixelFinishesAndConflictingEvidence() = review("OVERDEX_PIXEL_INCINERATE_ARCHIVE",
        "7750b6a15daf43fd377e1dd1e4ec854a55a537d01703ca2757f91997ef25b10a") { f ->
        val model = f.replay()
        val go = requireNotNull(model.matchStartNanos)
        val rows = f.archive.articles.associateBy { it.articleId }
        val links = model.finishingFastMoveAssociations.filter { it.basis == ReplaySliverExchange.BASIS }
        assertEquals(listOf("Sealeo", "Vaporeon"), links.map { it.recipientSpeciesName })
        val starts = listOf(100.312, 122.456)
        val landings = listOf(101.776, 123.130)
        for ((index, link) in links.withIndex()) {
            assertEquals("Camerupt", link.attackerSpeciesName)
            assertEquals("PLAYER", link.attackingSide)
            assertEquals("OPPONENT", link.recipientSide)
            assertEquals("Incinerate", link.moveName)
            assertEquals(starts[index], (link.moveSourceAtNanos - go) / 1e9, .008)
            assertEquals(landings[index], (link.landingAtNanos - go) / 1e9, .002)
            assertNull(link.reception)
            assertTrue(link.evidenceArticleIds.all { it in rows })
            assertTrue(link.moveArticleIds.any { rows[it]?.payload is ArchivedCompleteHpBarObserved })
            assertTrue(link.moveArticleIds.any { rows[it]?.payload is ArchivedAudioCaptured })
            assertTrue(link.faintArticleIds.any { rows[it]?.payload is ArchivedOpponentBattleResourceCountMeasured })
            assertTrue(link.faintArticleIds.any { rows[it]?.payload is ArchivedAudioCaptured })
            val attack = model.eventMarkers.single { it.finishingAttribution == link && it.kind == ReplayEventMarkerKind.FAST_MOVE }
            val faint = model.eventMarkers.single { it.finishingAttribution == link && it.kind == ReplayEventMarkerKind.FAINT }
            assertTrue(faint.label.contains("Incinerate by Camerupt"))
            assertEquals(link.impactAtNanos, attack.atNanos)
            val bounds = faint.faintTiming as ReplayFaintTiming.Window
            assertTrue(requireNotNull(bounds.after).atNanos < link.impactAtNanos)
            assertTrue(link.impactAtNanos < bounds.by.atNanos)
            val at = model.sceneAt(attack.atNanos)
            val landed = at.fastMoveActions.single { it.finishingAssociation == link }
            assertEquals("Incinerate", landed.moveName)
            assertEquals(1f, landed.progress, .001f)
            assertTrue(at.chargedMoveActions.isEmpty())
            assertEquals(false, at.opponent?.isFainted)
            assertEquals(landed.startedAtNanos, faint.atNanos)
            assertEquals(false, model.sceneAt(faint.atNanos).opponent?.isFainted)
            assertEquals(true, model.sceneAt(bounds.by.atNanos).opponent?.isFainted)
            assertEquals(at, model.sceneAt(attack.atNanos))
        }
        assertTrue(links.last().faintArticleIds.any { rows[it]?.payload is ArchivedMatchEnded })
        val earthPower = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.atNanos - go in 115_000_000_000L..117_000_000_000L }
        assertTrue(earthPower.label.contains("Earth Power"))
        assertNull(earthPower.finishingAttribution)
        assertTrue(model.eventMarkers.any { it.side == "OPPONENT" && it.label.contains("Water Gun") &&
            abs(it.atNanos - links.last().impactAtNanos) < 100_000_000L })
        assertEquals("Vaporeon", model.sceneAt(go + 104_510_000_000L).opponent?.speciesName)
        val reordered = f.replay(f.archive.copy(articles = f.archive.articles.reversed()))
        assertEquals(links, reordered.finishingFastMoveAssociations.filter { it.basis == ReplaySliverExchange.BASIS })

        val final = links.last()
        fun credits(m: MatchReplayModel) = m.finishingFastMoveAssociations.any { it.recipientSpeciesName == "Vaporeon" }
        fun faint(m: MatchReplayModel) = m.eventMarkers.any { it.kind == ReplayEventMarkerKind.FAINT && it.label.contains("Vaporeon") }
        fun withArticle(a: ArchivedRealityArticle) = f.replay(f.archive.copy(articles = f.archive.articles + a))
        val profile = rows.values.single { it.monotonicTimeNanos == final.impactAtNanos &&
            (it.payload as? ArchivedCompleteHpBarObserved)?.side == "OPPONENT" }
        val survivor = profile.copy(articleId = "control-one-real-column", monotonicTimeNanos = final.impactAtNanos + 1,
            payload = (profile.payload as ArchivedCompleteHpBarObserved).copy(status = "LIVE_COLOR", liveColumns = 1))
        assertFalse(credits(withArticle(survivor)))
        assertFalse(faint(withArticle(survivor)))
        val competitor = profile.copy(articleId = "control-second-use", monotonicTimeNanos = final.impactAtNanos + 1,
            payload = ArchivedFastMoveUseObserved("second-use", "PLAYER", "OPPONENT", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "control"))
        val disputed = withArticle(competitor)
        assertFalse(credits(disputed))
        assertTrue("The cry and last-opponent departure still stand", faint(disputed))
        val noCry = f.replay(sounds = f.audio.copy(partialCryCandidates = f.audio.partialCryCandidates.filterNot { it.speciesId == 134 }))
        assertFalse(credits(noCry))
        assertTrue(noCry.eventMarkers.any { it.kind == ReplayEventMarkerKind.FAST_MOVE && it.atNanos == final.impactAtNanos && it.label.contains("Incinerate") })
        val missingSound = f.replay(sounds = f.audio.copy(mixedCandidates = f.audio.mixedCandidates.filterNot {
            it.moveName == final.moveName && it.estimatedOnsetNanos == final.moveSourceAtNanos }))
        assertFalse(credits(missingSound))
        assertTrue(faint(missingSound))
        assertTrue(missingSound.eventMarkers.none { it.side == "PLAYER" && it.kind == ReplayEventMarkerKind.FAST_MOVE && it.atNanos == final.impactAtNanos })
        val otherUse = profile.copy(articleId = "living-opponent-acts", monotonicTimeNanos = requireNotNull(final.departedBy).atNanos + 1,
            payload = ArchivedFastMoveUseObserved("late-water-gun", "OPPONENT", "PLAYER", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "control"))
        assertFalse(faint(withArticle(otherUse)))
        val badge = rows.values.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Vaporeon" }
        val replaced = badge.copy(articleId = "control-living-switch", monotonicTimeNanos = final.impactAtNanos + 1,
            payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364))
        val returned = badge.copy(articleId = "control-living-return", monotonicTimeNanos = final.faintSourceAtNanos + 1)
        val switched = f.replay(f.archive.copy(articles = f.archive.articles + replaced + returned + survivor.copy(monotonicTimeNanos = returned.monotonicTimeNanos!! + 1)))
        assertFalse(credits(switched))
        assertFalse(faint(switched))
    }

    @Test fun moondropChargedFinishDoesNotReceiveFastMoveCredit() = review("OVERDEX_MOONDROP_FAST_FINISH_CONTROL",
        "78384b3f6bb3fd2fddec52a4785e3e3ebee02dbf5ba04166f350b6a0896548f4") { f ->
        val replay = f.replay()
        val go = requireNotNull(replay.matchStartNanos)
        val end = f.archive.articles.single { it.payload is ArchivedMatchEnded }
        assertEquals("WIN", (end.payload as ArchivedMatchEnded).result)
        assertEquals(44.245, (end.monotonicTimeNanos!! - go) / 1e9, .002)
        val lastBar = f.archive.articles.filter { val p = it.payload as? ArchivedCompleteHpBarObserved
            p?.side == "OPPONENT" && p.status == "LIVE_COLOR" }.maxBy { it.monotonicTimeNanos!! }
        assertEquals(141, (lastBar.payload as ArchivedCompleteHpBarObserved).liveColumns)
        assertTrue(replay.eventMarkers.any { it.kind == ReplayEventMarkerKind.FAINT && it.label.contains("Sealeo") })
        assertTrue(replay.eventMarkers.any { it.kind == ReplayEventMarkerKind.FAINT && it.label.contains("Sneasel") })
        assertTrue(replay.finishingFastMoveAssociations.none { it.recipientSpeciesName == "Vaporeon" })
    }
}
