package com.example.overdex.data

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCalibrationTest {
    @Test
    fun `default countdown region includes horizontal margin for the full GO glyph`() {
        val region = BattleCalibration.DEFAULT_COUNTDOWN_REGION

        assertEquals(0.20f, region.x)
        assertEquals(0.60f, region.width)
        assertEquals(0.80f, region.x + region.width)
    }

    @Test
    fun `only previously shipped countdown defaults are migrated`() {
        assertTrue(BattleCalibration.isSupersededCountdownDefault(BattleCalibration.PREVIOUS_NARROW_COUNTDOWN_REGION))
        assertFalse(BattleCalibration.isSupersededCountdownDefault(AnchorRegion(x = 0.25f, y = 0.35f, width = 0.50f, height = 0.30f)))
    }
}
