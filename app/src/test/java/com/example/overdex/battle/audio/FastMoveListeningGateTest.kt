package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class FastMoveListeningGateTest {
    @Test fun `any countdown glyph opens audio without requiring GO a species or a visual hit`() {
        for (glyph in listOf("3", "2", "1", "GO")) {
            val gate = FastMoveListeningGate()
            assertFalse(gate.allows(100))
            gate.observe(CountdownGlyphWitnessed(glyph, .99f, 1), 100, "glyph")
            assertFalse(gate.allows(99))
            assertTrue(gate.allows(100))
            gate.observe(GetReadyWitnessed, 200)
            gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt"), 300)
            assertTrue(gate.allows(400))
            assertEquals(listOf("glyph"), gate.triggerIds())
        }
    }

    @Test fun `fifth distinct faint closes audio and duplicate faint evidence does not count twice`() {
        val gate = FastMoveListeningGate()
        gate.observe(CountdownGlyphWitnessed("2", .99f, 1), 100)
        val sides = listOf(ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER,
            ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT)
        sides.forEachIndexed { index, side ->
            val name = "Pokemon$index"
            val at = 200L + index * 100
            gate.observe(ActivePokemonSpeciesWitnessed(side, name), at - 1)
            gate.observe(ActivePokemonFaintedWitnessed(side, name), at)
            gate.observe(ActivePokemonFaintedWitnessed(side, name), at + 1)
            gate.observe(ActivePokemonFaintedWitnessed(side), at + 2)
            assertEquals(index < 4, gate.allows(at + 3))
        }
        assertTrue(gate.accepts(400, 500, gate.generation))
        assertFalse(gate.accepts(550, 650, gate.generation))
        gate.observe(CountdownGlyphWitnessed("GO", .99f, 2), 700)
        assertFalse(gate.allows(800))
    }

    @Test fun `result can finish before five faints and manual restart invalidates queued work`() {
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, 100)
        gate.observe(MatchEnded(BattleResult.WIN), 400)
        assertTrue(gate.accepts(100, 300, gate.generation))
        assertFalse(gate.allows(500))
        val oldGeneration = gate.generation
        gate.observe(ObservationRestartedByUser, 600)
        assertFalse(gate.accepts(100, 300, oldGeneration))
        gate.observe(CountdownGlyphWitnessed("1", .99f, 1), 700)
        assertTrue(gate.allows(800))
        gate.observe(ScreenIgnoredByUser, 900)
        assertFalse(gate.allows(1000))
        gate.observe(MatchStarted, 1100)
        assertFalse(gate.allows(1200))
    }
}
