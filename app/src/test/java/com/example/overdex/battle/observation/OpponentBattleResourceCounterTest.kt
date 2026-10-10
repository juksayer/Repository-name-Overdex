package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

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

    @Test fun `recorded badge strips distinguish three two and one survivors`() {
        listOf("celluloid-shot0009.jpg" to 3, "celluloid-shot0014.jpg" to 2, "celluloid-shot0020.jpg" to 1).forEach { (name, expected) ->
            val frame = ImageIO.read(File("src/main/assets/battle_samples/$name"))
            val left = (frame.width * .82).toInt()
            val top = (frame.height * .118).toInt()
            val right = (frame.width * .99).toInt()
            val bottom = (frame.height * .14).toInt()
            assertEquals(name, expected, OpponentBattleResourceCounter.pokeBallCount(right - left, bottom - top) { x, y -> frame.getRGB(left + x, top + y) })
        }
    }

    @Test fun `blank black or white crops are unavailable rather than zero survivors`() {
        assertNull(OpponentBattleResourceCounter.pokeBallCount(116, 38) { x, _ -> if (x > 113) rgb(80, 180, 195) else rgb(0, 0, 0) })
        assertNull(OpponentBattleResourceCounter.pokeBallCount(116, 38) { _, _ -> WHITE })
    }

    @Test fun `three visible darkened balls can establish zero survivors`() {
        assertEquals(0, OpponentBattleResourceCounter.pokeBallCount(90, 24) { x, y ->
            if (y in 6..17 && listOf(8..20, 35..47, 62..74).any { x in it }) rgb(65, 65, 65) else WHITE
        })
    }
    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object { const val WHITE = -1 }
}
