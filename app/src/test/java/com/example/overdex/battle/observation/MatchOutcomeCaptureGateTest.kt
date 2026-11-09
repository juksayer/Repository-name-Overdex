package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOutcomeCaptureGateTest {
    @Test fun `finishing charged camera HP preserves urgent attention and the bounded late result tail`() {
        for (side in ActivePokemonSide.entries) for (chargedHp in listOf(0f, .1f)) {
            var now = 100_000_000_000L
            val battlefieldAt = now
            val gate = MatchOutcomeCaptureGate { now }
            gate.observeMatchStarted(now)
            gate.observePlayerBallCount(now, if (side == ActivePokemonSide.PLAYER) 1 else 3)
            gate.observeOpponentBallCount(now, if (side == ActivePokemonSide.OPPONENT) 1 else 3)
            gate.observeActiveHp(side, now, .83f)
            now += 7_000_000_000L
            gate.observeChargedMove(now)
            val chargedAt = now
            now += 1_000_000_000L
            gate.observeActiveHp(side, now, chargedHp, chargedCamera = true)
            assertTrue("Critical charge-camera HP still drives urgent result attention", gate.isEnabled())

            now = battlefieldAt + 44_000_000_000L
            assertFalse("Ordinary urgent attention remains bounded", gate.isEnabled())
            assertEquals("A charge-camera measurement is not a normal battlefield return",
                500_000_000L, gate.probeIntervalNanos())
            assertTrue(gate.acceptsProbeFrame(now))
            now += 500_000_000L
            assertTrue(gate.acceptsProbeFrame(now))
            now = chargedAt + 45_000_000_001L
            assertEquals("Camera HP must not extend the charge-source deadline",
                2_000_000_000L, gate.probeIntervalNanos())
        }
    }

    @Test fun `normal battlefield return cancels late tail even when later camera HP is observed`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .83f)
        now += 7_000_000_000L
        gate.observeChargedMove(now)
        now += 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .1f, chargedCamera = true)
        now += 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .5f)
        now += 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .1f, chargedCamera = true)
        now += 36_000_000_000L
        assertFalse(gate.isEnabled())
        assertEquals("A later camera frame cannot undo a real battlefield return",
            2_000_000_000L, gate.probeIntervalNanos())
    }

    @Test fun `stopped gate cannot reuse prior battlefield context for camera-only HP`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .83f)
        gate.stop()
        now += 1_000_000_000L
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 1)
        gate.observeChargedMove(now)
        now += 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, 0f, chargedCamera = true)
        now += 36_000_000_000L
        assertFalse(gate.isEnabled())
        assertEquals("No normal bar in this Match authorized a late disappearance tail",
            2_000_000_000L, gate.probeIntervalNanos())
    }

    @Test fun `late final charged result gets a bounded half second tail after ordinary fast window expires`() {
        var now = 100_000_000_000L
        val hpAt = now
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .11111111f)
        now += 7_000_000_000L
        gate.observeChargedMove(now)
        now = hpAt + 36_000_000_000L
        assertFalse("The four-Hz window still expires after 35 seconds", gate.isEnabled())
        assertTrue(gate.isProbeEnabled())
        assertEquals(500_000_000L, gate.probeIntervalNanos())
        assertTrue(gate.acceptsProbeFrame(now))
        now += 499_999_999L
        assertFalse(gate.acceptsProbeFrame(now))
        now++
        assertTrue(gate.acceptsProbeFrame(now))
        now = hpAt + 45_000_000_000L
        assertTrue("The Rocket YOU WIN roughly 45s after last HP is still acquired densely", gate.acceptsProbeFrame(now))
        now = hpAt + 52_000_000_000L
        assertEquals(500_000_000L, gate.probeIntervalNanos())
        now++
        assertEquals("The extra attention ends 45s after its charged-move source", 2_000_000_000L, gate.probeIntervalNanos())
        assertFalse(gate.isEnabled())
    }

    @Test fun `sparse probe remains two seconds apart without final charged disappearance`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 3)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .1f)
        gate.observeChargedMove(now)
        now += 40_000_000_000L
        assertEquals(2_000_000_000L, gate.probeIntervalNanos())
        assertTrue(gate.acceptsProbeFrame(now))
        now += 500_000_000L
        assertFalse(gate.acceptsProbeFrame(now))
        now += 1_500_000_000L
        assertTrue(gate.acceptsProbeFrame(now))
    }

    @Test fun `late attention needs charged evidence after last HP and closes on reappearance`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 1)
        gate.observeChargedMove(now - 1) // Before the current Match, so unavailable.
        gate.observeChargedMove(now + 1) // Future evidence cannot create attention.
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .75f)
        now += 36_000_000_000L
        assertEquals(2_000_000_000L, gate.probeIntervalNanos())
        gate.observeChargedMove(now)
        assertEquals(500_000_000L, gate.probeIntervalNanos())
        now++
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .5f)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 100_000_000_000L, .2f)
        now += 36_000_000_000L
        assertEquals("An old charge cannot rearm after a fresh battlefield HP measurement", 2_000_000_000L, gate.probeIntervalNanos())
    }

    @Test fun `probe capture rejects future duplicate and reordered frames and resets on stop`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        assertFalse(gate.acceptsProbeFrame(now - 1))
        assertFalse(gate.acceptsProbeFrame(now + 1))
        assertTrue(gate.acceptsProbeFrame(now))
        assertFalse(gate.acceptsProbeFrame(now))
        assertFalse(gate.acceptsProbeFrame(now - 1))
        gate.observeMatchEnded()
        now += 3_000_000_000L
        assertFalse(gate.acceptsProbeFrame(now))
        gate.stop()
        gate.observeMatchStarted(now)
        assertTrue(gate.acceptsProbeFrame(now))
        assertEquals(2_000_000_000L, gate.probeIntervalNanos())
    }

    @Test fun `zero remaining team still permits late charged result acquisition but no inferred winner`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        gate.observePlayerBallCount(now, 2)
        gate.observeOpponentBallCount(now, 1)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .2f)
        now += 1_000_000_000L
        gate.observeChargedMove(now)
        gate.observeOpponentBallCount(now, 0)
        now += 36_000_000_000L
        assertFalse(gate.isEnabled())
        assertTrue(gate.isProbeEnabled())
        assertEquals(500_000_000L, gate.probeIntervalNanos())
        now += 25_000_000_000L
        assertEquals(2_000_000_000L, gate.probeIntervalNanos())
    }

    @Test fun `sparse result probe stays available with healthy HP and reserves through an early concession`() {
        var now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        assertFalse(gate.isProbeEnabled())
        gate.observePlayerBallCount(now, 3)
        gate.observeOpponentBallCount(now, 3)
        assertFalse("Team Select counts cannot enable a battle result probe", gate.isProbeEnabled())
        gate.observeMatchStarted(now)
        gate.observeActiveHp(ActivePokemonSide.PLAYER, now, .9f)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .8f)
        assertFalse(gate.isEnabled())
        assertTrue(gate.isProbeEnabled())
        now += 65_000_000_000L
        assertFalse("Reserves still do not request expensive urgent result capture", gate.isEnabled())
        assertTrue("The previously reported minute-long unrecognized result must remain eligible", gate.isProbeEnabled())
    }

    @Test fun `sparse result probe needs neither ball counts nor red HP after GO`() {
        val now = 1_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        assertTrue(gate.isProbeEnabled())
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, now, .75f)
        assertFalse(gate.isEnabled())
        assertTrue(gate.isProbeEnabled())
    }

    @Test fun `measured battle HP enables sparse result recovery when GO and badge counts were missed`() {
        val now = 1_000_000_000L
        for (side in ActivePokemonSide.entries) {
            val gate = MatchOutcomeCaptureGate { now }
            assertFalse(gate.isProbeEnabled())
            gate.observeActiveHp(side, now, .75f)
            assertFalse(gate.isEnabled())
            assertTrue(gate.isProbeEnabled())
        }
    }

    @Test fun `urgent result window suppresses duplicate sparse probe and expiry restores it`() {
        var now = 100_000_000_000L
        val gate = MatchOutcomeCaptureGate { now }
        gate.observeMatchStarted(now)
        assertTrue(gate.isProbeEnabled())
        gate.observeResultMusic(now)
        assertTrue(gate.isEnabled())
        assertFalse(gate.isProbeEnabled())
        now += 10_000_000_001L
        assertFalse(gate.isEnabled())
        assertTrue(gate.isProbeEnabled())
        gate.observeMatchEnded()
        assertFalse(gate.isEnabled())
        assertFalse(gate.isProbeEnabled())
        gate.observeResultMusic(now)
        gate.observeActiveHp(ActivePokemonSide.PLAYER, now, .9f)
        assertFalse("Historical evidence cannot reopen an accepted result", gate.isProbeEnabled())
        gate.stop()
        assertFalse("Stopped observation clears the battle context", gate.isProbeEnabled())
    }

    @Test fun `sparse probe has its own capture source and shares the calibrated result crop`() {
        assertEquals(2_000_000_000L, MatchOutcomeCaptureGate.PROBE_CAPTURE_INTERVAL_NANOS)
        assertEquals("MATCH_OUTCOME_PROBE_CAPTURE", BattleWitnessContracts.matchOutcomeProbeCapture.witnessId)
        assertEquals(BattleWitnessContracts.matchOutcomeCropCapture.crop, BattleWitnessContracts.matchOutcomeProbeCapture.crop)
        assertFalse(BattleWitnessContracts.matchOutcomeCropCapture.witnessId == BattleWitnessContracts.matchOutcomeProbeCapture.witnessId)
    }

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
