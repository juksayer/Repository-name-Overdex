package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeciesTextResolverTest {
    @Test fun `finds a species inside team status OCR`() {
        assertEquals("GOURGEIST", SpeciesTextResolver.resolve("CP 1481\\nGourgeist", setOf("GOURGEIST", "VAPOREON")))
    }

    @Test fun `prefers the longest matching name`() {
        assertEquals("MR. MIME", SpeciesTextResolver.resolve("MR. MIME", setOf("MIME", "MR. MIME")))
    }

    @Test fun `tolerates a small unambiguous OCR error in a badge`() {
        assertEquals("TURTONATOR", SpeciesTextResolver.resolve("CP 1499 TURT0NATOR", setOf("TURTONATOR", "GOURGEIST")))
    }

    @Test fun `does not turn unrelated text into a species`() {
        assertNull(SpeciesTextResolver.resolve("CP 1481", setOf("GOURGEIST")))
    }
}
