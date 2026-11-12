package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class OpponentHandSamplingTest {
    private var now = 100_000_000_000L
    private val gate = QteTypeIconSampling(
        savedRegion = { BattleCalibration().opponentTrainerHandRegion }, now = { now },
        followsQte = false, sampleLimit = 100, settleNanos = 150_000_000L,
    )

    @Test fun `charged cues cannot start a hand experiment or cut off an explicitly requested sample`() {
        gate.beginSession()
        gate.observe(GetReadyWitnessed, now)
        assertFalse(gate.isEnabled())
        val draft = BattleCalibration().opponentTrainerHandRegion.copy(x = .75f)
        assertTrue("A manual sample is available before GO", gate.sample(draft))
        gate.observe(ChargeMoveUsedAnnounced, now)
        assertTrue(gate.isEnabled())
        assertEquals(draft, gate.region)
        gate.observe(MatchEnded(BattleResult.WIN), now)
        assertFalse(gate.isEnabled())
        assertFalse(gate.sample(draft))
    }

    @Test fun `source frames wait for guide hiding but do not extend the ten second deadline`() {
        gate.beginSession()
        val tappedAt = now
        assertTrue(gate.sample(BattleCalibration().opponentTrainerHandRegion))
        assertFalse(gate.acceptsFrame(tappedAt - 1))
        assertFalse(gate.acceptsFrame(tappedAt + 149_999_999L))
        now += 150_000_000L
        assertTrue(gate.acceptsFrame(now))
        now = tappedAt + QteTypeIconSampling.WINDOW_NANOS
        assertFalse(gate.isEnabled())
        assertFalse(gate.acceptsFrame(now))
    }

    @Test fun `stop or ignore retires manual sampling and restart needs a fresh tap and frame`() {
        gate.beginSession()
        val draft = BattleCalibration().opponentTrainerHandRegion
        gate.sample(draft)
        gate.observe(ScreenIgnoredByUser, now)
        assertFalse(gate.sample(draft))
        now += 1_000_000_000L
        gate.observe(ObservationRestartedByUser, now)
        gate.observe(GetReadyWitnessed, now)
        assertFalse(gate.isEnabled())
        assertTrue(gate.sample(draft))
        assertFalse(gate.acceptsFrame(now - 1))
        gate.observe(ObservationSessionStoppedByUser, now)
        assertFalse(gate.isEnabled())
    }

    @Test fun `one requested hand sample cannot preserve more than one hundred images`() {
        gate.beginSession()
        gate.sample(BattleCalibration().opponentTrainerHandRegion)
        now += 150_000_000L
        repeat(100) { gate.captured(now) }
        assertEquals(100, gate.status.value.saved)
        assertFalse(gate.isEnabled())
    }
}
