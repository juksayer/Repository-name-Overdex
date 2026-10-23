package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TeamBadgePresenceDetectorTest {
    @Test fun `badge colors in the wrong rows are only scenery`() {
        assertFalse(TeamBadgePresenceDetector.present(40, 12, AnchorRegion(0f, 0f, 1f, 1f)) { x, y ->
            when {
                y == 0 && x in 10..14 -> 0x407080 // teal scene edge, not a name row
                y == 4 && x in 10..14 -> 0xff3030 // red object above the resource row
                else -> 0xeef5f5
            }
        })
    }

    @Test fun `recorded battle badges show HUD but result with lingering HUD does not`() {
        val samples = Json.parseToJsonElement(javaClass.getResource(
            "/battle/recorded-team-badge-samples.json")!!.readText()).jsonArray
        for (entry in samples) {
            val sample = entry.jsonObject
            val pixels = sample.getValue("pixels").jsonArray.map { it.jsonPrimitive.int }
            assertEquals(sample.getValue("source").jsonPrimitive.content + sample.getValue("side"),
                sample.getValue("expectedPresent").jsonPrimitive.boolean,
                TeamBadgePresenceDetector.present(40, 12, AnchorRegion(0f, 0f, 1f, 1f)) { x, y -> pixels[y * 40 + x] })
        }
    }

    @Test fun `white or dark loading screen is not a badge`() {
        for (color in listOf(0xffffff, 0, 0x335566)) {
            assertFalse(TeamBadgePresenceDetector.present(40, 12, AnchorRegion(0f, 0f, 1f, 1f)) { _, _ -> color })
        }
    }
}
