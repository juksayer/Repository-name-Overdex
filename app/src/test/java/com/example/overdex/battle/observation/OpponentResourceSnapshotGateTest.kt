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

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0.20f)
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(shields))
        gate.captured(balls)
        gate.captured(balls)
        assertTrue(gate.isEnabled(balls))
        gate.captured(playerBalls)
        gate.captured(playerBalls)
        now = 3_000_000_000L
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(playerBalls))

        gate.observeDamageTick(ActivePokemonSide.OPPONENT, 0.14f)
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(playerBalls))
    }

    @Test fun `accepted species opens a bounded window and repeated identity does not keep it open`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        var now = 0L
        val gate = TeamResourceSnapshotGate(setOf(balls, shields), setOf(balls), now = { now })

        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Sneasel")
        repeat(2) { gate.captured(balls); gate.captured(shields) }
        assertTrue(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
        now = 2_500_000_000L
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "sneasel")
        now = 3_000_000_000L
        assertFalse(gate.isEnabled(balls))

        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Sealeo")
        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
        gate.stop()
        assertFalse(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
    }
}
