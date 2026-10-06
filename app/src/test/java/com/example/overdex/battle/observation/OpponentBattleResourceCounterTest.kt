package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class OpponentBattleResourceCounterTest {
    @Test
    fun countsThreeSeparatedBrightPokeBalls() {
        val width = 90
        val height = 24
        val redRuns = listOf(8..20, 35..47, 62..74)

        val count = OpponentBattleResourceCounter.count(
            width = width,
            height = height,
            pixelAt = { x, y ->
                if (y in 6..17 && redRuns.any { x in it }) rgb(225, 45, 35) else WHITE
            },
            maximumCount = 3,
            acceptsPixel = OpponentBattleResourceCounter::isBrightPokeBallRed
        )

        assertEquals(3, count)
    }

    @Test
    fun ignoresDimmedBallAndCountsRemainingTwo() {
        val redRuns = listOf(8..20, 35..47)
        val count = OpponentBattleResourceCounter.count(
            width = 90,
            height = 24,
            pixelAt = { x, y ->
                when {
                    y in 6..17 && redRuns.any { x in it } -> rgb(225, 45, 35)
                    y in 6..17 && x in 62..74 -> rgb(65, 65, 65)
                    else -> WHITE
                }
            },
            maximumCount = 3,
            acceptsPixel = OpponentBattleResourceCounter::isBrightPokeBallRed
        )

        assertEquals(2, count)
    }

    @Test
    fun countsTwoShieldColorClusters() {
        val runs = listOf(7..24, 39..56)
        val count = OpponentBattleResourceCounter.count(
            width = 64,
            height = 24,
            pixelAt = { x, y ->
                if (y in 5..18 && runs.any { x in it }) rgb(225, 110, 235) else WHITE
            },
            maximumCount = 2,
            acceptsPixel = OpponentBattleResourceCounter::isShieldColor
        )

        assertEquals(2, count)
    }
    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object { const val WHITE = -1 }
}
