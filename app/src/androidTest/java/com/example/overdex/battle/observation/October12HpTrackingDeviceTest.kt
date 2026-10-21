package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs recorded pixels through the live shared scanner without touching saved profiles. */
class October12HpTrackingDeviceTest {
    @Test fun oneFrameSubscriptionDeliversBothSidesWithIdenticalCaptureTimes() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("battle/oct12/frame-055.png")
            .use(BitmapFactory::decodeStream)!!
        val completed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val subscriptions = java.util.concurrent.atomic.AtomicInteger()
        val input = object : com.example.overdex.model.observation.ObservationInput {
            override val source = com.example.overdex.model.observation.SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (android.graphics.Bitmap) -> Unit) = error("Use capture timestamps")
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
                subscriptions.incrementAndGet()
                repeat(30) { i ->
                    onFrame(CapturedVisualFrame(bitmap, 1000L + i * 34, 1_000_000_000L + i * 34_000_000L))
                    kotlinx.coroutines.yield()
                }
                completed.complete(Unit)
            }
        }
        val knowledge = object : com.example.overdex.data.PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): com.example.overdex.model.Pokemon? = null
            override suspend fun getPokemonById(id: Int): com.example.overdex.model.Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val custody = com.example.overdex.battle.custody.InMemoryTestimonyCustody()
        val match = Match("HP_FRAME_FANOUT_TEST", custody = custody,
            realityTimeline = com.example.overdex.battle.reality.InMemoryRealityTimeline(), pokemonKnowledge = knowledge)
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(
            trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
            opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
        ))
        val witness = LiveActiveHpBarWitness(input, tracker, { true }, LiveActiveHpBarFrameHub(), LiveActiveHpBarFrameHub())
        try {
            witness.start(match)
            kotlinx.coroutines.withTimeout(15_000) { completed.await() }
            val hp = custody.getRecords().filterIsInstance<com.example.overdex.battle.custody.TestimonyRecord>()
                .filter { it.payload is com.example.overdex.battle.custody.ActiveHpBarMeasured }
            assertEquals(1, subscriptions.get())
            assertEquals(60, hp.size)
            assertEquals(30, hp.groupBy { it.monotonicTimeNanos }.size)
            hp.groupBy { it.monotonicTimeNanos }.values.forEach { pair ->
                assertEquals(ActivePokemonSide.entries.toSet(), pair.map {
                    (it.payload as com.example.overdex.battle.custody.ActiveHpBarMeasured).side
                }.toSet())
                assertEquals(setOf("PLAYER_ACTIVE_HP_BAR_WITNESS", "OPPONENT_ACTIVE_HP_BAR_WITNESS"), pair.map { it.sourceId.id }.toSet())
                assertEquals(1, pair.map { it.timestamp }.toSet().size)
            }
        } finally {
            witness.stop()
            match.release()
            bitmap.recycle()
        }
    }

    @Test fun recoversBothBarsAndReportsSharedFrameProcessingTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("battle/oct12/frame-055.png")
            .use(BitmapFactory::decodeStream)!!
        val calibration = BattleCalibration(
            trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
            opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
        )
        val tracker = LiveBattlefieldHpTracker(calibration)
        val elapsed = mutableListOf<Long>()
        try {
            repeat(40) { i ->
                val frame = CapturedVisualFrame(bitmap, 1000L + i * 34L, 1_000_000_000L + i * 34_000_000L)
                val start = System.nanoTime()
                val measurements = tracker.measureFrame(frame)
                val player = requireNotNull(measurements[ActivePokemonSide.PLAYER])
                val opponent = requireNotNull(measurements[ActivePokemonSide.OPPONENT])
                if (i >= 10) elapsed += System.nanoTime() - start
                assertTrue(player.centerX < opponent.centerX)
                assertEquals(192f, opponent.width.toFloat(), 5f)
                assertTrue(opponent.right > 576)
                assertTrue(opponent.filledFraction > .96f)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("hp_timing", "frames=${elapsed.size} mean_ms=${elapsed.average() / 1_000_000} max_ms=${elapsed.max() / 1_000_000.0}")
            })
        } finally { bitmap.recycle() }
    }
}
