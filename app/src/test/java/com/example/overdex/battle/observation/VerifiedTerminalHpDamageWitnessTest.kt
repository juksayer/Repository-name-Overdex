package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the live numeric handoff and custody publication, without Android pixel fixtures. */
class VerifiedTerminalHpDamageWitnessTest {
    @Test fun `verified empty flag reaches live damage witness and preserves the only empty source frame`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.startAndSettleLowBaseline()
            fixture.sample(300, 0f, HpBarFillColor.EMPTY)
            fixture.awaitDamageCount(2)
            val terminal = fixture.damage().last()
            val payload = terminal.payload as ActiveHpBarDamageTickMeasured
            assertEquals(.12f, payload.beforeFraction, .00001f)
            assertEquals(0f, payload.afterFraction, 0f)
            assertEquals(.12f, payload.lostFraction, .00001f)
            assertEquals(fixture.base + 300_000_000L, terminal.monotonicTimeNanos)
            assertEquals(1_000_300L, terminal.perceivedAt)
            assertTrue(terminal.sourceId.id.contains("DAMAGE"))
            fixture.assertNoInventedBattleEvents()
        } finally { fixture.close() }
    }

    @Test fun `phase blocked empty cannot become damage and later ordinary terminal sample still works`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.startAndSettleLowBaseline()
            fixture.sample(300, 0f, HpBarFillColor.EMPTY, allowed = false)
            fixture.sample(400, .12f, HpBarFillColor.RED)
            fixture.sample(433, .12f, HpBarFillColor.RED)
            fixture.sample(466, 0f, HpBarFillColor.EMPTY)
            fixture.awaitDamageCount(2)
            assertEquals(listOf(fixture.base + 100_000_000L, fixture.base + 466_000_000L),
                fixture.damage().map { it.monotonicTimeNanos })
            fixture.assertNoInventedBattleEvents()
        } finally { fixture.close() }
    }

    @Test fun `unverified empty still follows settling in the live witness`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.startAndSettleLowBaseline()
            fixture.sample(300, 0f)
            fixture.sample(450, 0f)
            fixture.awaitDamageCount(2)
            assertEquals(fixture.base + 300_000_000L, fixture.damage().last().monotonicTimeNanos)
            fixture.assertNoInventedBattleEvents()
        } finally { fixture.close() }
    }

    private class Fixture {
        private val custody = InMemoryTestimonyCustody()
        private val timeline = InMemoryRealityTimeline()
        private val match = Match("verified-terminal-handoff", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        private val hub = LiveActiveHpBarFrameHub()
        private val witness = LiveActiveHpBarDamageTickWitness.opponent(hub)
        val base = System.nanoTime()

        suspend fun startAndSettleLowBaseline() {
            witness.start(match)
            withTimeout(5_000) {
                while (timeline.getArticles().none { it.sourceId.id == witness.observerId.id &&
                        (it.payload as? WitnessOperating)?.operating == true }) delay(1)
            }
            // Availability precedes collection setup; elapsed time is not readiness.
            withTimeout(5_000) { while (hub.sampleSubscriberCount == 0) delay(1) }
            sample(0, .30f)
            sample(33, .30f)
            sample(100, .12f, HpBarFillColor.RED)
            sample(250, .12f, HpBarFillColor.RED)
            awaitDamageCount(1)
            assertEquals(base + 100_000_000L, damage().single().monotonicTimeNanos)
            sample(283, .12f, HpBarFillColor.RED)
        }

        fun sample(millis: Long, fill: Float, color: HpBarFillColor? = null, allowed: Boolean = true) {
            hub.publish(LiveActiveHpBarSample(
                measurement = ActiveHpBarMeasurement(380, 450, 572, 468, fill, .97f),
                appearance = null,
                recipientVisual = null,
                capturedAtWallTimeMillis = 1_000_000L + millis,
                capturedAtMonotonicTimeNanos = base + millis * 1_000_000L,
                sourceWidth = 672,
                sourceHeight = 1493,
                fastMoveEvidenceAllowed = allowed,
                fillColor = color,
            ))
        }

        fun damage(): List<RealityArticle> = timeline.getArticles().filter { it.payload is ActiveHpBarDamageTickMeasured }

        suspend fun awaitDamageCount(count: Int) {
            withTimeout(5_000) {
                while (damage().size < count) delay(1)
                match.awaitAcceptedEvidencePublished()
            }
            assertEquals(count, damage().size)
        }

        fun assertNoInventedBattleEvents() {
            assertTrue(timeline.getArticles().none {
                it.payload is ActivePokemonFaintedWitnessed || it.payload is FastMoveUseObserved || it.payload is MatchEnded
            })
        }

        fun close() {
            witness.stop()
            match.release()
        }
    }
}
