package com.example.overdex.battle.observation

import com.example.overdex.data.BattleCalibration
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesCropContractTest {
    @Test
    fun `species crops retain the measured name-strip contracts`() {
        val calibration = BattleCalibration()

        // android.graphics.Rect is a stub in local unit tests. Assert the
        // normalized contract here; resolver pixel behavior is Android-tested.
        assertEquals(BattleRegionId.PLAYER_SPECIES_NAME, BattleCropContracts.playerActiveSpeciesText.region)
        assertEquals(20f / 1080f, calibration.playerSpeciesNameRegion.x)
        assertEquals(145f / 2400f, calibration.playerSpeciesNameRegion.y)
        assertEquals(272f / 1080f, calibration.playerSpeciesNameRegion.width)
        assertEquals(60f / 2400f, calibration.playerSpeciesNameRegion.height)
        assertEquals(BattleRegionId.OPPONENT_SPECIES_NAME, BattleCropContracts.opponentActiveSpeciesText.region)
        assertEquals(788f / 1080f, calibration.opponentSpeciesNameRegion.x)
        assertEquals(145f / 2400f, calibration.opponentSpeciesNameRegion.y)
        assertEquals(272f / 1080f, calibration.opponentSpeciesNameRegion.width)
        assertEquals(60f / 2400f, calibration.opponentSpeciesNameRegion.height)
    }
}
