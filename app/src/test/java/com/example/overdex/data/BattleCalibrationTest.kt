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
        // The longer grade and a 48 px vertical-motion allowance must fit the
        // same visible box without extending down to the bubble controls.
        val glyphs = listOf(
            floatArrayOf(236f, 410f, 437f, 476f),
            floatArrayOf(138f, 410f, 534f, 476f),
        )
        glyphs.forEach { glyph ->
            assertTrue(region.x * 672f <= glyph[0])
            assertTrue(region.y * 1493f <= glyph[1] - 48f)
            assertTrue((region.x + region.width) * 672f >= glyph[2])
            assertTrue((region.y + region.height) * 1493f >= glyph[3] + 48f)
        }
        assertTrue(region.y * 1493f > 330f)
        assertTrue((region.y + region.height) * 1493f < 800f)
    }

    @Test
    fun `qte progress box contains Pixel four grade glyphs with vertical motion allowance`() {
        val region = BattleCalibration().qteProgressTextRegion
        // 1080 x 2280 source screenshots 20261108_152147, _152206, _152222.
        // Conservative complete glyph bounds include the antialiased outline.
        val glyphs = listOf(
            floatArrayOf(390f, 624f, 690f, 722f), // NICE!
            floatArrayOf(330f, 632f, 745f, 736f), // GREAT!
            floatArrayOf(144f, 614f, 936f, 735f), // EXCELLENT!
        )
        glyphs.forEach { glyph ->
            assertTrue(region.x * 1080f <= glyph[0])
            assertTrue(region.y * 2280f <= glyph[1] - 64f)
            assertTrue((region.x + region.width) * 1080f >= glyph[2])
            assertTrue((region.y + region.height) * 2280f >= glyph[3] + 64f)
        }
        assertEquals(BattleCalibration.PREVIOUS_DEFAULT_QTE_PROGRESS_TEXT_REGION.x, region.x)
        assertEquals(BattleCalibration.PREVIOUS_DEFAULT_QTE_PROGRESS_TEXT_REGION.width, region.width)
        assertTrue((region.y + region.height) * 2280f < 900f)
    }
}
