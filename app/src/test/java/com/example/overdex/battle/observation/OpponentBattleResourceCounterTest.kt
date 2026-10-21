package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import com.example.overdex.model.observation.OcrBox
import kotlinx.serialization.json.*

class OpponentBattleResourceCounterTest {
    @Test fun `HUD over a resource cap cannot turn its visible dark rim into a spent resource`() {
        val topRightCovered = OcrBox(60f, 0f, 90f, 10f)
        assertNull(OpponentBattleResourceCounter.pokeBallCount(90, 24, topRightCovered) { x, y ->
            when {
                y in 6..9 && listOf(8..20, 35..47).any { x in it } -> rgb(225, 45, 35)
                y in 10..17 && listOf(8..20, 35..47, 62..74).any { x in it } -> rgb(65, 65, 65)
                else -> WHITE
            }
        })
        assertNull(OpponentBattleResourceCounter.shieldCount(64, 24, OcrBox(32f, 0f, 64f, 10f)) { x, y ->
            if (y in 6..17 && (x in 7..24 || x in 39..56)) rgb(80, 65, 85) else WHITE
        })
    }

    @Test fun `October 20 HUD cards cannot become spent resources and visible badge caps still count`() {
        val directory = File("src/test/resources/battle/resources-20261020")
        val cases = Json.parseToJsonElement(File(directory, "manifest.json").readText()).jsonArray
        for (value in cases) {
            val sample = value.jsonObject
            val name = sample.getValue("file").jsonPrimitive.content
            val crop = ImageIO.read(File(directory, name))
            val bounds = (sample["overlayBounds"] as? JsonObject)?.let {
                OcrBox(it.getValue("left").jsonPrimitive.float, it.getValue("top").jsonPrimitive.float,
                    it.getValue("right").jsonPrimitive.float, it.getValue("bottom").jsonPrimitive.float)
            }
            val actual = if (sample.getValue("resource").jsonPrimitive.content == "POKE_BALLS")
                OpponentBattleResourceCounter.pokeBallCount(crop.width, crop.height, bounds, crop::getRGB)
            else OpponentBattleResourceCounter.shieldCount(crop.width, crop.height, bounds, crop::getRGB)
            assertEquals(name, sample.getValue("expectedCount").jsonPrimitive.intOrNull, actual)
        }
    }

    @Test fun `player shields are counted from an actual badge strip`() {
        val frame = ImageIO.read(File("src/androidTest/assets/species/oct10/lead_frame.png"))
        val crop = frame.getSubimage(145, 125, 60, 30)
        assertEquals(2, OpponentBattleResourceCounter.shieldCount(crop.width, crop.height, crop::getRGB))
    }

    @Test fun `covered shield badge is unknown instead of zero`() {
        assertNull(OpponentBattleResourceCounter.shieldCount(60, 30) { _, _ -> 0xff000000.toInt() })
    }

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

    @Test fun `October 14 disappearing badge does not faint Vaporeon`() {
        val expected = listOf(
            "before.png" to 1,
            "hidden-first.png" to null,
            "hidden-second.png" to null,
            "returned.png" to 1
        )
        for ((name, remaining) in expected) {
            val crop = ImageIO.read(File("src/test/resources/battle/balls-20261014/$name"))
            assertEquals(name, remaining, OpponentBattleResourceCounter.pokeBallCount(crop.width, crop.height, crop::getRGB))
        }
    }

    @Test fun `partial white badge against a dark background is unavailable on either side`() {
        for (mirror in listOf(false, true)) {
            assertNull(OpponentBattleResourceCounter.pokeBallCount(90, 24) { x, y ->
                val position = if (mirror) 89 - x else x
                if (position < 60 && y < 18) WHITE else rgb(0, 0, 0)
            })
        }
    }

    @Test fun `three visible darkened balls can establish zero survivors`() {
        assertEquals(0, OpponentBattleResourceCounter.pokeBallCount(90, 24) { x, y ->
            if (y in 6..17 && listOf(8..20, 35..47, 62..74).any { x in it }) rgb(65, 65, 65) else WHITE
        })
    }
    @Test fun `October 12 hidden sky strips are unavailable and returning badge retains two shields`() {
        for ((name, expected) in listOf("player-shields-74.png" to null, "opponent-shields-75.png" to null, "player-shields-79.png" to 2)) {
            val crop = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/$name"))
            assertEquals(name, expected, OpponentBattleResourceCounter.shieldCount(crop.width, crop.height, crop::getRGB))
        }
    }

    @Test fun `two visible shield glyphs allow zero one and two remaining`() {
        for (remaining in 0..2) {
            assertEquals(remaining, OpponentBattleResourceCounter.shieldCount(64, 24) { x, y ->
                val slot = if (x in 7..24) 0 else if (x in 39..56) 1 else -1
                if (y !in 5..18 || slot < 0) WHITE
                else if (slot < remaining) rgb(225, 110, 235) else rgb(80, 65, 85)
            })
        }
        assertNull(OpponentBattleResourceCounter.shieldCount(64, 24) { _, _ -> WHITE })
    }

    @Test fun `recorded dark player shields are visible zero rather than unavailable`() {
        val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/frame-156.png"))
        val crop = source.getSubimage(146, 124, 57, 35)
        assertEquals(0, OpponentBattleResourceCounter.shieldCount(crop.width, crop.height, crop::getRGB))
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object { const val WHITE = -1 }
}
