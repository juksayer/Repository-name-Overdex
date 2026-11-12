package com.example.overdex.battle.observation

import com.example.overdex.battle.audio.FastMoveListeningGate
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.MatchStarted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalFaintListeningGateTest {
    @Test
    fun `last delivered historical loss cannot end recording before later witnessed losses`() {
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, 1)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Turtonator"), 3)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Camerupt"), 5)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Gourgeist"), 12)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Carbink"), 8)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Quagsire"), 10)
        assertTrue(gate.allows(11))
        assertFalse(gate.allows(12))
    }

    @Test
    fun `two historical player faints resolved after Gourgeist enters cannot await its entry again`() {
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, 1)
        gate.observe(species(ActivePokemonSide.PLAYER, "Turtonator"), 2)
        gate.observe(species(ActivePokemonSide.PLAYER, "Camerupt"), 4)
        gate.observe(species(ActivePokemonSide.PLAYER, "Gourgeist"), 6)
        // Both earlier losses become named only after the replacement has independently
        // returned alive. Their source clocks remain the historical ball-count clocks.
        gate.observe(faint(ActivePokemonSide.PLAYER, "Turtonator"), 3)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Camerupt"), 5)
        assertTrue("A historical loss must not close current battle recording",
            gate.accepts(6, 7, gate.generation))

        gate.observe(species(ActivePokemonSide.OPPONENT, "Carbink"), 7)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Carbink"), 8)
        gate.observe(species(ActivePokemonSide.OPPONENT, "Quagsire"), 9)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Quagsire"), 10)
        assertTrue("Four distinct losses leave one Pokémon on each side", gate.allows(11))
        gate.observe(faint(ActivePokemonSide.PLAYER, "Gourgeist"), 12)
        assertFalse("The actual fifth named loss must still end recording", gate.allows(12))
    }

    @Test
    fun `late old identity cannot select an old actor for the current unnamed faint`() {
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, 1)
        gate.observe(species(ActivePokemonSide.PLAYER, "Turtonator"), 2)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Turtonator"), 3)
        gate.observe(species(ActivePokemonSide.PLAYER, "Camerupt"), 4)
        gate.observe(faint(ActivePokemonSide.PLAYER, "Camerupt"), 5)
        gate.observe(species(ActivePokemonSide.PLAYER, "Gourgeist"), 6)
        gate.observe(species(ActivePokemonSide.OPPONENT, "Carbink"), 7)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Carbink"), 8)
        gate.observe(species(ActivePokemonSide.OPPONENT, "Quagsire"), 9)
        gate.observe(faint(ActivePokemonSide.OPPONENT, "Quagsire"), 10)

        // A buffered crop arrives after the fourth loss but describes Camerupt before its KO.
        gate.observe(species(ActivePokemonSide.PLAYER, "Camerupt"), 4)
        assertTrue(gate.allows(11))
        gate.observe(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER), 12)
        assertFalse("Source-ordered active identity must still be Gourgeist", gate.allows(12))
    }

    private fun species(side: ActivePokemonSide, name: String) = ActivePokemonSpeciesWitnessed(side, name)
    private fun faint(side: ActivePokemonSide, name: String) = ActivePokemonFaintedWitnessed(side, name)
}
