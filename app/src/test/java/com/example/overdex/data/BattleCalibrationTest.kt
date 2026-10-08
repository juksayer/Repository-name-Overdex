package com.example.overdex.data

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCalibrationTest {
    @Test
    fun `default countdown region includes horizontal margin for the full GO glyph`() {
        val region = BattleCalibration.DEFAULT_COUNTDOWN_REGION

        assertEquals(0.20f, region.x)
        assertEquals(0.60f, region.width)
        assertEquals(0.80f, region.x + region.width)
    }

    @Test
    fun `only previously shipped countdown defaults are migrated`() {
        assertTrue(BattleCalibration.isSupersededCountdownDefault(BattleCalibration.PREVIOUS_NARROW_COUNTDOWN_REGION))
        assertFalse(BattleCalibration.isSupersededCountdownDefault(AnchorRegion(x = 0.25f, y = 0.35f, width = 0.50f, height = 0.30f)))
    }

    @Test
    fun `schema one named profile moves former species strips onto the text row`() {
        val legacy = BattleCalibration(
            playerSpeciesNameRegion = AnchorRegion(
                x = 20f / 1080f,
                y = 229f / 2400f,
                width = 272f / 1080f,
                height = 60f / 2400f
            ),
            opponentSpeciesNameRegion = AnchorRegion(
                x = 788f / 1080f,
                y = 241f / 2400f,
                width = 272f / 1080f,
                height = 60f / 2400f
            )
        )

        val migrated = migrateNamedSpeciesRegions(1, 1080, 2400, legacy)
        val expected = BattleCalibration()

        assertEquals(expected.playerSpeciesNameRegion.y, migrated.playerSpeciesNameRegion.y)
        assertEquals(expected.playerSpeciesNameRegion.height, migrated.playerSpeciesNameRegion.height)
        assertEquals(expected.opponentSpeciesNameRegion.y, migrated.opponentSpeciesNameRegion.y)
        assertEquals(expected.opponentSpeciesNameRegion.height, migrated.opponentSpeciesNameRegion.height)
        assertEquals(legacy.playerSpeciesNameRegion.x, migrated.playerSpeciesNameRegion.x)
        assertEquals(legacy.opponentSpeciesNameRegion.width, migrated.opponentSpeciesNameRegion.width)
        assertEquals(legacy, migrateNamedSpeciesRegions(2, 1080, 2400, legacy))
        assertEquals(legacy, migrateNamedSpeciesRegions(1, 1080, 2280, legacy))
    }

    @Test
    fun `current profile and unrelated custom species boxes are preserved`() {
        val current = BattleCalibration(
            playerSpeciesNameRegion = AnchorRegion(0.10f, 0.30f, 0.20f, 0.05f),
            opponentSpeciesNameRegion = AnchorRegion(0.70f, 0.30f, 0.20f, 0.05f)
        )

        assertEquals(current, migrateNamedSpeciesRegions(1, 1080, 2400, current))
        assertEquals(current, migrateNamedSpeciesRegions(2, 1080, 2400, current))
    }
}
