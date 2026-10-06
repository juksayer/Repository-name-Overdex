package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastOverlaySpeciesResolverTest {
    private val names = setOf("Sneasel", "Sealeo", "Vaporeon", "Raichu")

    @Test fun `accepts a direct badge name`() {
        assertEquals("Sneasel", FastOverlaySpeciesResolver.resolve("Sneasel", names))
        assertEquals(
            FastOverlaySpeciesResolution("Sneasel", 0.98f, "EXACT_CATALOGUE_TEXT_EXPECTED_CASE"),
            FastOverlaySpeciesResolver.resolveDetailed("Sneasel", names)
        )
    }

    @Test fun `accepts overmon style partial badge text`() {
        assertEquals("Sneasel", FastOverlaySpeciesResolver.resolve("Sneaz", names))
        assertEquals(
            FastOverlaySpeciesResolution("Sneasel", 0.68f, "UNIQUE_OVERMON_PREFIX_MATCH"),
            FastOverlaySpeciesResolver.resolveDetailed("Sneaz", names)
        )
    }

    @Test fun `does not turn cp text into a species`() {
        assertNull(FastOverlaySpeciesResolver.resolve("CP 1499", names))
    }

    @Test fun `does not emit Mew from a truncated Mewtwo badge`() {
        assertNull(FastOverlaySpeciesResolver.resolve("MewtO", names + setOf("Mew", "Mewtwo")))
    }
}
