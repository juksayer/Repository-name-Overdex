package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveArticleSelector
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the independent terminal reader with actual Bitmaps and the ordinary evidence pipeline. */
class LiveTerminalHpBarWitnessDeviceTest {
    @Test fun onePixelVerifiedEmptyFrameCompletesDamageWithoutAnotherEmptyFrame() = runBlocking {
        val fixture = PipelineFixture(observeDamage = true)
        try {
            fixture.start()
            fixture.deliver(0, Bar.LOW_RED, Bar.LOW_RED)
            fixture.deliver(1, Bar.LOW_RED, Bar.LOW_RED)
            fixture.deliver(2, Bar.LOW_RED, Bar.LOW_RED)
            fixture.deliver(3, Bar.MISSING, Bar.EMPTY)
            fixture.deliver(4, Bar.MISSING, Bar.MISSING)
            val tick = try {
                withTimeout(5_000) {
                    while (fixture.timeline.getArticles().none { it.payload is ActiveHpBarDamageTickMeasured }) delay(10)
                    fixture.timeline.getArticles().single { it.payload is ActiveHpBarDamageTickMeasured }
                }
            } catch (failure: Exception) {
                throw AssertionError("Terminal damage not published: ${fixture.hpArticles()}; latest=${fixture.latestOpponentSample()}", failure)
            }
            val damage = tick.payload as ActiveHpBarDamageTickMeasured
            assertEquals(ActivePokemonSide.OPPONENT, damage.damagedSide)
            assertTrue(damage.beforeFraction > 0f && damage.beforeFraction <= .20f)
            assertEquals(0f, damage.afterFraction, 0f)
            assertEquals(fixture.sourceAt(3), tick.monotonicTimeNanos)
            assertEquals(fixture.wallAt(3), tick.perceivedAt)
            fixture.drain()
            assertEquals(1, fixture.timeline.getArticles().count { it.payload is ActiveHpBarDamageTickMeasured })
            assertEquals(1, fixture.hpArticles().count {
                val hp = it.payload as ActiveHpBarMeasured
                hp.filledFraction == 0f && hp.fillColor == "EMPTY"
            })
            assertTrue(fixture.timeline.getArticles().none { it.payload is ActivePokemonFaintedWitnessed })
        } finally {
            fixture.close()
        }
    }

    @Test fun independentReaderCapturesOneEmptyFrameWhileTheOrdinaryLocatorIsBlocked() = runBlocking {
        val blockNextLocatorClock = AtomicBoolean(false)
        val locatorEntered = CountDownLatch(1)
        val releaseLocator = CountDownLatch(1)
        val fixture = PipelineFixture(elapsedTimeNanos = {
            if (blockNextLocatorClock.compareAndSet(true, false)) {
                locatorEntered.countDown()
                check(releaseLocator.await(10, TimeUnit.SECONDS)) { "The ordinary locator was not released" }
            }
            System.nanoTime()
        })
        try {
            fixture.start()
            fixture.deliver(0, Bar.RED, Bar.RED)
            fixture.deliver(1, Bar.RED, Bar.RED)

            blockNextLocatorClock.set(true)
            val blockedFrame = fixture.enqueue(2, Bar.RED, Bar.RED)
            assertTrue("The ordinary locator did not reach the controlled stall",
                withContext(Dispatchers.IO) { locatorEntered.await(5, TimeUnit.SECONDS) })
            val emptyFrame = fixture.enqueue(3, Bar.RED, Bar.EMPTY)
            withTimeout(5_000) {
                while (fixture.hpArticles().none {
                    val hp = it.payload as ActiveHpBarMeasured
                    hp.side == ActivePokemonSide.OPPONENT && hp.filledFraction == 0f &&
                        it.monotonicTimeNanos == fixture.sourceAt(3)
                }) delay(10)
            }
            // This proves the source frame reached Custody before the ordinary locator can finish.
            assertEquals(1L, releaseLocator.count)
            val duringStall = fixture.hpArticles().single {
                (it.payload as ActiveHpBarMeasured).filledFraction == 0f
            }
            assertEquals(fixture.sourceAt(3), duringStall.monotonicTimeNanos)
            assertEquals(fixture.wallAt(3), duringStall.perceivedAt)
            assertNoInventedBattleEvents(fixture)

            // Only one delivered frame contains the empty outline; its next frame contains no bars.
            val vanishedFrame = fixture.enqueue(4, Bar.MISSING, Bar.MISSING)
            releaseLocator.countDown()
            withTimeout(5_000) {
                blockedFrame.await()
                emptyFrame.await()
                vanishedFrame.await()
            }
            fixture.drain()
            val articles = fixture.hpArticles()
            assertEquals(1, articles.count { (it.payload as ActiveHpBarMeasured).filledFraction == 0f })
            assertFalse(articles.any { it.monotonicTimeNanos == fixture.sourceAt(4) })
            assertSourceTimeNeverRewinds(articles)
            assertNoInventedBattleEvents(fixture)
        } finally {
            releaseLocator.countDown()
            fixture.close()
        }
    }

