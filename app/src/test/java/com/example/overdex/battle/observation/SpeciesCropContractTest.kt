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
        assertEquals(BattleRegionId.PLAYER_TEAM_INFO, BattleCropContracts.playerActiveSpeciesText.region)
        assertEquals(22f / 425f, BattleCropContracts.playerActiveSpeciesText.areaInRegion.x)
        assertEquals(25f / 125f, BattleCropContracts.playerActiveSpeciesText.areaInRegion.y)
        assertEquals(253f / 425f, BattleCropContracts.playerActiveSpeciesText.areaInRegion.width)
        assertEquals(25f / 125f, BattleCropContracts.playerActiveSpeciesText.areaInRegion.height)
        assertEquals(BattleRegionId.OPPONENT_SPECIES_NAME, BattleCropContracts.opponentActiveSpeciesText.region)
        assertEquals(785f / 1080f, calibration.opponentSpeciesNameRegion.x)
        assertEquals(250f / 2400f, calibration.opponentSpeciesNameRegion.y)
        assertEquals(253f / 1080f, calibration.opponentSpeciesNameRegion.width)
        assertEquals(25f / 2400f, calibration.opponentSpeciesNameRegion.height)
    }
}
