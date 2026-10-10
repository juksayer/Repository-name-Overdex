package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveHpBarMeasurerTest {
    @Test
    fun `active bar size does not jump to a smaller card or thin effect`() {
        assertTrue(ActiveHpBarGeometry.matchesSize(191, 18, 193, 17))
        assertTrue(!ActiveHpBarGeometry.matchesSize(144, 8, 193, 17))
        assertTrue(!ActiveHpBarGeometry.matchesSize(187, 8, 193, 17))
    }

    @Test
    fun `measures active HP bar outline geometry and left-aligned fill`() {
        val width = 240
        val height = 120
        val pixels = mutableMapOf<Pair<Int, Int>, Int>()
        val white = 0xffeeeeee.toInt()
        val fill = 0xff1effbc.toInt()
        for (x in 20 until 220) {
            pixels[x to 40] = white
            pixels[x to 62] = white
        }
        for (y in 40..62) {
            pixels[20 to y] = white
            pixels[219 to y] = white
        }
        for (x in 23 until 123) for (y in 46 until 58) pixels[x to y] = fill

        val measurement = ActiveHpBarMeasurer.measureAll(width, height) { x, y ->
            pixels[x to y] ?: 0xff182825.toInt()
        }.singleOrNull()

        assertNotNull(measurement)
        measurement!!
        assertEquals(20, measurement.left)
        assertEquals(40, measurement.top)
        assertEquals(220, measurement.right)
        assertEquals(62, measurement.bottom)
        assertEquals(0.51f, measurement.filledFraction, 0.03f)
        assertTrue(measurement.confidence >= 0.7f)
    }

    @Test
    fun `does not treat a short QTE effect as an active HP bar`() {
        val result = ActiveHpBarMeasurer.measureAll(300, 120) { x, y ->
            if ((y == 40 || y == 46) && x in 30 until 280) 0xffeeeeee.toInt()
            else if (x in 34 until 180 && y in 42 until 45) 0xff1effbc.toInt()
            else 0xff182825.toInt()
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `does not treat a neutral outline without an HP fill as a measurement`() {
        val result = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
            if ((y == 40 || y == 62) && x in 20 until 220) 0xffeeeeee.toInt() else 0xff182825.toInt()
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `measures compact bar after capture scaling`() {
        val result = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
            when {
                (y == 40 || y == 50) && x in 20 until 220 -> 0xffeeeeee.toInt()
                (x == 20 || x == 219) && y in 40..50 -> 0xffeeeeee.toInt()
                x in 23 until 150 && y in 42 until 49 -> 0xff1effbc.toInt()
                else -> 0xff182825.toInt()
            }
        }.singleOrNull()

        assertNotNull(result)
        assertEquals(40, result!!.top)
        assertEquals(50, result.bottom)
    }

    @Test
    fun `orange pulse outline remains the same measurable hp bar`() {
        val orange = 0xffff8a20.toInt()
        val result = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
            when {
                (y == 40 || y == 62) && x in 20 until 220 -> orange
                (x == 20 || x == 219) && y in 40..62 -> orange
                x in 23 until 150 && y in 44 until 59 -> 0xff1effbc.toInt()
                else -> 0xff182825.toInt()
            }
        }.singleOrNull()

        assertNotNull(result)
        assertEquals(20, result!!.left)
        assertEquals(220, result.right)
        assertEquals(0.65f, result.filledFraction, 0.03f)
    }

    @Test
    fun `thick orange outline and orange damage chunk do not count as remaining hp`() {
        val result = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
            when {
                x in 20 until 220 && (y in 40..42 || y in 60..62) -> 0xffff8a20.toInt()
                (x in 20..22 || x in 217..219) && y in 40..62 -> 0xffff8a20.toInt()
                x in 23 until 72 && y in 44..58 -> 0xff1effbc.toInt()
                x in 72 until 145 && y in 44..58 -> 0xffff8a20.toInt()
                else -> 0xff182825.toInt()
            }
        }.firstOrNull()

        assertNotNull(result)
        assertEquals(0.25f, result!!.filledFraction, 0.03f)
    }

    @Test
    fun `yellow and red hp remain measurable`() {
        for (fill in listOf(0xfff7ee59.toInt(), 0xffee3540.toInt())) {
            val result = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
                when {
                    (y == 40 || y == 62) && x in 20 until 220 -> 0xffeeeeee.toInt()
                    (x == 20 || x == 219) && y in 40..62 -> 0xffeeeeee.toInt()
                    x in 23 until 62 && y in 44..58 -> fill
                    else -> 0xff182825.toInt()
                }
            }.singleOrNull()
            assertNotNull(result)
            assertEquals(0.20f, result!!.filledFraction, 0.03f)
        }
    }

    @Test
    fun `rejects a wider battlefield highlight and keeps the rectangular hp bar`() {
        val white = 0xffeeeeee.toInt()
        val fill = 0xff1effbc.toInt()
        val result = ActiveHpBarMeasurer.measureAll(320, 220) { x, y ->
            when {
                // A wider pair of bright rows with coloured scenery inside. It
                // has no vertical end caps and must never become the X anchor.
                (y == 25 || y == 45) && x in 15 until 285 -> white
                x in 18 until 190 && y in 29 until 42 -> fill

                // Pokémon art can form a convincing saturated rectangle where
                // it is clipped by the purpose-specific crop boundary.
                (y == 65 || y == 83) && x in 0 until 193 -> white
                (x == 0 || x == 192) && y in 65..83 -> white
                x in 3 until 185 && y in 68 until 81 -> fill

                // The actual GO HP rectangle.
                (y == 110 || y == 128) && x in 62 until 255 -> white
                (x == 62 || x == 254) && y in 110..128 -> white
                x in 65 until 175 && y in 113 until 126 -> fill
                else -> 0xff182825.toInt()
            }
        }

        assertTrue(result.isNotEmpty())
        assertEquals(62, result.first().left)
        assertEquals(110, result.first().top)
        assertEquals(255, result.first().right)
        assertEquals(128, result.first().bottom)
        assertTrue(result.none { it.top == 25 })
        assertTrue(result.none { it.top == 65 })
    }

    @Test
    fun `orange damage chunk inside white bar is not measured as border pulse`() {
        val white = 0xffeeeeee.toInt()
        val orange = 0xffff8a20.toInt()
        val bar = ActiveHpBarMeasurement(20, 40, 220, 62, 0.75f, 0.9f)
        val appearance = ActiveHpBarBorderAppearanceMeasurer.measure(240, 120, bar) { x, y ->
            when {
                (y == 40 || y == 62) && x in 20 until 220 -> white
                (x == 20 || x == 219) && y in 40..62 -> white
                x in 150 until 215 && y in 44 until 59 -> orange
                x in 23 until 150 && y in 44 until 59 -> 0xff1effbc.toInt()
                else -> 0xff182825.toInt()
            }
        }

        requireNotNull(appearance)
        assertTrue(appearance.whiteFraction > 0.95f)
        assertTrue(appearance.orangeFraction < 0.01f)
    }

    @Test
    fun `orange outline is measured as border pulse rather than fill color`() {
        val orange = 0xffff8a20.toInt()
        val bar = ActiveHpBarMeasurement(20, 40, 220, 62, 0.75f, 0.9f)
        val appearance = ActiveHpBarBorderAppearanceMeasurer.measure(240, 120, bar) { x, y ->
            when {
                (y == 40 || y == 62) && x in 20 until 220 -> orange
                (x == 20 || x == 219) && y in 40..62 -> orange
                x in 23 until 150 && y in 44 until 59 -> 0xff1effbc.toInt()
                else -> 0xff182825.toInt()
            }
        }

        requireNotNull(appearance)
        assertTrue(appearance.orangeFraction > 0.95f)
        assertTrue(appearance.whiteFraction < 0.01f)
    }
}