    @Test fun oneVisibleEmptyFrameSurvivesCustodyTimelineAndCompactArchiveWithoutInventingAFaint() = runBlocking {
        val fixture = PipelineFixture()
        try {
            fixture.start()
            // The ordinary reader supplies owned geometry; a later verified red frame arms the watch.
            fixture.deliver(0, Bar.RED, Bar.RED)
            fixture.deliver(1, Bar.RED, Bar.RED)
            fixture.deliver(2, Bar.RED, Bar.EMPTY)
            // The empty outline is present for exactly one delivered frame.
            fixture.deliver(3, Bar.MISSING, Bar.MISSING)
            fixture.drain()

            val articles = fixture.hpArticles()
            val empty = articles.single { (it.payload as ActiveHpBarMeasured).filledFraction == 0f }
            val hp = empty.payload as ActiveHpBarMeasured
            assertEquals(ActivePokemonSide.OPPONENT, hp.side)
            assertEquals(fixture.sourceAt(2), empty.monotonicTimeNanos)
            assertEquals(fixture.wallAt(2), empty.perceivedAt)
            assertEquals(WIDTH, hp.sourceWidth)
            assertEquals(HEIGHT, hp.sourceHeight)
            assertTrue(hp.barLeft in 459..461 && hp.barRight in 679..681)
            assertTrue(hp.barTop in 135..137 && hp.barBottom in 157..159)
            assertFalse(articles.any { it.monotonicTimeNanos == fixture.sourceAt(3) })
            assertSourceTimeNeverRewinds(articles)
            assertNoInventedBattleEvents(fixture)

            val archive = MatchArchiveSource(MatchId(fixture.match.matchId), fixture.timeline).snapshot()
            val restored = Json.decodeFromString(MatchArchive.serializer(),
                Json.encodeToString(MatchArchive.serializer(), archive))
            val compact = MatchArchiveArticleSelector.select(restored.articles,
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            val archivedEmpty = compact.single {
                (it.payload as? ArchivedActiveHpBarMeasured)?.filledFraction == 0f
            }
            assertEquals(empty.id.value, archivedEmpty.articleId)
            assertEquals(empty.sourceId.id, archivedEmpty.sourceId)
            assertEquals(fixture.sourceAt(2), archivedEmpty.monotonicTimeNanos)
            assertEquals(fixture.wallAt(2), archivedEmpty.perceivedAt)
            val archivedHp = archivedEmpty.payload as ArchivedActiveHpBarMeasured
            assertEquals("OPPONENT", archivedHp.side)
            assertEquals(WIDTH, archivedHp.sourceWidth)
            assertEquals(HEIGHT, archivedHp.sourceHeight)
        } finally {
            fixture.close()
        }
    }

    @Test fun disappearingRedBarsAndExpiredGeometryNeverBecomeEmptyMeasurements() = runBlocking {
        val fixture = PipelineFixture()
        try {
            fixture.start()
            fixture.deliver(0, Bar.RED, Bar.RED)
            fixture.deliver(1, Bar.RED, Bar.RED)
            fixture.deliver(2, Bar.MISSING, Bar.MISSING)
            fixture.deliver(3, Bar.MISSING, Bar.MISSING)
            // Reappearing outlines more than 500 ms after the last verified red bar cannot reuse it.
            fixture.deliver(25, Bar.EMPTY, Bar.EMPTY)
            fixture.drain()

            val articles = fixture.hpArticles()
            assertTrue("Red seed measurements must actually have reached Custody", articles.isNotEmpty())
            assertTrue(articles.none { (it.payload as ActiveHpBarMeasured).filledFraction == 0f })
            assertTrue(articles.none { it.monotonicTimeNanos == fixture.sourceAt(2) ||
                it.monotonicTimeNanos == fixture.sourceAt(3) ||
                it.monotonicTimeNanos == fixture.sourceAt(25) })
            assertSourceTimeNeverRewinds(articles)
            assertNoInventedBattleEvents(fixture)
        } finally {
            fixture.close()
        }
    }

    @Test fun redOpponentCannotArmPlayerAndOldFramesCannotRewindTerminalEvidence() = runBlocking {
        val fixture = PipelineFixture()
        try {
            fixture.start()
            fixture.deliver(0, Bar.GREEN, Bar.RED)
            fixture.deliver(1, Bar.GREEN, Bar.RED)
            // A green player bar has no terminal watch, even while the opponent watch is active.
            fixture.deliver(2, Bar.EMPTY, Bar.RED)
            fixture.deliver(3, Bar.MISSING, Bar.EMPTY)
            fixture.drain()

            val beforeOldFrame = fixture.hpArticles()
            val empty = beforeOldFrame.single { (it.payload as ActiveHpBarMeasured).filledFraction == 0f }
            assertEquals(ActivePokemonSide.OPPONENT, (empty.payload as ActiveHpBarMeasured).side)
            assertEquals(fixture.sourceAt(3), empty.monotonicTimeNanos)
            assertFalse(beforeOldFrame.any {
                val hp = it.payload as ActiveHpBarMeasured
                hp.side == ActivePokemonSide.PLAYER && hp.filledFraction == 0f
            })

            // Publication order advances, but the pixels still carry their original older source clock.
            fixture.deliver(1, Bar.GREEN, Bar.RED)
            fixture.drain()
            assertEquals(beforeOldFrame.size, fixture.hpArticles().size)
            assertSourceTimeNeverRewinds(fixture.hpArticles())
            assertNoInventedBattleEvents(fixture)
        } finally {
            fixture.close()
        }
    }

    private class PipelineFixture(
        elapsedTimeNanos: () -> Long = System::nanoTime, private val observeDamage: Boolean = false,
    ) {
        val timeline = InMemoryRealityTimeline()
        val input = MulticastInput()
        private val enabled = AtomicBoolean(true)
        private val custody = InMemoryTestimonyCustody()
        val match = Match("terminal-hp-device-${System.nanoTime()}", custody = custody,
            realityTimeline = timeline, pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        private val playerHub = LiveActiveHpBarFrameHub()
        private val opponentHub = LiveActiveHpBarFrameHub()
        private val damageWitness = LiveActiveHpBarDamageTickWitness.opponent(opponentHub)
        private val witness = LiveActiveHpBarWitness(input,
            LiveBattlefieldHpTracker(BattleCalibration(
                trainerHpRegion = AnchorRegion(0f, .10f, .5f, .50f),
                opponentHpRegion = AnchorRegion(.5f, .10f, .5f, .50f),
            ), elapsedTimeNanos = elapsedTimeNanos), enabled::get,
            playerHub, opponentHub)
        private val sourceBase = System.nanoTime() - 2_000_000_000L
        private val bitmaps = mutableListOf<Bitmap>()

        suspend fun start() {
            if (observeDamage) {
                damageWitness.start(match)
                withTimeout(5_000) {
                    while (timeline.getArticles().none { it.sourceId.id == damageWitness.observerId.id }) delay(10)
                }
            }
            witness.start(match)
            input.awaitBothCollectors()
        }
        fun sourceAt(index: Int) = sourceBase + index * FRAME_INTERVAL_NANOS
        fun wallAt(index: Int) = 1_700_000_000_000L + index * 33L
        suspend fun deliver(index: Int, player: Bar, opponent: Bar) {
            val bitmap = bitmapWithBars(player, opponent).also(bitmaps::add)
            input.deliver(CapturedVisualFrame(bitmap, wallAt(index), sourceAt(index)))
            drain()
        }
        suspend fun enqueue(index: Int, player: Bar, opponent: Bar): CompletableDeferred<Unit> {
            val bitmap = bitmapWithBars(player, opponent).also(bitmaps::add)
            return input.enqueue(CapturedVisualFrame(bitmap, wallAt(index), sourceAt(index)))
        }
        suspend fun drain() = withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
        fun hpArticles() = timeline.getArticles().filter { it.payload is ActiveHpBarMeasured }
        fun latestOpponentSample(): String = opponentHub.latestSample()?.let {
            "${it.measurement}, color=${it.fillColor}, allowed=${it.fastMoveEvidenceAllowed}, at=${it.capturedAtMonotonicTimeNanos}"
        } ?: "none"
        suspend fun close() {
            witness.stop()
            if (observeDamage) damageWitness.stop()
            try { drain() } finally {
                match.release()
                bitmaps.forEach { it.recycle() }
            }
        }
    }

    /** Each collector sees the same source frame; no competing-consumer Channel is used here. */
    private class MulticastInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        override val supportsIndependentFrameCollectors = true
        private data class Delivery(
            val frame: CapturedVisualFrame,
            val remaining: AtomicInteger = AtomicInteger(2),
            val consumed: CompletableDeferred<Unit> = CompletableDeferred(),
        )
        private val frames = MutableSharedFlow<Delivery>(
            replay = 0,
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val latest = AtomicReference<VisualFramePublication?>(null)
        private var sequence = 0L
        override fun latestVisualPublication() = latest.get()
        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) =
            supplyFrames { onVisualData(it.bitmap) }
        override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
            frames.collect { delivery ->
                try {
                    onFrame(delivery.frame)
                } finally {
                    if (delivery.remaining.decrementAndGet() == 0) delivery.consumed.complete(Unit)
                }
            }
        }
        suspend fun awaitBothCollectors() = withTimeout(5_000) {
            frames.subscriptionCount.first { it == 2 }
        }
        suspend fun deliver(frame: CapturedVisualFrame) {
            withTimeout(5_000) { enqueue(frame).await() }
        }
        suspend fun enqueue(frame: CapturedVisualFrame): CompletableDeferred<Unit> {
            val publication = VisualFramePublication(73L, ++sequence, System.nanoTime())
            latest.set(publication)
            val delivery = Delivery(frame.copy(publication = publication))
            frames.emit(delivery)
            return delivery.consumed
        }
    }

