package com.example.overdex.battle.observation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOutcomeCaptureGateTest {
    @Test fun `red hp only opens result probing during the final duel`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }

        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 3)
        gate.observeActiveHp(now, 0.20f)
        assertFalse(gate.isEnabled())

        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(now, 0.20f)
        assertTrue(gate.isEnabled())

        now += 7_900_000_000L
        assertTrue(gate.isEnabled())
        now += 200_000_000L
        // If this was an ordinary faint, the replacement's healthy bar closes
        // the red-health probe once its bounded window has elapsed.
        gate.observeActiveHp(now, 0.95f)
        assertFalse(gate.isEnabled())
    }

    @Test fun `healthy final duel absence stays dormant and accepted end closes red probe`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(now, 0.75f)

        now += 1_000_000_000L
        assertFalse(gate.isEnabled())

        gate.observeActiveHp(now, 0.19f)
        assertTrue(gate.isEnabled())

        gate.observeMatchEnded()
        assertFalse(gate.isEnabled())
    }

    @Test fun `one total poke ball opens final result window`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        assertFalse(gate.isEnabled())

        gate.observeOpponentBallCount(now, 0)
        assertTrue(gate.isEnabled())
    }

    @Test fun `one visible ball without the other badge count is not enough`() {
        val gate = MatchOutcomeCaptureGate { 1_000_000_000L }
        gate.observeMatchStarted(1_000_000_000L)

        gate.observePlayerBallCount(1_000_000_000L, 1)

        assertFalse(gate.isEnabled())
    }
}
