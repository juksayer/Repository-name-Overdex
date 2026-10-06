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

    @Test fun `canonical Pokemon Go case is stronger evidence than all caps`() {
        assertEquals(1f, SpeciesOcrTypography.caseEvidence("Sneasel", "Sneasel"))
        assertTrue(SpeciesOcrTypography.caseEvidence("SNEASEL", "Sneasel") < 1f)
    }
}
