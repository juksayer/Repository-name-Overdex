package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.TerminalParticleSample
import com.example.overdex.battle.custody.TerminalParticleFeature
import com.example.overdex.battle.custody.TerminalFlashFeature
import com.example.overdex.data.GameMasterLoader
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordedDarmanitanFinishTest {
    @Test fun recordedCounterAndCry() = runBlocking<Unit> {
        val path = System.getenv("OVERDEX_DARMANITAN_ARCHIVE")
        assumeTrue(path != null)
        val file = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("4738ef05155d47ccb259b2d33236792aab709f36638afeb34867c6e759c2af96", hash)
        val archive = ZipFile(file).use { MatchArchiveSerializer.deserialize(it.getInputStream(it.getEntry("timeline.json")).bufferedReader().readText()) }
        val assets = File("src/main/assets")
        val gm = GameMasterLoader { File(assets,"gamemaster.json").readText() }
        val data = requireNotNull(ReplayLastOpponentFastFinish.read(archive, gm, file::inputStream) { File(assets,it).readBytes() })
        fun model(a: MatchArchive, e: ReplayLastOpponentFastEvidence = data) = MatchReplayModel(a, lastOpponentFastEvidence = e)
        val replay = model(archive)
        val link = replay.finishingFastMoveAssociations.singleOrNull { it.recipientSpeciesName == "Darmanitan" }
        assertNotNull("audio missing ${data.audio.unavailableReferences} ${data.audio.unavailableArtifacts}; mixed=" +
            data.audio.mixedCandidates.joinToString { "${it.moveName} ${it.estimatedOnsetNanos} ${it.fragments.map { f -> f.correlation }}" } +
            " cries=${data.audio.cries.map { it.speciesId }}", link)
        link!!
        assertEquals("Counter",link.moveName)
        assertEquals("Zapdos",link.attackerSpeciesName)
        assertNull(link.reception)
        fun faintMarker(m: MatchReplayModel) = m.eventMarkers.single {
            it.kind == ReplayEventMarkerKind.FAINT && it.finishingAttribution?.recipientSpeciesName == "Darmanitan"
        }
        val marker = faintMarker(replay)
        val window = marker.faintTiming as ReplayFaintTiming.Window
        val go = requireNotNull(replay.matchStartNanos)
        val lastAlive = requireNotNull(window.after)
        assertEquals(93_444L, (lastAlive.atNanos - go) / 1_000_000L)
        assertEquals(93_982L, (window.by.atNanos - go) / 1_000_000L)
        assertEquals(go + 93_982_125_006L, marker.atNanos)
        assertEquals(data.cryId, window.by.articleId)
        assertTrue(lastAlive.articleId in link.evidenceArticleIds)
        assertEquals("KO window 1:33–1:34 from GO",
            com.example.overdex.ui.screens.observatory.formatReplayFaintTime(window, go, replay.startNanos))
        assertEquals(link,replay.sceneAt(link.impactAtNanos).fastMoveActions.single { it.side == "PLAYER" }.finishingAssociation)
        assertTrue(replay.sceneAt(link.faintSourceAtNanos).opponent!!.isFainted)
        assertFalse(replay.sceneAt(link.moveSourceAtNanos).opponent!!.isFainted)
        val reversed = model(archive.copy(articles = archive.articles.reversed()))
        assertEquals(link, reversed.finishingFastMoveAssociations.single { it.recipientSpeciesName == "Darmanitan" })
        assertEquals(marker, faintMarker(reversed))
        val cry = archive.articles.single { it.articleId == data.cryId }
        val bar = archive.articles.last { row ->
            (row.payload as? ArchivedCompleteHpBarObserved)?.side == "OPPONENT" &&
                row.monotonicTimeNanos in link.moveSourceAtNanos..link.impactAtNanos
        }.payload as ArchivedCompleteHpBarObserved
        fun burst(atX: Double): ArchivedRealityArticle {
            val y = (bar.barBottom + 20).toDouble()
            val bounds = listOf(0, 0, bar.sourceWidth, bar.sourceHeight)
            val samples = (0..5).map { i ->
                val t = link.impactAtNanos + i * 50_000_000L
                val points = if (i == 1) List(4) { TerminalParticleFeature(atX + it, y, 3, 2, 2, 1.0, 1.0, 1.0, 1.0) } else emptyList()
                val flashes = if (i in 2..3) listOf(TerminalFlashFeature(atX, y, if (i == 2) 20 else 8, 5, 4, .8)) else emptyList()
                TerminalParticleSample(t, 0, t - 50_000_000L, "SAMPLED", bar.sourceWidth, bar.sourceHeight,
                    bounds, 1L, i.toLong(), 0L, points, flashes)
            }
            return cry.copy(articleId = "control-burst", sourceId = "TERMINAL_PARTICLE_WITNESS",
                payload = ArchivedTerminalParticleObservations("OPPONENT", link.moveSourceAtNanos,
                    link.impactAtNanos + 1_000_000_000L, samples, true, samples.size))
        }
        val burstRow = burst((bar.barLeft + bar.barRight) / 2.0)
        val withBurst = archive.copy(articles = archive.articles + burstRow)
        fun credit(a: MatchArchive, e: ReplayLastOpponentFastEvidence = data) =
            model(a, e).finishingFastMoveAssociations.singleOrNull { it.basis == ReplayLastOpponentFastFinish.BASIS }
        assertTrue(burstRow.articleId in requireNotNull(credit(withBurst)).faintArticleIds)
        assertFalse(burstRow.articleId in requireNotNull(credit(archive.copy(articles = archive.articles + burst(2.0)))).faintArticleIds)
        assertNull(credit(withBurst.copy(articles = withBurst.articles - cry)))
        assertNull(credit(withBurst, data.copy(audio = data.audio.copy(mixedCandidates = emptyList()))))
        assertNull(credit(withBurst, data.copy(opponentMoves = data.opponentMoves + "Counter")))
        val afterCry = link.impactAtNanos + 200_000_000L
        val switched = cry.copy(articleId = "living-switch", monotonicTimeNanos = afterCry,
            payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Torchic", 255))
        val returned = switched.copy(articleId = "living-return", monotonicTimeNanos = afterCry + 900_000_000L,
            payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Darmanitan", 555))
        assertNull(credit(withBurst.copy(articles = withBurst.articles + switched + returned)))
        val sliver = archive.articles.last { row -> val p = row.payload as? ArchivedActiveHpBarMeasured
            p?.side == "OPPONENT" && p.fillColor == "RED" && row.monotonicTimeNanos!! < link.moveSourceAtNanos }
        assertNull(credit(withBurst.copy(articles = withBurst.articles + sliver.copy(articleId = "still-alive", monotonicTimeNanos = afterCry))))
        val otherAttack = cry.copy(articleId = "competing-use", monotonicTimeNanos = link.moveSourceAtNanos + 50_000_000L,
            payload = ArchivedFastMoveUseObserved("second-use", "PLAYER", "OPPONENT", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "control"))
        assertNull(credit(withBurst.copy(articles = withBurst.articles + otherAttack)))
        val secondBall = cry.copy(articleId = "two-still-conscious", monotonicTimeNanos = link.moveSourceAtNanos - 1,
            payload = ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3))
        assertNull(credit(withBurst.copy(articles = withBurst.articles + secondBall)))
        val remaining = (sliver.payload as ArchivedActiveHpBarMeasured).copy(filledFraction = .15f)
        val highHp = listOf(-20L, -10L).map { delta ->
            sliver.copy(articleId = "survives-hit-$delta", monotonicTimeNanos = link.moveSourceAtNanos + delta,
                payload = remaining)
        }
        assertNull(credit(withBurst.copy(articles = withBurst.articles + highHp)))
        assertEquals(hash,ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
