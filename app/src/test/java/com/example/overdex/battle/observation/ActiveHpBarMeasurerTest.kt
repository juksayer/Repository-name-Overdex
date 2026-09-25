package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveHpBarMeasurerTest {
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
}
