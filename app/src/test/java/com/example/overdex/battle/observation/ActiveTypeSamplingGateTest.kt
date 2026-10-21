package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide.*
import org.junit.Assert.*
import org.junit.Test

class ActiveTypeSamplingGateTest {
    @Test fun `steady badges retain a recovery probe but avoid five captures per second`() {
        val gate = ActiveTypeSamplingGate()
        val captures = (0..300).map { it * 200_000_000L }.filter { at ->
            gate.accepts(PLAYER, at).also { if (it) gate.captured(PLAYER, at) }
        }
        assertTrue(captures.size in 39..42)
        assertTrue(captures.zipWithNext().all { (a,b) -> b - a <= 1_600_000_000L })
    }
    @Test fun `species changes reopen only that side and repeated same-name testimony does not`() {
        val gate = ActiveTypeSamplingGate()
        gate.species(PLAYER, "Pikachu")
        for (side in listOf(PLAYER, OPPONENT)) for (at in listOf(0L, 200_000_000, 400_000_000)) gate.captured(side, at)
        gate.species(PLAYER, "pikachu")
        assertFalse(gate.accepts(PLAYER, 600_000_000))
        gate.species(OPPONENT, "Sealeo")
        assertTrue(gate.accepts(OPPONENT, 600_000_000))
        assertFalse(gate.accepts(PLAYER, 600_000_000))
        gate.trigger()
        assertTrue(gate.accepts(PLAYER, 600_000_000))
    }
}
