package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesOcrTypographyTest {
    @Test fun `known glyph confusion costs less than arbitrary substitution`() {
        assertTrue(
            SpeciesOcrTypography.weightedDistance("5NEASEL", "SNEASEL") <
                SpeciesOcrTypography.weightedDistance("XNEASEL", "SNEASEL")
        )
    }

    @Test fun `impossible symbols stay in the reading until a unique legal name explains them`() {
        for (reading in listOf("\$neasel", "P!kachu", "Pik@chu", "P|kachu")) {
            assertEquals(if (reading.contains("neasel")) "Sneasel" else "Pikachu",
                SpeciesTextResolver.resolve(reading, setOf("Sneasel", "Pikachu", "Pichu")))
        }
        assertEquals("SNE#ASEL", SpeciesOcrTypography.normalizeLabel("Sne#asel!"))
        assertEquals(null, SpeciesTextResolver.resolve("Ear", setOf("Sneasel", "Camerupt")))
        assertEquals(null, SpeciesTextResolver.resolve("Pik###chu", setOf("Pikachu")))
    }

    @Test fun `canonical Pokemon Go case is stronger evidence than all caps`() {
        assertEquals(1f, SpeciesOcrTypography.caseEvidence("Sneasel", "Sneasel"))
        assertTrue(SpeciesOcrTypography.caseEvidence("SNEASEL", "Sneasel") < 1f)
    }
}
