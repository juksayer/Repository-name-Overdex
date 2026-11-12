package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class DecisionTimerCaptureGateTest {
    private var now = 20 * SECOND
    private fun gate() = DecisionTimerCaptureGate(now = { now })

    @Test fun `ordinary combat and match start leave timer asleep`() {
        val gate = gate()
        gate.observe(article(MatchStarted))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        gate.observe(article(RawTestimony("NOT VERY EFFECTIVE")))
        assertFalse(gate.isEnabled())
    }

    @Test fun `either faint and every charged phase cue admit bounded source timed capture`() {
        for (cue in listOf(GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
                ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER),
                ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT))) {
            val gate = gate()
            gate.observe(article(cue))
            assertTrue(gate.isEnabled())
            assertFalse(gate.acceptsFrame(now - 1))
            assertTrue(gate.acceptsFrame(now))
            now += 15 * SECOND
            assertFalse(gate.isEnabled())
        }
    }

    @Test fun `known timer narrows capture and disappearance finishes it early`() {
        val gate = gate()
        gate.request(now)
        now += SECOND
        gate.measured(4, now)
        now += 4 * SECOND
        assertTrue(gate.isEnabled())
        gate.measured(null, now)
        assertFalse(gate.isEnabled())
        gate.request(now)
        gate.request(now - SECOND)
        assertFalse(gate.isEnabled())
        now += SECOND
        gate.request(now)
        assertTrue(gate.isEnabled())
    }

    @Test fun `zero timer has a bounded one second clearance tail`() {
        val gate = gate()
        gate.request(now)
        gate.measured(0, now)
        now += SECOND - 1
        assertTrue(gate.isEnabled())
        now++
        assertFalse(gate.isEnabled())
    }

    @Test fun `repeated phase cues and oversized timer never extend the hard window`() {
        val gate = gate()
        val original = now
        gate.request(now)
        repeat(14) {
            now += SECOND
            gate.request(now)
            gate.measured(15, now)
        }
        now = original + 15 * SECOND
        assertFalse(gate.isEnabled())
    }

    @Test fun `old equal future and fully expired cues cannot reopen resolved timer`() {
        val gate = gate()
        gate.request(now)
        gate.measured(4, now)
        now += SECOND
        gate.measured(null, now)
        gate.request(now)
        gate.request(now - SECOND)
        gate.request(now + SECOND)
        gate.request(now - 16 * SECOND)
        assertFalse(gate.isEnabled())
    }

    @Test fun `late earlier dial disappearance cannot close newer assignment`() {
        val gate = gate()
        gate.request(now)
        now += 16 * SECOND
        gate.request(now)
        gate.measured(null, now - SECOND)
        assertTrue(gate.isEnabled())
        gate.measured(4, now)
        now += SECOND
        gate.measured(null, now)
        assertFalse(gate.isEnabled())
    }

    @Test fun `previous timer disappearance cannot finish a new assignment before its numeric dial appears`() {
        val gate = gate()
        val filter = DecisionTimerReadingFilter()
        gate.request(now)
        assertNull(filter.accept(true, "1", now))
        now += SECOND / 4
        val firstDial = requireNotNull(filter.accept(true, "1", now))
        gate.measured(firstDial.seconds, now)
        now += 3 * SECOND
        assertFalse(gate.isEnabled())

        // The prior dial's disappearance was not processed after its gate expired.
        gate.request(now)
        assertNull(filter.accept(false, "", now))
        now += SECOND / 4
        val staleClearance = requireNotNull(filter.accept(false, "", now))
        assertNull(staleClearance.seconds)
        gate.measured(staleClearance.seconds, now)
        assertTrue(gate.isEnabled())

        now += SECOND / 4
        assertNull(filter.accept(true, "4", now))
        now += SECOND / 4
        val currentDial = requireNotNull(filter.accept(true, "4", now))
        gate.measured(currentDial.seconds, now)
        assertTrue(gate.isEnabled())
        now += SECOND / 4
        assertNull(filter.accept(false, "", now))
        now += SECOND / 4
        gate.measured(requireNotNull(filter.accept(false, "", now)).seconds, now)
        assertFalse(gate.isEnabled())
    }

    @Test fun `result and stop close current attention and reject late charged cues`() {
        for (stop in listOf(false, true)) {
            val gate = gate()
            gate.request(now)
            if (stop) gate.stop() else gate.observe(article(MatchEnded(BattleResult.WIN)))
            now += SECOND
            gate.observe(article(AttackIncoming))
            assertFalse(gate.isEnabled())
            gate.observe(article(ObservationRestartedByUser))
            gate.observe(article(GetReadyWitnessed, now - SECOND))
            assertFalse(gate.isEnabled())
            now += SECOND
            gate.observe(article(GetReadyWitnessed))
            assertTrue(gate.isEnabled())
        }
    }

    private fun article(payload: TestimonyPayload, at: Long = now) = RealityArticle(
        ArticleId(java.util.UUID.randomUUID().toString()), 0, 0, SourceId("TEST"), payload, monotonicTimeNanos = at)
    private companion object { const val SECOND = 1_000_000_000L }
}
