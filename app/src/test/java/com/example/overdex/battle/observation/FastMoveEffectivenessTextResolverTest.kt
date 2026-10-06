package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastMoveEffectivenessTextResolverTest {
    @Test
    fun `reads full super effective phrase`() {
        assertEquals(
            FastMoveEffectiveness.SUPER_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve("SUPER EFFECTIVE!")?.effectiveness
        )
    }

    @Test
    fun `reads fading super effective suffix without confusing not very effective`() {
        assertEquals(
            FastMoveEffectiveness.SUPER_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve("ER EFFECTIVE!")?.effectiveness
        )
        assertEquals(
            FastMoveEffectiveness.NOT_VERY_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve("NOT VERY EFFECTIVE...")?.effectiveness
        )
        assertNull(FastMoveEffectivenessTextResolver.resolve("VERY EFFECTIVE"))
    }

    @Test
    fun `reads a clipped super effective prefix without accepting a very effective fragment`() {
        assertEquals(
            FastMoveEffectiveness.SUPER_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve("PER EFFECTIVE!")?.effectiveness
        )
        assertNull(FastMoveEffectivenessTextResolver.resolve("VERY EFFECTIVE"))
    }

    @Test
    fun `HP effectiveness source identifies the damaged side`() {
        assertEquals(
            ActivePokemonSide.PLAYER,
            FastMoveEffectivenessSources.damagedSide(FastMoveEffectivenessSources.PLAYER_TEXT)
        )
        assertEquals(
            ActivePokemonSide.OPPONENT,
            FastMoveEffectivenessSources.damagedSide(FastMoveEffectivenessSources.OPPONENT_TEXT)
        )
        assertNull(FastMoveEffectivenessSources.damagedSide("ANNOUNCEMENT_WITNESS"))
    }
}
