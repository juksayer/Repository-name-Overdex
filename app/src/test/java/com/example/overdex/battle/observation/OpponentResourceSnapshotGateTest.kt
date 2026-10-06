package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentResourceSnapshotGateTest {
    @Test fun `one cue requests exactly two samples from each resource crop`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val gate = OpponentResourceSnapshotGate(setOf(balls, shields), balls)

        gate.requestSnapshot()

        assertTrue(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
        gate.captured(balls)
        assertTrue(gate.isEnabled(balls))
        gate.captured(balls)
        assertFalse(gate.isEnabled(balls))
        assertTrue(gate.isEnabled(shields))
    }

    @Test fun `battle cry request samples only opponent poke balls`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val gate = OpponentResourceSnapshotGate(setOf(balls, shields), balls)

        gate.requestPokeBallSnapshot()

        assertTrue(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
    }

    @Test fun `opponent entering red hp starts and further hits refresh faint watch`() {
        val balls = "OpponentPokeBallsCrop"
        val shields = "OpponentShieldsCrop"
        val gate = OpponentResourceSnapshotGate(setOf(balls, shields), balls)

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0.21f)
        assertFalse(gate.isEnabled(balls))

        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0.20f)
        assertTrue(gate.isEnabled(balls))
        assertFalse(gate.isEnabled(shields))
        gate.captured(balls)
        gate.captured(balls)
        assertFalse(gate.isEnabled(balls))

        gate.observeDamageTick(ActivePokemonSide.OPPONENT, 0.14f)
        assertTrue(gate.isEnabled(balls))
    }
}
