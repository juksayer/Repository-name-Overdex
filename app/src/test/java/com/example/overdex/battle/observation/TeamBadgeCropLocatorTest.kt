package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class TeamBadgeCropLocatorTest {
    @Test fun `one remaining ball is found above misleading HUD cards`() = checkRecordedRows("one", 1, 2)
    @Test fun `two remaining balls are found above misleading HUD cards`() = checkRecordedRows("two", 2, 0)
    @Test fun `three remaining balls are found above misleading HUD cards`() = checkRecordedRows("three", 3, 0)

    private fun checkRecordedRows(name: String, remaining: Int, falseHudCount: Int) {
        val directory = File("src/test/resources/battle/badge-location-20261024")
        val hud = ImageIO.read(File(directory, "$name-assigned-hud.png"))
        val native = ImageIO.read(File(directory, "$name-native.png"))
        // Manual annotation of the HUD row; not reconstructed capture-time metadata.
        val overlay = AnchorRegion(544f / 672, 165f / 1419, 116f / 672, 36f / 1419)
        val assigned = BattleCropProvenance(name, 672, 1419, BattleCropBounds(544, 165, 660, 201))
        val translated = assigned.copy(bounds = BattleCropBounds(544, 123, 660, 159))
        assertEquals(falseHudCount, OpponentBattleResourceCounter.pokeBallCount(116, 36, hud::getRGB))
        assertEquals(remaining, OpponentBattleResourceCounter.pokeBallCount(116, 36, native::getRGB))
        for (region in listOf(BattleRegionId.PLAYER_POKE_BALLS, BattleRegionId.OPPONENT_POKE_BALLS)) {
            assertFalse("$name: HUD must not stop native-row recovery",
                TeamBadgeCropLocator.containsResourceShape(region, assigned, overlay, hud::getRGB))
            assertTrue("$name: native row must remain eligible",
                TeamBadgeCropLocator.containsResourceShape(region, translated, overlay, native::getRGB))
        }
    }

    @Test fun `location validation agrees with witnessed resource visibility including partial HUD overlap`() {
        val directory = File("src/test/resources/battle/resources-20261020")
        val cases = Json.parseToJsonElement(File(directory, "manifest.json").readText()).jsonArray
        for (value in cases) {
            val sample = value.jsonObject
            val name = sample.getValue("file").jsonPrimitive.content
            val crop = ImageIO.read(File(directory, name))
            // A larger synthetic canvas keeps negative crop-local HUD coordinates valid.
            val provenance = BattleCropProvenance(name, 1000, 1000,
                BattleCropBounds(300, 300, 300 + crop.width, 300 + crop.height))
            val overlay = (sample["overlayBounds"] as? JsonObject)?.let {
                val left = it.getValue("left").jsonPrimitive.float
                val top = it.getValue("top").jsonPrimitive.float
                AnchorRegion((300 + left) / 1000, (300 + top) / 1000,
                    (it.getValue("right").jsonPrimitive.float - left) / 1000,
                    (it.getValue("bottom").jsonPrimitive.float - top) / 1000)
            }
            val regions = if (sample.getValue("resource").jsonPrimitive.content == "POKE_BALLS")
                listOf(BattleRegionId.PLAYER_POKE_BALLS, BattleRegionId.OPPONENT_POKE_BALLS)
            else listOf(BattleRegionId.PLAYER_SHIELDS, BattleRegionId.OPPONENT_SHIELDS)
            for (region in regions) {
                assertEquals("$name $region", sample.getValue("expectedCount").jsonPrimitive.intOrNull != null,
                    TeamBadgeCropLocator.containsResourceShape(region, provenance, overlay, crop::getRGB))
            }
        }
    }
}
