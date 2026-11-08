package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class TransientTextProbeTest {
    @Test fun `recorded announcement glyph shapes outrank their no-text companion frames`() {
        val ready = fixture("get-ready-announcement")
        val move = fixture("rock-tomb-announcement")
        val blank = fixture("nice-announcement")
        assertTrue(ready.hasText)
        assertTrue(move.hasText)
        assertTrue(ready.score > blank.score)
        assertTrue(move.score > blank.score)
        assertFalse(blank.hasText)
        assertNotEquals(0L, ready.signature)
        assertNotEquals(ready.signature, move.signature)
    }

    @Test fun `recorded NICE glyph shape outranks the surrounding cloud crops`() {
        val nice = fixture("nice-qte")
        assertTrue(nice.hasText)
        assertTrue(nice.score >= .8f)
        for (name in listOf("get-ready-qte", "rock-tomb-qte")) {
            val blank = fixture(name)
            assertFalse(blank.hasText)
            assertEquals(0L, blank.signature)
            assertTrue(nice.score > blank.score)
        }
    }

    @Test fun `flat scenery black and a single bright decoration do not count as text`() {
        for (color in listOf(0xff000000.toInt(), 0xffeeeeee.toInt(), 0xff8097b6.toInt())) {
            assertFalse(analyze(IntArray(WIDTH * HEIGHT) { color }).hasText)
        }
        val pixels = IntArray(WIDTH * HEIGHT) { NAVY }
        rectangle(pixels, 35, 8, 68, 28, WHITE)
        assertFalse(analyze(pixels).hasText)
    }

    @Test fun `white gray and yellow glyph rows are supported but blue particles are not`() {
        for (color in listOf(WHITE, 0xff999999.toInt(), 0xffffd837.toInt())) {
            val reading = analyze(letters(color))
            assertTrue("glyph color ${color.toUInt().toString(16)}", reading.hasText)
            assertNotEquals(0L, reading.signature)
            assertNotNull(reading.textBounds)
        }
        assertFalse(analyze(letters(0xff8dceff.toInt())).hasText)
    }

    @Test fun `excluded HUD text cannot manufacture a text-bearing frame or dark contrast border`() {
        val pixels = letters(WHITE)
        assertTrue(analyze(pixels).hasText)
        val masked = TransientTextProbe.analyze(pixels, WIDTH, HEIGHT,
            listOf(TransientTextProbe.PixelBounds(0, 0, WIDTH, HEIGHT)))
        assertFalse(masked.hasText)
        assertEquals(0L, masked.signature)
        assertNull(masked.textBounds)
        assertEquals(0f, masked.score, 0f)

        val flat = IntArray(WIDTH * HEIGHT) { WHITE }
        val artificialHoles = (0..5).map { TransientTextProbe.PixelBounds(8 + it * 15, 10, 14 + it * 15, 30) }
        assertFalse(TransientTextProbe.analyze(flat, WIDTH, HEIGHT, artificialHoles).hasText)
    }

    @Test fun `clipped edge fragments and transparent pixels do not become a glyph row`() {
        val clipped = letters(WHITE, top = 0)
        assertFalse(analyze(clipped).hasText)
        val transparent = letters(0x00ffffff)
        assertFalse(analyze(transparent).hasText)
    }

    @Test fun `signature ignores unrelated scenery and foreground brightness`() {
        val first = letters(WHITE)
        val changed = letters(0xffaaaaaa.toInt())
        for (x in 0 until WIDTH) changed[x] = 0xff123456.toInt()
        assertEquals(analyze(first).signature, analyze(changed).signature)
        assertArrayEquals("probe must leave its evidence untouched", first.copyOf(), first.also { analyze(it) })
    }

    @Test fun `NICE and GREAT shaped rows keep distinct exact signatures`() {
        val nice = rasterWord("NICE")
        val great = rasterWord("GREAT")
        val first = analyze(nice)
        val second = analyze(great)
        assertTrue(first.hasText)
        assertTrue(second.hasText)
        assertNotEquals(first.signature, second.signature)
        assertEquals(first.signature, analyze(nice.copyOf()).signature)
    }

    @Test fun `invalid geometry is a weak reading rather than an exception`() {
        for ((width, height) in listOf(-1 to 20, 40 to 0, Int.MAX_VALUE to Int.MAX_VALUE, 30 to 20)) {
            val reading = TransientTextProbe.analyze(IntArray(10), width, height)
            assertFalse(reading.hasText)
            assertEquals(0L, reading.signature)
        }
    }

    @Test fun `sampling and returned bounds remain in source crop coordinates`() {
        val small = letters(WHITE)
        val scale = 4
        val large = IntArray(WIDTH * HEIGHT * scale * scale) { index ->
            val x = index % (WIDTH * scale)
            val y = index / (WIDTH * scale)
            small[(y / scale) * WIDTH + x / scale]
        }
        val reading = TransientTextProbe.analyze(large, WIDTH * scale, HEIGHT * scale)
        assertTrue(reading.hasText)
        val bounds = requireNotNull(reading.textBounds)
        assertEquals(8 * scale, bounds.left)
        assertEquals(10 * scale, bounds.top)
        assertEquals(89 * scale, bounds.right)
        assertEquals(30 * scale, bounds.bottom)
    }

    private fun analyze(pixels: IntArray) = TransientTextProbe.analyze(pixels, WIDTH, HEIGHT)

    private fun letters(color: Int, top: Int = 10): IntArray = IntArray(WIDTH * HEIGHT) { NAVY }.also { pixels ->
        repeat(6) { rectangle(pixels, 8 + it * 15, top, 14 + it * 15, top + 20, color) }
    }

    private fun rectangle(pixels: IntArray, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top until bottom) for (x in left until right) pixels[y * WIDTH + x] = color
    }

    private fun rasterWord(word: String): IntArray {
        val alphabet = mapOf(
            'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
            'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
            'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'G' to listOf("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
            'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        )
        return IntArray(WIDTH * HEIGHT) { NAVY }.also { pixels ->
            word.forEachIndexed { letter, character ->
                requireNotNull(alphabet[character]).forEachIndexed { row, line ->
                    line.forEachIndexed { column, ink ->
                        if (ink == '1') rectangle(pixels, 10 + letter * 14 + column * 2, 10 + row * 3,
                            12 + letter * 14 + column * 2, 13 + row * 3, WHITE)
                    }
                }
            }
        }
    }

    /**
     * Tiny non-personal masks derived from real preserved crops, not raw screenshots.
     * provenance.json records source hashes, sizes, and the lossy transformation.
     * Geometry is real; this deliberately does not assert ML Kit recognition.
     */
    private fun fixture(name: String): TransientTextProbe.Reading {
        val lines = requireNotNull(javaClass.getResourceAsStream("/transient-text-probe/$name.mask"))
            .bufferedReader().use { it.readLines() }
        val (width, height) = lines.first().split(' ').map { it.toInt() }
        val pixels = IntArray(width * height) { NAVY }
        assertEquals(height + 1, lines.size)
        lines.drop(1).forEachIndexed { y, row ->
            if (row != "-") row.split(',').forEach { run ->
                val (start, length) = run.split(':').map { it.toInt() }
                for (x in start until start + length) pixels[y * width + x] = WHITE
            }
        }
        return TransientTextProbe.analyze(pixels, width, height)
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 40
        const val NAVY = 0xff18365d.toInt()
        const val WHITE = 0xffeeeeee.toInt()
    }
}
