package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonGoTypeIconMatcherTest {
    @Test fun `capture strips shorter than normalization size remain valid`() {
        assertEquals(24, PokemonGoTypeIconMatcher.resolvedBadgeSide(width = 260, height = 30))
    }

    @Test fun `badge side never exceeds either bitmap dimension`() {
        assertEquals(18, PokemonGoTypeIconMatcher.resolvedBadgeSide(width = 18, height = 40))
        assertEquals(1, PokemonGoTypeIconMatcher.resolvedBadgeSide(width = 1, height = 1))
        assertNull(PokemonGoTypeIconMatcher.resolvedBadgeSide(width = 0, height = 30))
    }
}
