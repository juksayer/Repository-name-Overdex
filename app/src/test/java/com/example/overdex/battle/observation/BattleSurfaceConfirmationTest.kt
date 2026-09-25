package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.model.PokemonType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSurfaceConfirmationTest {
    @Test
    fun `accepted countdown evidence establishes a battle surface without GO`() {
        val confirmation = BattleSurfaceConfirmation()

        assertTrue(confirmation.observe(CountdownGlyphWitnessed("2", 0.9f, 1)))
    }

    @Test
    fun `species and type evidence corroborate a battle surface when countdown was missed`() {
        val confirmation = BattleSurfaceConfirmation()

        assertFalse(confirmation.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        assertTrue(
            confirmation.observe(
                ActivePokemonTypesWitnessed(
                    side = ActivePokemonSide.OPPONENT,
                    types = listOf(PokemonType.DARK),
                    similarity = 0.9f
                )
            )
        )
    }

    @Test
    fun `reset removes prior battle surface confirmation`() {
        val confirmation = BattleSurfaceConfirmation()
        confirmation.observe(CountdownGlyphWitnessed("1", 0.9f, 2))

        confirmation.reset()

        assertFalse(confirmation.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
    }
}
