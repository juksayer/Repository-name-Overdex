package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOutcomeCaptureGateTest {
    @Test fun `result music opens a bounded OCR probe without requiring missing badge counts`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeResultMusic(now)
        assertFalse("Menu music before a battle cannot arm result OCR", gate.isEnabled())
        gate.observeMatchStarted(1_000_000_000)
        gate.observeResultMusic(now)
        assertTrue(gate.isEnabled())
        now += 10_000_000_001
        assertFalse(gate.isEnabled())
        gate.observeMatchEnded()
        gate.observeResultMusic(now)
        assertFalse("A result is never reopened by its soundtrack", gate.isEnabled())
    }

    @Test fun `stale and future music timestamps do not open result OCR`() {
        val gate = MatchOutcomeCaptureGate { 100_000_000_000 }
        gate.observeMatchStarted(20_000_000_000)
        gate.observeResultMusic(10_000_000_000)
        assertFalse(gate.isEnabled())
        gate.observeResultMusic(50_000_000_000)
        assertFalse(gate.isEnabled())
        gate.observeResultMusic(110_000_000_000)
        assertFalse(gate.isEnabled())
    }

    @Test fun `red hp stays dormant for teams with reserves`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }

        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 3)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.20f)
        assertFalse(gate.isEnabled())

        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.20f)
        assertTrue(gate.isEnabled())

        now += 7_900_000_000L
        assertTrue(gate.isEnabled())
        now += 200_000_000L
        // If this was an ordinary faint, the replacement's healthy bar closes
        // the red-health probe once its bounded window has elapsed.
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.95f)
        assertFalse(gate.isEnabled())
    }

    @Test fun `healthy final duel absence stays dormant and accepted end closes red probe`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.75f)

        now += 1_000_000_000L
        assertFalse(gate.isEnabled())

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.19f)
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

    @Test fun `last opponent in red opens a result check while player still has a bench`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observePlayerBallCount(now, 2)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.PLAYER, now, 0.1f)
        assertFalse("The player's red bar is not the last opponent's bar", gate.isEnabled())
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.18f)
        assertTrue(gate.isEnabled())
        now += 9_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.PLAYER, now, 0.7f)
        assertTrue("The surviving player's bar cannot hide the last opponent's disappearance", gate.isEnabled())
        now += 27_000_000_000L
        assertFalse("A missing bar must not leave result OCR on indefinitely", gate.isEnabled())
    }

    @Test fun `last player in red also opens a result check against a full opponent team`() {
        val now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 3)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0.1f)
        assertFalse(gate.isEnabled())
        gate.observeActiveHp(ActivePokemonSide.PLAYER, now, 0.1f)
        assertTrue(gate.isEnabled())
        gate.observeMatchEnded()
        assertFalse(gate.isEnabled())
    }

    @Test fun `last member disappears above red before result and final ball fade are observed`() {
        var now = 178_110_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observePlayerBallCount(now, 1)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .28f)
        assertFalse(gate.isEnabled())
        now = 201_000_000_000L
        assertTrue("Recorded YOU WIN must be eligible for OCR even without red HP", gate.isEnabled())
        now = 214_000_000_000L
        assertFalse("Recovery is bounded", gate.isEnabled())
    }

    @Test fun `healthy reappearance closes missing final member recovery`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observePlayerBallCount(now, 2)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .5f)
        now += 3_000_000_000L
        assertTrue(gate.isEnabled())
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .5f)
        assertFalse(gate.isEnabled())
        gate.observeOpponentBallCount(now, 2)
        now += 3_000_000_000L
        assertFalse("Ordinary missing bars with reserves do not open result OCR", gate.isEnabled())
    }

    @Test fun `empty team opens a bounded result check regardless of surviving bench size`() {
        for (survivors in 1..3) for (emptySide in ActivePokemonSide.entries) {
            var now = 1_000_000_000L
            val gate = MatchOutcomeCaptureGate { now }
            gate.observePlayerBallCount(now, if (emptySide == ActivePokemonSide.PLAYER) 0 else survivors)
            gate.observeOpponentBallCount(now, if (emptySide == ActivePokemonSide.OPPONENT) 0 else survivors)
            assertTrue(gate.isEnabled())
            now += 10_000_000_001L
            assertFalse(gate.isEnabled())
        }
    }
}
