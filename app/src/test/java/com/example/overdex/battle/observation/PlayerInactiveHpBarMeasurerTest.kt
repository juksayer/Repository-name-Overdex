package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerInactiveHpBarMeasurerTest {
    @Test fun `measures one separately captured card HP bar from its reference band`() {
        val pixels = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 17 until 81) for (y in 138 until 150) pixels[x to y] = 0xff1edcb4.toInt()
        assertEquals(64f / 127f, PlayerInactiveHpBarMeasurer.measure(160, 190) { x, y -> pixels[x to y] ?: 0xff192323.toInt() }!!, 0.02f)
    }

    @Test fun `each separately captured card has its own HP measurement`() {
        val pixels = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 17 until 144) for (y in 138 until 150) pixels[x to y] = 0xfff0d21e.toInt()
        val pixelAt = { x: Int, y: Int -> pixels[x to y] ?: 0xff192323.toInt() }
        assertEquals(1f, PlayerInactiveHpBarMeasurer.measure(160, 190, pixelAt)!!, 0.02f)
        assertNull(PlayerInactiveHpBarMeasurer.measure(160, 190) { _, _ -> 0xff192323.toInt() })
    }
}
