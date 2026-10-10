package com.example.overdex.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleCalibrationTest {
    @Test
    fun `default countdown region includes horizontal margin for the full GO glyph`() {
        val region = BattleCalibration.DEFAULT_COUNTDOWN_REGION

        assertEquals(0.20f, region.x)
        assertEquals(0.60f, region.width)
        assertEquals(0.80f, region.x + region.width)
    }
}
