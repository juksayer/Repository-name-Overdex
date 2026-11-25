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
        val cryTime = (marker.faintTiming as ReplayFaintTiming.Cry).onset
        val go = requireNotNull(replay.matchStartNanos)
        assertEquals(go + 93_982_125_006L, cryTime.atNanos)
        assertEquals(cryTime.atNanos, marker.atNanos)
        assertEquals(data.cryId, cryTime.articleId)
        assertEquals("Faint cry at 01:33.9 from GO",
            com.example.overdex.ui.screens.observatory.formatReplayFaintTime(marker.faintTiming, go, replay.startNanos))
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
        val noOutcome = archive.copy(articles = archive.articles.filterNot {
            it.payload is ArchivedMatchEnded || it.payload is ArchivedMatchOutcomeLabelWitnessed
        })
        val burstOnly = noOutcome.copy(articles = noOutcome.articles + burstRow)
        val cryAnchored = requireNotNull(ReplayLastOpponentFastFinish.read(burstOnly, gm, file::inputStream) { File(assets,it).readBytes() })
        val burstFinish = requireNotNull(credit(burstOnly, cryAnchored)) { "Qualified departure with no result" }
        assertEquals(link.moveSourceAtNanos, burstFinish.moveSourceAtNanos)
        assertEquals(link.faintSourceAtNanos, burstFinish.faintSourceAtNanos)
        assertTrue(burstRow.articleId in burstFinish.faintArticleIds)
        assertEquals(burstFinish, model(burstOnly, cryAnchored).sceneAt(burstFinish.impactAtNanos)
            .fastMoveActions.single { it.side == "PLAYER" }.finishingAssociation)
        val lateResult = cry.copy(articleId = "late-win", monotonicTimeNanos = cryTime.atNanos + 31_000_000_000L,
            payload = ArchivedMatchEnded("WIN"))
        assertEquals(cryTime.atNanos, credit(noOutcome.copy(articles = noOutcome.articles + lateResult))?.faintSourceAtNanos)
        assertNull(credit(noOutcome.copy(articles = noOutcome.articles + burst(2.0))))
        assertNull(credit(burstOnly.copy(articles = burstOnly.articles + cry.copy(articleId = "loss",
            monotonicTimeNanos = link.impactAtNanos + 2_000_000_000L, payload = ArchivedMatchEnded("LOSS")))))
        assertNull(credit(withBurst.copy(articles = withBurst.articles - cry)))
        assertNull(credit(withBurst, data.copy(audio = data.audio.copy(mixedCandidates = emptyList()))))
        assertNull(credit(withBurst, data.copy(defendingMoves = data.defendingMoves + "Counter")))
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
        // Feed the identical audio and geometry from the other side. Only the opponent
        // can own Counter here. The black frames stand in for a missed results label.
        val playerBall = cry.copy(articleId = "last-player", monotonicTimeNanos = link.moveSourceAtNanos - 1,
            payload = ArchivedPlayerPokeBallCountMeasured(1, 3))
        val strip = archive.articles.first { row -> row.sourceId == "ANNOUNCEMENT_CROP_CAPTURE" &&
            (row.payload as? ArchivedCropCaptured)?.cropName == "AnnouncementCrop" }
        val darkFrames = (1..2).map { n -> strip.copy(articleId = "dark-$n",
            monotonicTimeNanos = cryTime.atNanos + n * 200_000_000L) }
        val playerBar = cry.copy(articleId = "player-terminal-bar", sourceId = "COMPLETE_PLAYER_HP_BAR_FRAME_WITNESS",
            confidence = .99f, monotonicTimeNanos = cryTime.atNanos - 50_000_000L,
            payload = bar.copy(side = "PLAYER", status = "DAMAGE_COLOR", liveColumns = 0, damageColumns = 1,
                unmaskedUncertainColumns = 0, appearanceStartedAtNanos = null))
        fun reverseFinish(profile: ArchivedRealityArticle, darkIds: List<String> = darkFrames.map { it.articleId },
                          extra: List<ArchivedRealityArticle> = emptyList()) = ReplayLastOpponentFastFinish.resolve(
            data.copy(recipientSide = "PLAYER"), (listOf(cry, playerBall, profile) + darkFrames + extra).sortedBy { it.monotonicTimeNanos },
            requireNotNull(replay.sceneAt(link.moveSourceAtNanos).player), requireNotNull(replay.sceneAt(link.moveSourceAtNanos).opponent),
            link.attackerAppearanceAtNanos, link.recipientAppearanceAtNanos, darkIds)
        val reversedFinish = requireNotNull(reverseFinish(playerBar))
        assertEquals("OPPONENT", reversedFinish.attackingSide)
        assertEquals("PLAYER", reversedFinish.recipientSide)
        assertEquals("Counter", reversedFinish.moveName)
        assertTrue(darkFrames.all { it.articleId in reversedFinish.faintArticleIds })
        val orange = playerBar.payload as ArchivedCompleteHpBarObserved
        assertNull(reverseFinish(playerBar.copy(payload = orange.copy(liveColumns = 1))))
        assertNull(reverseFinish(playerBar.copy(payload = orange.copy(unmaskedUncertainColumns = 1))))
        val restored = sliver.copy(sourceId = "PLAYER_ACTIVE_HP_BAR_WITNESS",
            payload = (sliver.payload as ArchivedActiveHpBarMeasured).copy(side = "PLAYER"))
        assertNull(reverseFinish(playerBar, extra = listOf(restored.copy(articleId = "restored-before-cry",
            monotonicTimeNanos = cryTime.atNanos - 1))))
        assertNull(reverseFinish(playerBar, darkIds = listOf(darkFrames.first().articleId)))
        assertNull(reverseFinish(playerBar, extra = listOf(cry.copy(articleId = "competing-charge",
            monotonicTimeNanos = cryTime.atNanos - 10, payload = ArchivedChargeMoveUsedAnnounced))))
        assertEquals(hash,ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
