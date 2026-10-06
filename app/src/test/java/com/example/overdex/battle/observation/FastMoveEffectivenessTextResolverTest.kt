package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.FastMoveEffectiveness
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
}
