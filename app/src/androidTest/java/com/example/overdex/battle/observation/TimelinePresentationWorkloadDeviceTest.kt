package com.example.overdex.battle.observation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.forecast.OpponentEnergyForecast
import com.example.overdex.battle.inference.FastMoveCadenceInference
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/**
 * Native integration checks for presentation work removed from the live evidence path.
 * These are deterministic workload checks, not claims about real-phone recognition speed.
 */
class TimelinePresentationWorkloadDeviceTest {
    @Test fun rawBurstIsPublishedBeforeResultWithoutInvalidatingTheShellCountOrLosingSourceEnvelopes() = runBlocking {
        DroidballOverlayPresentation.reset()
        val custody = InMemoryTestimonyCustody()
        val reality = InMemoryRealityTimeline()
        val match = Match("presentation-workload-${System.nanoTime()}", custody = custody,
            realityTimeline = reality, pokemonKnowledge = knowledge)
        val base = System.nanoTime() - 10_000_000_000L
        val wall = 1_700_000_000_000L
        suspend fun drain() = withTimeout(15_000) { match.awaitAcceptedEvidencePublished() }
        fun submit(payload: TestimonyPayload, offset: Long, refs: List<String> = emptyList()) =
            custody.submitTestimony(SourceId(SOURCE), payload, wall + offset / 1_000_000L,
                .93f, refs, base + offset)
        val invalidations = AtomicInteger()
        val observer = SnapshotStateObserver { callback -> callback() }
        try {
            submit(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, sealeo.name, sealeo.id), 0)
            submit(MatchStarted, 100_000_000L)
            drain()
            val speciesId = reality.getArticles().single { it.payload is ActivePokemonSpeciesWitnessed }.id.value
            val shellHistory = match.battleMemory.timeline
            val started = BattleEvent(type = BattleEventType.BATTLE_STARTED, timestamp = wall,
                sourceArticleId = ArticleId(speciesId))
            shellHistory.record(started)
            Snapshot.sendApplyNotifications()
            observer.start()
            observer.observeReads(Any(), { invalidations.incrementAndGet() }) { shellHistory.eventCount }

            // Numeric measurements are preserved immediately. A late final delivery
            // deliberately has an earlier source time than its predecessor.
            val accepted = (0 until BURST_SIZE).map { index ->
                val offset = if (index == BURST_SIZE - 1) 250_000_000L else 200_000_000L + index * 1_000_000L
                submit(ActiveHpBarMeasured(if (index % 2 == 0) ActivePokemonSide.PLAYER else ActivePokemonSide.OPPONENT,
                    10, 100, 200, 120, .8f, 700, 420), offset,
                    listOf(speciesId, "synthetic-source-frame-$index"))
            }
            drain()
            Snapshot.sendApplyNotifications()
            val live = reality.getArticles()
            val measured = live.filter { it.sourceId.id == SOURCE && it.payload is ActiveHpBarMeasured }
            assertEquals(BURST_SIZE, measured.size)
            assertEquals(accepted.map { it.sequenceNumber }, measured.map { it.sequenceNumber })
            measured.zip(accepted).forEach { (article, input) ->
                assertEquals(input.timestamp, article.perceivedAt)
                assertEquals(input.monotonicTimeNanos, article.monotonicTimeNanos)
                assertEquals(input.evidenceReferences, article.evidenceReferences)
                assertEquals(input.payload, article.payload)
                assertEquals(input.confidence, article.confidence)
            }
            assertTrue(measured.last().monotonicTimeNanos!! < measured[measured.lastIndex - 1].monotonicTimeNanos!!)
            assertTrue("No result is required to publish accepted measurements", live.none { it.payload is MatchEnded })
            assertTrue(live.none { it.payload is FastMoveUseObserved || it.payload is ActivePokemonFaintedWitnessed })
            assertEquals(listOf(started), shellHistory.events)
            assertEquals(1, shellHistory.eventCount)
            assertEquals(0, invalidations.get())
            assertTrue(shellHistory.records.containsAll(measured))

            val result = submit(MatchEnded(BattleResult.WIN), 900_000_000L, listOf(measured.last().id.value))
            drain()
            val ended = reality.getArticles().single { it.payload is MatchEnded }
            assertEquals(result.monotonicTimeNanos, ended.monotonicTimeNanos)
            assertEquals(result.timestamp, ended.perceivedAt)
            val archive = MatchArchiveSource(MatchId(match.matchId), reality).snapshot()
            val restored = MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive))
            assertEquals(archive, restored)
            measured.forEach { article ->
                val stored = restored.articles.single { it.articleId == article.id.value }
                assertEquals(article.monotonicTimeNanos, stored.monotonicTimeNanos)
                assertEquals(article.perceivedAt, stored.perceivedAt)
                assertEquals(article.recordedAt, stored.recordedAt)
                assertEquals(article.evidenceReferences, stored.evidenceReferences)
                assertEquals(article.sequenceNumber, stored.sequenceNumber)
                assertEquals(article.predecessorIds.map { it.value }, stored.predecessorIds)
            }

            // The observer is live: an actual semantic history change still notifies it.
            shellHistory.record(BattleEvent(type = BattleEventType.BATTLE_ENDED, timestamp = result.timestamp,
                result = BattleResult.WIN, sourceArticleId = ended.id))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations.get())
            assertEquals(2, shellHistory.eventCount)
        } finally {
            observer.stop()
            observer.clear()
            match.release()
            DroidballOverlayPresentation.reset()
        }
    }

    @Test fun unrelatedMeasurementBurstReusesCandidateRankingAndForecastHistoryWhileLateChargeStillRevisesIt() = runBlocking {
        val candidates = FastMoveCadenceInference(knowledge)
        val forecast = OpponentEnergyForecast()
        forecast.registerMoves(sealeo.name, sealeo.fastMoves)
        fun event(id: String, at: Long, payload: TestimonyPayload) = RealityArticle(
            ArticleId(id), 1_700_000_000_000L + at / 1_000_000L, 1_700_000_000_001L + at / 1_000_000L,
            SourceId(SOURCE), payload, monotonicTimeNanos = at)
        val species = event("species", 0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, sealeo.name, sealeo.id))
        val go = event("go", 100_000_000L, MatchStarted)
        val hit = event("witnessed-hit", 4_000_000_000L, FastMoveUseObserved("witnessed-use",
            ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER, attackerSpeciesName = sealeo.name,
            evidenceKinds = listOf("HP_BORDER_PULSE")))
        candidates.accept(species); candidates.accept(go)
        candidates.accept(hit)
        forecast.observe(species); forecast.observe(go); forecast.observe(hit)
        for (index in 0..60) forecast.observeBattlefield(index * 100_000_000L, true)
        val initialCandidate = requireNotNull(candidates.candidateSnapshot(ActivePokemonSide.OPPONENT))
        val initialForecast = requireNotNull(forecast.estimate(5_000_000_000L))
        val rankingBuilds = candidates.candidatePresentationBuildCount
        val projectionWork = forecast.projectionWork()

        repeat(BURST_SIZE) { index ->
            val sample = event("raw-$index", 1_000_000_000L + index * 1_000_000L,
                RawTestimony("unrelated frame metadata $index"))
            candidates.accept(sample)
            forecast.observe(sample)
            assertSame(initialCandidate, candidates.candidateSnapshot(ActivePokemonSide.OPPONENT))
            assertEquals(initialForecast, forecast.estimate(5_000_000_000L))
        }
        assertEquals(rankingBuilds, candidates.candidatePresentationBuildCount)
        assertEquals(projectionWork, forecast.projectionWork())

        // A source-earlier cue delivered after all those reads must invalidate
        // the projection and remove the elapsed charge phase from its hypothesis.
        val lateCharge = event("late-charge", 2_000_000_000L, GetReadyWitnessed)
        candidates.accept(lateCharge)
        forecast.observe(lateCharge)
        val revised = requireNotNull(forecast.estimate(5_000_000_000L))
        assertTrue(revised.paused)
        assertTrue(revised.energy < initialForecast.energy)
        assertTrue(forecast.projectionWork().builds > projectionWork.builds)
        assertEquals(initialCandidate.remainingMoveNames,
            candidates.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
    }

    private companion object {
        const val SOURCE = "TIMELINE_PRESENTATION_WORKLOAD_TEST"
        const val BURST_SIZE = 512
        val sealeo = Pokemon(364, "Sealeo", listOf(PokemonType.WATER, PokemonType.ICE), "Hoenn",
            fastMoves = listOf(Move("Water Gun", PokemonType.WATER, 3, 3, true, 1),
                Move("Powder Snow", PokemonType.ICE, 5, 8, true, 2)), chargedMoves = emptyList())
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = sealeo.takeIf { it.name.equals(name, ignoreCase = true) }
            override suspend fun getPokemonById(id: Int) = sealeo.takeIf { it.id == id }
            override suspend fun getAllSpeciesNames() = setOf(sealeo.name)
        }
    }
}