    private enum class Bar { GREEN, RED, LOW_RED, EMPTY, MISSING }

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 420
        const val BAR_TOP = 136
        const val FRAME_INTERVAL_NANOS = 33_000_000L

        fun bitmapWithBars(player: Bar, opponent: Bar): Bitmap {
            val pixels = IntArray(WIDTH * HEIGHT) { 0xff182825.toInt() }
            fun bar(left: Int, state: Bar) {
                if (state == Bar.MISSING) return
                val right = left + 220
                for (x in left until right) {
                    pixels[BAR_TOP * WIDTH + x] = 0xffeeeeee.toInt()
                    pixels[(BAR_TOP + 22) * WIDTH + x] = 0xffeeeeee.toInt()
                }
                for (y in BAR_TOP..BAR_TOP + 22) {
                    pixels[y * WIDTH + left] = 0xffeeeeee.toInt()
                    pixels[y * WIDTH + right - 1] = 0xffeeeeee.toInt()
                }
                val fillWidth = when (state) {
                    Bar.GREEN -> 160
                    Bar.RED -> 46
                    Bar.LOW_RED -> 28
                    Bar.EMPTY, Bar.MISSING -> 0
                }
                val fillColor = if (state == Bar.RED || state == Bar.LOW_RED) 0xffee3540.toInt() else 0xff1effbc.toInt()
                for (x in left + 3 until left + fillWidth) {
                    for (y in BAR_TOP + 5 until BAR_TOP + 18) pixels[y * WIDTH + x] = fillColor
                }
            }
            bar(20, player)
            bar(460, opponent)
            return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        }

        fun assertSourceTimeNeverRewinds(articles: List<RealityArticle>) {
            articles.groupBy { (it.payload as ActiveHpBarMeasured).side }.values.forEach { sideArticles ->
                val clocks = sideArticles.map { requireNotNull(it.monotonicTimeNanos) }
                assertTrue("A side must publish each source time at most once, in increasing order",
                    clocks.zipWithNext().all { (before, after) -> after > before })
            }
        }

        fun assertNoInventedBattleEvents(fixture: PipelineFixture) {
            assertTrue(fixture.timeline.getArticles().none {
                it.payload is ActivePokemonFaintedWitnessed || it.payload is FastMoveUseObserved ||
                    it.payload is FastMoveEnergyDerived || it.payload is ChargedMoveEnergySpent ||
                    it.payload is MatchStarted
            })
        }
    }
}
