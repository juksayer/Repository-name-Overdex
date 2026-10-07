package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun `species crop recovery checks the calibrated row then adjacent rows`() {
        val calibrated = AnchorRegion(x = 0.7f, y = 0.1f, width = 0.25f, height = 0.025f)

        assertEquals(0.1f, SpeciesCropSearchPlan.region(calibrated, 0).y)
        assertEquals(0.075f, SpeciesCropSearchPlan.region(calibrated, 1).y)
        assertEquals(0.125f, SpeciesCropSearchPlan.region(calibrated, 2).y)
        assertEquals(0.05f, SpeciesCropSearchPlan.region(calibrated, 3).y)
        assertEquals(0.15f, SpeciesCropSearchPlan.region(calibrated, 4).y)
        assertEquals(0, SpeciesCropSearchPlan.next(4))
    }

    @Test fun `species crop recovery never leaves the captured frame`() {
        val top = SpeciesCropSearchPlan.region(
            AnchorRegion(x = 0f, y = 0.01f, width = 0.2f, height = 0.05f),
            3,
        )
        val bottom = SpeciesCropSearchPlan.region(
            AnchorRegion(x = 0f, y = 0.97f, width = 0.2f, height = 0.05f),
            4,
        )

        assertEquals(0f, top.y)
        assertTrue(bottom.y + bottom.height <= 1f)
    }
}
