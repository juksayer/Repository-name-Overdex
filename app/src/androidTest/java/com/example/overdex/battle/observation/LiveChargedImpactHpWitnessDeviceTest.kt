package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Charge preparation can preserve a single zero even while ordinary acquisition is stalled. */
class LiveChargedImpactHpWitnessDeviceTest {
    @Test fun singleChargedEmptyFrameKeepsItsSourceClockCueProvenanceAndArchiveWithoutAFastMove() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.start()
            fixture.deliver(0, normal())
            fixture.deliver(1, normal())
            val cue = fixture.prepare()
            fixture.deliver(100, centered(empty = true))
            fixture.deliver(101, missing())
            fixture.drain()
            val empty = fixture.timeline.getArticles().single {
                (it.payload as? ActiveHpBarMeasured)?.filledFraction == 0f
            }
            assertEquals(fixture.at(100), empty.monotonicTimeNanos)
            assertEquals(fixture.wall(100), empty.perceivedAt)
            assertEquals(ChargedImpactHpSource.id(ActivePokemonSide.OPPONENT), empty.sourceId.id)
            assertTrue(empty.evidenceReferences.orEmpty().contains(cue))
            assertEquals(240, (empty.payload as ActiveHpBarMeasured).barLeft)
            assertEquals(210, (empty.payload as ActiveHpBarMeasured).barTop)
            assertFalse(fixture.timeline.getArticles().any { it.monotonicTimeNanos == fixture.at(101) && it.payload is ActiveHpBarMeasured })
            fixture.assertNoInventedHits()
            val snapshot = MatchArchiveSource(MatchId(fixture.match.matchId), fixture.timeline).snapshot()
            val restored = Json.decodeFromString(MatchArchive.serializer(), Json.encodeToString(MatchArchive.serializer(), snapshot))
            val archived = restored.articles.single { (it.payload as? ArchivedActiveHpBarMeasured)?.filledFraction == 0f }
            assertEquals(empty.id.value, archived.articleId)
            assertEquals(empty.monotonicTimeNanos, archived.monotonicTimeNanos)
            assertEquals(empty.sourceId.id, archived.sourceId)
            assertEquals(empty.evidenceReferences, archived.evidenceReferences)
        } finally { fixture.close() }
    }

    @Test fun chargeReaderRemainsIndependentOfAStalledOrdinaryLocator() = runBlocking {
        val block = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture {
            if (block.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            System.nanoTime()
        }
        try {
            fixture.start()
            fixture.deliver(0, normal())
            fixture.deliver(1, normal())
            fixture.prepare()
            block.set(true)
            val blocked = fixture.enqueue(2, normal())
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            val impact = fixture.enqueue(100, centered(empty = true))
            withTimeout(5_000) { while (fixture.timeline.getArticles().none {
                (it.payload as? ActiveHpBarMeasured)?.filledFraction == 0f && it.monotonicTimeNanos == fixture.at(100)
            }) delay(10) }
            assertEquals(1L, release.count)
            release.countDown()
            withTimeout(5_000) { blocked.await(); impact.await() }
            fixture.deliver(102, missing())
            fixture.drain()
            fixture.assertNoInventedHits()
        } finally { release.countDown(); fixture.close() }
    }

    @Test fun chargedDamageAndTheFirstNormalReturnNeverEnterFastMoveDetectors() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.start()
            fixture.deliver(0, normal())
            fixture.deliver(1, normal())
            fixture.prepare()
            fixture.deliver(100, centered(empty = false, fill = 100))
            fixture.deliver(102, normal(fill = 100))
            fixture.deliver(106, normal(fill = 100))
            fixture.deliver(110, normal(fill = 100))
            delay(100)
            fixture.drain()
            assertTrue(fixture.timeline.getArticles().any { it.monotonicTimeNanos == fixture.at(100) &&
                ChargedImpactHpSource.matches(it.sourceId.id) && it.payload is ActiveHpBarMeasured })
            assertTrue(fixture.timeline.getArticles().any { it.monotonicTimeNanos == fixture.at(102) &&
                it.payload is ActiveHpBarMeasured && (it.payload as ActiveHpBarMeasured).side == ActivePokemonSide.OPPONENT })
            fixture.assertNoInventedHits()
        } finally { fixture.close() }
    }

    private class Fixture(clock: () -> Long = System::nanoTime) {
        val timeline = InMemoryRealityTimeline()
        private val input = Input()
        private val custody = InMemoryTestimonyCustody()
        val match = Match("charge-hp-device-${System.nanoTime()}", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        private val playerHub = LiveActiveHpBarFrameHub()
        private val opponentHub = LiveActiveHpBarFrameHub()
        private val witness = LiveActiveHpBarWitness(input, LiveBattlefieldHpTracker(BattleCalibration(
            trainerHpRegion = AnchorRegion(0f, .1f, .5f, .7f),
            opponentHpRegion = AnchorRegion(.5f, .1f, .5f, .7f)), elapsedTimeNanos = clock),
            { true }, playerHub, opponentHub)
        private val derived = listOf(LiveActiveHpBarDamageTickWitness.opponent(opponentHub),
            LiveActiveHpBarBorderPulseWitness.opponent(opponentHub),
            LiveActiveHpBarBorderCadenceWitness.opponent(opponentHub),
            LiveFastMoveRecipientVisualArtifactWitness.opponent(opponentHub))
        private val base = System.nanoTime() - 20_000_000_000L
        private val bitmaps = mutableListOf<Bitmap>()
        fun at(index: Int) = base + index * 33_000_000L
        fun wall(index: Int) = 1_700_000_000_000L + index * 33L
        suspend fun start() {
            derived.forEach { it.start(match) }
            witness.start(match)
            withTimeout(5_000) { input.frames.subscriptionCount.first { it == 2 } }
            delay(50)
        }
        suspend fun prepare(): String {
            custody.submitTestimony(SourceId("GET_READY_PHRASE_WITNESS"), GetReadyWitnessed,
                wall(1), .99f, emptyList(), at(1))
            drain()
            // Let every independent article subscriber consume the accepted preparation cue.
            delay(100)
            return timeline.getArticles().single { it.payload == GetReadyWitnessed }.id.value
        }
        suspend fun deliver(index: Int, bitmap: Bitmap) { withTimeout(5_000) { enqueue(index, bitmap).await() }; drain() }
        suspend fun enqueue(index: Int, bitmap: Bitmap): CompletableDeferred<Unit> {
            bitmaps += bitmap
            val delivery = Input.Delivery(CapturedVisualFrame(bitmap, wall(index), at(index)))
            input.frames.emit(delivery)
            return delivery.done
        }
        suspend fun drain() = withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
        fun assertNoInventedHits() {
            assertTrue(timeline.getArticles().none { it.payload is ActiveHpBarDamageTickMeasured ||
                it.payload is ActiveHpBarBorderCadenceMeasured || it.payload is FastMoveRecipientVisualArtifactMeasured ||
                it.payload is FastMoveUseObserved || it.payload is FastMoveEnergyDerived ||
                it.payload is ActivePokemonFaintedWitnessed })
        }
        suspend fun close() {
            witness.stop(); derived.forEach { it.stop() }
            try { drain() } finally { match.release(); bitmaps.forEach { it.recycle() } }
        }
    }

    private class Input : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        override val supportsIndependentFrameCollectors = true
        data class Delivery(val frame: CapturedVisualFrame, val remaining: AtomicInteger = AtomicInteger(2),
            val done: CompletableDeferred<Unit> = CompletableDeferred())
        val frames = MutableSharedFlow<Delivery>(extraBufferCapacity = 2)
        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) = supplyFrames { onVisualData(it.bitmap) }
        override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
            frames.collect { delivery -> try { onFrame(delivery.frame) }
                finally { if (delivery.remaining.decrementAndGet() == 0) delivery.done.complete(Unit) } }
        }
    }

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 420
        const val DARK = -0xe7d7db
        const val WHITE = -0x111112
        const val GREEN = -0xe10044
        fun missing() = Bitmap.createBitmap(IntArray(WIDTH * HEIGHT) { DARK }, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        fun normal(fill: Int = 160) = bitmap(20, 136, 160, 460, 136, fill)
        fun centered(empty: Boolean, fill: Int = 160) = bitmap(-300, 136, 0, 240, 210, if (empty) 0 else fill)
        fun bitmap(playerX: Int, playerY: Int, playerFill: Int, opponentX: Int, opponentY: Int, opponentFill: Int): Bitmap {
            val pixels = IntArray(WIDTH * HEIGHT) { DARK }
            fun bar(left: Int, top: Int, fill: Int) {
                if (left < 0) return
                val right = left + 220
                for (x in left until right) { pixels[top * WIDTH + x] = WHITE; pixels[(top + 22) * WIDTH + x] = WHITE }
                for (y in top..top + 22) { pixels[y * WIDTH + left] = WHITE; pixels[y * WIDTH + right - 1] = WHITE }
                for (x in left + 3 until left + fill) for (y in top + 5 until top + 18) pixels[y * WIDTH + x] = GREEN
            }
            bar(playerX, playerY, playerFill); bar(opponentX, opponentY, opponentFill)
            return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        }
    }
}
