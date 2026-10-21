package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class PointerStripOcclusionTest {
    private val coordinates = AnchorRegion(0f, 82f / 2400, 3f / 7, 44f / 2400)

    @Test fun `both recorded type crops are blocked despite coordinates OCR using only left three cells`() {
        for (x in listOf(12f / 672, 589f / 672)) {
            val crop = AnchorRegion(x, 48f / 1493, 68f / 672, 34f / 1493)
            assertTrue(PointerStripOcclusion.covers(crop, coordinates, true))
            assertFalse(PointerStripOcclusion.covers(crop, coordinates, false))
        }
    }

    @Test fun `a crop below the strip remains available without moving calibration`() {
        val crop = AnchorRegion(.1f, .1f, .2f, .04f)
        val before = crop.copy()
        assertFalse(PointerStripOcclusion.covers(crop, coordinates, true))
        assertEquals(before, crop)
        assertFalse(PointerStripOcclusion.covers(crop.copy(y = coordinates.y + coordinates.height), coordinates, true))
    }
}
