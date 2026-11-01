package com.example.overdex.data

import org.junit.Assert.assertEquals
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
    fun `qte progress box contains the recorded Nice and Excellent glyph bounds`() {
        val region = BattleCalibration().qteProgressTextRegion
        // Observed in the 672 x 1493 source frames at 103173 ms and 105215 ms.
        // The longer grade must fit the same box without capturing the lower
        // bubble controls or the higher species and move announcement.
        val glyphs = listOf(
            floatArrayOf(236f, 410f, 437f, 476f),
            floatArrayOf(138f, 410f, 534f, 476f),
        )
        glyphs.forEach { glyph ->
            assertTrue(region.x * 672f <= glyph[0])
            assertTrue(region.y * 1493f <= glyph[1])
            assertTrue((region.x + region.width) * 672f >= glyph[2])
            assertTrue((region.y + region.height) * 1493f >= glyph[3])
        }
        assertTrue(region.y * 1493f > 359f)
        assertTrue((region.y + region.height) * 1493f < 800f)
    }
}
