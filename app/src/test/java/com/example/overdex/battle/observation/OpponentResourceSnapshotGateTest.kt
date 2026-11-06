package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamResourceSnapshotGateTest {
    @Test fun `one cue requests exactly two samples from each resource crop`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls))

        gate.requestSnapshot()

        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
        gate.captured(balls)
        assertTrue(gate.isEnabled(balls))
        gate.captured(balls)
        assertFalse(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
    }

    @Test fun `battle cry request samples both poke ball badges and not shields`() {
        val playerBalls = "PlayerPokeBallsCrop"
        val opponentBalls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val gate = TeamResourceSnapshotGate(
            setOf(playerBalls, opponentBalls, shields),
            setOf(playerBalls, opponentBalls)
        )

        gate.requestPokeBallSnapshot()

        assertTrue(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(shields))
    }

    @Test fun `opponent entering red hp starts and further hits refresh faint watch`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val playerBalls = "PlayerPokeBallsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(
            setOf(playerBalls, balls, shields),
            setOf(playerBalls, balls),
            now = { now },
        )

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0.21f)
        assertFalse(gate.isEnabled(balls))

        now = 1_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0.20f)
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(shields))
        gate.captured(balls)
        gate.captured(balls)
        assertTrue(gate.isEnabled(balls))
        gate.captured(playerBalls)
        gate.captured(playerBalls)
        now += 3_000_000_000L
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(playerBalls))

        gate.observeDamageTick(ActivePokemonSide.OPPONENT, 0.14f)
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(playerBalls))
    }

    @Test fun `fresh eight percent hp keeps ball observation open throughout a long idle pause`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls), now = { now })

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
        repeat(2) { gate.captured(balls) }
        for (second in 1L..20L) {
            now = second * 1_000_000_000L
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
            assertTrue(gate.isEnabled(balls))
            assertFalse(gate.isEnabled(shields))
        }
        // Fresh low HP, rather than a new hit, kept the watch available.
        // Once those frames end it still has only a bounded three-second tail.
        now = 22_999_999_999L
        assertTrue(gate.isEnabled(balls))
        now = 23_000_000_000L
        assertFalse(gate.isEnabled(balls))
    }

    @Test fun `healthy samples from the other side cannot renew the low hp watch`() {
        val playerBalls = "PlayerPokeBallsCrop"
        val opponentBalls = "OpponentPokeBallsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(playerBalls, opponentBalls),
            setOf(playerBalls, opponentBalls), now = { now })

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
        repeat(2) { gate.captured(playerBalls); gate.captured(opponentBalls) }
        for (second in 1L..6L) {
            now = second * 1_000_000_000L
            gate.observeActiveHp(ActivePokemonSide.PLAYER, 1f)
            if (second >= 3) {
                assertFalse(gate.isEnabled(playerBalls))
                assertFalse(gate.isEnabled(opponentBalls))
            }
        }
        // The other side's newer frame must not reject this still-fresh
        // source-time measurement of the threatened combatant.
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 5_500_000_000L)
        assertTrue(gate.isEnabled(opponentBalls))
        now = 8_500_000_000L
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `repeated delayed stale and future hp cannot move the captured frame deadline`() {
        val balls = "OpponentPokeBallsCrop"
        var now = 1_000_000_000L
        val gate = TeamResourceSnapshotGate(setOf(balls), setOf(balls), now = { now })
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 500_000_000L)
        repeat(2) { gate.captured(balls) }

        now = 3_250_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 500_000_000L)
        assertTrue(gate.isEnabled(balls))
        now = 3_500_000_000L
        assertFalse(gate.isEnabled(balls))
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 400_000_000L)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 4_000_000_000L)
        assertFalse(gate.isEnabled(balls))
    }

    @Test fun `hp rearm hysteresis does not extend a critical watch from a higher plateau`() {
        val balls = "OpponentPokeBallsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(balls), setOf(balls), now = { now })
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .20f)
        repeat(2) { gate.captured(balls) }
        now = 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .23f)
        now = 3_000_000_000L
        assertFalse(gate.isEnabled(balls))
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .24f)
        now = 4_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .20f)
        // Re-entering critical HP requests two fresh snapshots after rearming.
        assertTrue(gate.isEnabled(balls))
        now = 7_000_000_000L
        assertTrue(gate.isEnabled(balls))
        repeat(2) { gate.captured(balls) }
        assertFalse(gate.isEnabled(balls))
    }

    @Test fun `replacement rejects departed low hp frames and rearms its own watch`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls), now = { now })
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Former")
        now = 1_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
        now = 2_000_000_000L
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Replacement")
        repeat(2) { gate.captured(balls); gate.captured(shields) }
        now = 4_000_000_000L
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, observedAt = 1_500_000_000L)
        now = 5_000_000_000L
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
        now = 8_000_000_000L
        assertTrue(gate.isEnabled(balls)) // The replacement independently entered critical HP.
        repeat(2) { gate.captured(balls) }
        assertFalse(gate.isEnabled(balls))
    }

    @Test fun `result and stop keep late low hp and other cues from reopening observation`() {
        for (stop in listOf(false, true)) {
            val balls = "OpponentPokeBallsCrop"
            val shields = "OpponentShieldsCrop"
            var now = 0L
            val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls), now = { now })
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
            if (stop) gate.stop() else gate.observeMatchEnded()
            now = 1_000_000_000L
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f)
            gate.observeDamageTick(ActivePokemonSide.OPPONENT, .04f)
            gate.observeSpecies(ActivePokemonSide.OPPONENT, "Late identity")
            gate.requestResourceWatch()
            gate.requestFaintWatch()
            assertFalse(gate.isEnabled(balls))
            assertFalse(gate.isEnabled(shields))
        }
    }

    @Test fun `accepted species opens a bounded window and repeated identity does not keep it open`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls), now = { now })

        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Sneasel")
        repeat(2) { gate.captured(balls); gate.captured(shields) }
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
        now = 2_500_000_000L
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "sneasel")
        now = 3_000_000_000L
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))

        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Sealeo")
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
        gate.stop()
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
    }
}
