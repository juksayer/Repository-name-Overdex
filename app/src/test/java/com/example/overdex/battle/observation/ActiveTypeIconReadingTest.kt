package com.example.overdex.battle.observation

import com.example.overdex.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveTypeIconReadingTest {
    private fun result(type: PokemonType?, similarity: Float) =
        PokemonGoTypeIconMatcher.Result(type, type ?: PokemonType.ELECTRIC, similarity, .8f)

    @Test fun `one recognized badge is a complete single type reading`() {
        assertEquals(ActiveTypeIconReading(listOf(PokemonType.FIRE), .9f),
            ActiveTypeIconReading.from(listOf(result(PokemonType.FIRE, .9f))))
    }

    @Test fun `recognized pair retains order and confidence in its weaker accepted badge`() {
        assertEquals(ActiveTypeIconReading(listOf(PokemonType.POISON, PokemonType.DARK), .8f),
            ActiveTypeIconReading.from(listOf(result(PokemonType.POISON, .9f), result(PokemonType.DARK, .8f))))
    }

    @Test fun `unrecognized member of a visible pair never becomes a single type conclusion`() {
        for (pair in listOf(
            listOf(result(null, .31f), result(PokemonType.WATER, .54f)),
            listOf(result(PokemonType.WATER, .54f), result(null, .31f)),
            listOf(result(null, .31f), result(null, .32f)),
        )) assertNull(ActiveTypeIconReading.from(pair))
    }

    @Test fun `empty excess duplicate and invalid confidence readings cannot become testimony`() {
        assertNull(ActiveTypeIconReading.from(emptyList()))
        assertNull(ActiveTypeIconReading.from(listOf(result(PokemonType.FIRE, .9f),
            result(PokemonType.WATER, .9f), result(PokemonType.GRASS, .9f))))
        assertNull(ActiveTypeIconReading.from(listOf(result(PokemonType.FIRE, .9f), result(PokemonType.FIRE, .8f))))
        for (score in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            assertNull(ActiveTypeIconReading.from(listOf(result(PokemonType.FIRE, score))))
        }
    }
}
