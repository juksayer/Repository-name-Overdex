package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.archive.ArchivedHpObservationPipelineMeasured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveArticleSelector
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.HpObservationPipelineMeasured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import com.example.overdex.model.observation.VisualFramePublication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Bitmap -> live witness -> Custody -> Match -> Timeline, without private battle assets. */
class HpObservationDiagnosticsDeviceTest {
    @Test fun actualFramesDistinguishFoundMissingAndDisabledAndArchiveBothFlushes() = runBlocking {
        val fixture = PipelineFixture()
        try {
            fixture.start()
            fixture.deliver(0, player = true, opponent = true)
            fixture.deliver(1, player = false, opponent = false)
            fixture.enabled.set(false)
            fixture.deliver(2, player = true, opponent = true)
            fixture.enabled.set(true)
            fixture.deliver(3, player = true, opponent = false)
            fixture.drain()

            // Producer progress stays visible even without delivering another frame.
            fixture.input.advancePublicationWithoutDelivery(7L)
            withTimeout(8_000) {
                while (fixture.summaries().none { it.reason == "PERIODIC" }) delay(20)
            }
            fixture.witness.stop()
            fixture.drain()
            val summaries = fixture.summaries()
            assertTrue(summaries.any { it.reason == "PERIODIC" })
            val summary = summaries.single { it.reason == "SESSION_STOP" }
            val counts = summary.cumulative
            assertEquals(4L, counts.framesReceived)
            assertEquals(3L, counts.enabledFramesReceived)
            assertEquals(1L, counts.disabledFramesReceived)
            assertEquals(3L, counts.locationPasses)
            assertEquals(6L, counts.publishedSequenceAdvances)
            assertEquals(7L, summary.latestPublicationSequenceNumber)
            assertEquals(0, summary.pendingSubmissions)
            assertEquals(3L, counts.player.locationAttempts)
            assertEquals(2L, counts.player.found)
            assertEquals(1L, counts.player.missesByReason["NO_CANDIDATE"])
            assertEquals(2L, counts.player.submitted)
            assertEquals(2L, counts.player.recorded)
            assertEquals(3L, counts.opponent.locationAttempts)
            assertEquals(1L, counts.opponent.found)
            assertEquals(2L, counts.opponent.missesByReason.values.sum())
            assertEquals(1L, counts.opponent.missesByReason["NO_CANDIDATE"])
            assertEquals(1L, counts.opponent.submitted)
            assertEquals(1L, counts.opponent.recorded)
            assertEquals(0L, counts.player.recordedWithoutSubmission)
            assertEquals(0L, counts.opponent.recordedWithoutSubmission)
            assertEquals(2L, counts.player.sourceToTimeline.sampleCount)
            assertEquals(1L, counts.opponent.submissionToTimeline.sampleCount)
            assertEquals(4L, counts.sourceToPublication.sampleCount)
            assertEquals(4L, counts.publicationToReceipt.sampleCount)
            val locator = requireNotNull(counts.locatorBreakdown)
            assertEquals(3L, locator.timedPasses)
            assertEquals(0L, locator.untimedPasses)
            assertEquals(0L, locator.cacheHitPasses)
            assertEquals(3L, locator.lockWait.sampleCount)
            assertEquals(3L, locator.threadCpu.sampleCount + locator.threadCpuUnavailablePasses)
            assertTrue(locator.pixelExtraction.calls > 0L)
            assertEquals(locator.pixelExtraction.calls, locator.geometryDetection.calls)
            assertTrue(locator.recoverySearch.calls > 0L)
            val playerSampling = requireNotNull(counts.player.postLocatorSampling)
            val opponentSampling = requireNotNull(counts.opponent.postLocatorSampling)
            assertEquals(2L, playerSampling.calls)
            assertEquals(1L, opponentSampling.calls)
            assertEquals(2L, playerSampling.elapsed.sampleCount)
            assertEquals(1L, opponentSampling.elapsed.sampleCount)
            assertEquals(2L, playerSampling.threadCpu.sampleCount + playerSampling.cpuUnavailablePasses)
            assertEquals(1L, opponentSampling.threadCpu.sampleCount + opponentSampling.cpuUnavailablePasses)

            val geometry = fixture.timeline.getArticles().filter { it.payload is ActiveHpBarMeasured }
            assertEquals(3, geometry.size)
            assertTrue(geometry.all { article ->
                val hp = article.payload as ActiveHpBarMeasured
                hp.sourceWidth == WIDTH && hp.sourceHeight == HEIGHT && hp.filledFraction in .65f.. .80f
            })
            geometry.forEach { article ->
                val index = if (article.monotonicTimeNanos == fixture.sourceAt(0)) 0 else 3
                assertEquals(fixture.sourceAt(index), article.monotonicTimeNanos)
                assertEquals(fixture.wallAt(index), article.perceivedAt)
            }
            assertEquals(setOf(ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT),
                geometry.filter { it.monotonicTimeNanos == fixture.sourceAt(0) }
                    .map { (it.payload as ActiveHpBarMeasured).side }.toSet())
            assertFalse(geometry.any { it.monotonicTimeNanos == fixture.sourceAt(1) ||
                it.monotonicTimeNanos == fixture.sourceAt(2) })
            assertNoInventedBattleEvents(fixture.timeline)

            val archive = MatchArchiveSource(MatchId(fixture.match.matchId), fixture.timeline).snapshot()
            val encoded = Json.encodeToString(MatchArchive.serializer(), archive)
            val restored = Json.decodeFromString(MatchArchive.serializer(), encoded)
            val compact = MatchArchiveArticleSelector.select(restored.articles,
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            val retained = compact.mapNotNull { it.payload as? ArchivedHpObservationPipelineMeasured }
            assertTrue(retained.any { it.reason == "PERIODIC" })
            assertEquals(summary.cumulative, retained.single { it.reason == "SESSION_STOP" }.cumulative)
            assertEquals(summary.histogramUpperBoundsNanos,
                retained.single { it.reason == "SESSION_STOP" }.histogramUpperBoundsNanos)
        } finally {
            fixture.close()
        }
    }

    @Test fun recordedCountersWaitForActualAppendAndNormalResultFlushesTheCompletedPipeline() = runBlocking {
        val enteredAppend = CountDownLatch(1)
        val releaseAppend = CountDownLatch(1)
        val blockFirstHp = AtomicBoolean(true)
        val ledger = InMemoryRealityTimeline()
        val timeline = object : RealityTimeline {
            override fun append(article: RealityArticle) {
                if (article.payload is ActiveHpBarMeasured && blockFirstHp.compareAndSet(true, false)) {
                    enteredAppend.countDown()
                    check(releaseAppend.await(5, TimeUnit.SECONDS)) { "HP append was not released" }
                }
                ledger.append(article)
            }
            override fun getArticles() = ledger.getArticles()
        }
        val fixture = PipelineFixture(timeline)
        try {
            fixture.start()
            fixture.deliver(0, player = true, opponent = true)
            assertTrue("Live HP testimony never reached the Timeline", enteredAppend.await(3, TimeUnit.SECONDS))
            val beforeAppend = requireNotNull(fixture.match.hpObservationDiagnostics.takeSummary(
                System.nanoTime(), fixture.input.latestVisualPublication(), HpDiagnosticSummaryReason.SESSION_STOP))
            assertEquals(1L, beforeAppend.cumulative.player.submitted)
            assertEquals(1L, beforeAppend.cumulative.opponent.submitted)
            assertEquals(0L, beforeAppend.cumulative.player.recorded)
            assertEquals(0L, beforeAppend.cumulative.opponent.recorded)
            assertEquals(2, beforeAppend.pendingSubmissions)
            assertTrue(ledger.getArticles().none { it.payload is ActiveHpBarMeasured })

            releaseAppend.countDown()
            fixture.drain()
            val resultSource = fixture.sourceAt(0) + 1_000_000_000L
            fixture.custody.submitTestimony(SourceId("YOU_WIN_WITNESS"), RawTestimony("You Win!"),
                fixture.wallAt(0) + 1_000L, null, emptyList(), resultSource)
            withTimeout(5_000) {
                while (fixture.summaries().none { it.reason == "MATCH_END" }) delay(10)
            }
            fixture.drain()
            val summary = fixture.summaries().single { it.reason == "MATCH_END" }
            assertEquals(1L, summary.cumulative.player.recorded)
            assertEquals(1L, summary.cumulative.opponent.recorded)
            assertEquals(0, summary.pendingSubmissions)
            assertEquals(1L, summary.cumulative.player.submissionToTimeline.sampleCount)
            assertNotNull(summary.cumulative.player.submissionToTimeline.maximumNanos)
            val end = timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(resultSource, end.monotonicTimeNanos)
            assertEquals(fixture.wallAt(0) + 1_000L, end.perceivedAt)
            assertNoInventedBattleEvents(timeline)
        } finally {
            releaseAppend.countDown()
            fixture.close()
        }
    }

    private class PipelineFixture(val timeline: RealityTimeline = InMemoryRealityTimeline()) {
        val input = ControlledInput()
        val enabled = AtomicBoolean(true)
        val custody = InMemoryTestimonyCustody()
        val match = Match("hp-pipeline-device-${System.nanoTime()}", custody = custody,
            realityTimeline = timeline, pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        val witness = LiveActiveHpBarWitness(input, LiveBattlefieldHpTracker(BattleCalibration(
            trainerHpRegion = AnchorRegion(0f, .10f, .5f, .50f),
            opponentHpRegion = AnchorRegion(.5f, .10f, .5f, .50f),
        )), enabled::get, LiveActiveHpBarFrameHub(), LiveActiveHpBarFrameHub())
        private val sourceBase = System.nanoTime() - 2_000_000_000L
        private val bitmaps = mutableListOf<Bitmap>()

        suspend fun start() {
            witness.start(match)
            withTimeout(3_000) { input.ready.await() }
        }
        fun sourceAt(index: Int) = sourceBase + index * 300_000_000L
        fun wallAt(index: Int) = 1_700_000_000_000L + index * 300L
        suspend fun deliver(index: Int, player: Boolean, opponent: Boolean) {
            val bitmap = bitmapWithBars(player, opponent).also(bitmaps::add)
            input.deliver(CapturedVisualFrame(bitmap, wallAt(index), sourceAt(index)))
        }
        suspend fun drain() = withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
        fun summaries() = timeline.getArticles().mapNotNull { it.payload as? HpObservationPipelineMeasured }
        suspend fun close() {
            witness.stop()
            try { drain() } finally {
                match.release()
                input.close()
                bitmaps.forEach { it.recycle() }
            }
        }
    }

    private class ControlledInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        val ready = CompletableDeferred<Unit>()
        private data class Delivery(val frame: CapturedVisualFrame, val consumed: CompletableDeferred<Unit>)
        private val frames = Channel<Delivery>(Channel.UNLIMITED)
        private val latest = AtomicReference<VisualFramePublication?>(null)
        private var sequence = 0L
        override fun latestVisualPublication() = latest.get()
        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) =
            supplyFrames { onVisualData(it.bitmap) }
        override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
            ready.complete(Unit)
            for (delivery in frames) {
                onFrame(delivery.frame)
                delivery.consumed.complete(Unit)
            }
        }
        suspend fun deliver(frame: CapturedVisualFrame) {
            val publication = VisualFramePublication(42L, ++sequence, System.nanoTime())
            latest.set(publication)
            val consumed = CompletableDeferred<Unit>()
            frames.send(Delivery(frame.copy(publication = publication), consumed))
            withTimeout(3_000) { consumed.await() }
        }
        fun advancePublicationWithoutDelivery(sequence: Long) {
            this.sequence = sequence
            latest.set(VisualFramePublication(42L, sequence, System.nanoTime()))
        }
        fun close() { frames.close() }
    }

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 420

        fun bitmapWithBars(player: Boolean, opponent: Boolean): Bitmap {
            val pixels = IntArray(WIDTH * HEIGHT) { 0xff182825.toInt() }
            fun bar(left: Int) {
                val right = left + 220
                for (x in left until right) {
                    pixels[100 * WIDTH + x] = 0xffeeeeee.toInt()
                    pixels[122 * WIDTH + x] = 0xffeeeeee.toInt()
                }
                for (y in 100..122) {
                    pixels[y * WIDTH + left] = 0xffeeeeee.toInt()
                    pixels[y * WIDTH + right - 1] = 0xffeeeeee.toInt()
                }
                for (x in left + 3 until left + 160) for (y in 105 until 118) {
                    pixels[y * WIDTH + x] = 0xff1effbc.toInt()
                }
            }
            if (player) bar(20)
            if (opponent) bar(460)
            return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        }

        fun assertNoInventedBattleEvents(timeline: RealityTimeline) {
            assertTrue(timeline.getArticles().none { it.payload is FastMoveUseObserved ||
                it.payload is FastMoveEnergyDerived || it.payload is ChargedMoveEnergySpent ||
                it.payload is MatchStarted })
        }
    }
}
