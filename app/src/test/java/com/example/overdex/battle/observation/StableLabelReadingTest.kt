package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class StableLabelReadingTest {
    private val pixels = intArrayOf(-1, -1, 0xff102030.toInt(), -1)
    private fun ink(p: IntArray = pixels) = LabelInk.fromPixels(4, 1, p)
    @Test fun `same letter mask reuses its actual source for at most five seconds`() {
        val cache = StableLabelReading()
        cache.remember(ink(), 1_000, "EARTH POWER", listOf("crop-original"))
        val backgroundChange = pixels.copyOf().also { it[2] = 0xff283010.toInt() }
        assertEquals(listOf("crop-original"), cache.reuse(ink(backgroundChange), 1_001)?.references)
        assertNull(cache.reuse(ink(), 999))
        assertNull(cache.reuse(ink(), 5_000_001_000))
    }
    @Test fun `one changed letter pixel dimensions or missing evidence requires OCR`() {
        val cache = StableLabelReading()
        cache.remember(ink(), 0, "EARTH POWER", emptyList())
        assertNull(cache.reuse(ink(), 1))
        cache.remember(ink(), 0, "EARTH POWER", listOf("crop"))
        assertNull(cache.reuse(ink(pixels.copyOf().also { it[1] = 0xff202020.toInt() }), 1))
        assertNull(cache.reuse(LabelInk.fromPixels(2, 2, pixels), 1))
    }
    @Test fun `blank reads are retried sooner`() {
        val cache = StableLabelReading()
        cache.remember(ink(), 0, "", listOf("crop"))
        assertNotNull(cache.reuse(ink(), 999_999_999))
        assertNull(cache.reuse(ink(), 1_000_000_000))
    }
}
