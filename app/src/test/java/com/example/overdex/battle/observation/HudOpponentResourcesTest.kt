package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import org.junit.Assert.*
import org.junit.Test

class HudOpponentResourcesTest {
    @Test fun `an archived match cannot overwrite the next match forecast`() {
        val old = com.example.overdex.battle.forecast.OpponentEnergyEstimate(
            "Sealeo", "Water Gun", 40, 10, 2, 20, false, false, 0, false)
        DroidballOverlayPresentation.beginEnergyForecast("old")
        DroidballOverlayPresentation.beginEnergyForecast("new")
        DroidballOverlayPresentation.setOpponentEnergyEstimate("old", old)
        assertNull(DroidballOverlayPresentation.opponentEnergyEstimate.value)
        DroidballOverlayPresentation.setOpponentEnergyEstimate("new", old.copy(energy = 10))
        assertEquals(10, DroidballOverlayPresentation.opponentEnergyEstimate.value!!.energy)
        DroidballOverlayPresentation.reset()
        DroidballOverlayPresentation.setOpponentEnergyEstimate("new", old)
        assertNull(DroidballOverlayPresentation.opponentEnergyEstimate.value)
    }
    @Test fun `retained counts accept newer readings and reset for the next match`() {
        DroidballOverlayPresentation.reset()
        assertNull(DroidballOverlayPresentation.opponentResources.value.pokeBalls)
        assertFalse(DroidballOverlayPresentation.battleBadgeSeen.value)
        fun balls(n: Int, at: Long) = DroidballOverlayPresentation.recordOpponentResources(
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, n, 3), at)
        balls(3, 1); balls(2, 3); balls(3, 2)
        assertEquals(2, DroidballOverlayPresentation.opponentResources.value.pokeBalls)
        DroidballOverlayPresentation.recordOpponentResources(
            OpponentBattleResourceCountMeasured(OpponentBattleResource.SHIELDS, 1, 2), 2)
        assertEquals(1, DroidballOverlayPresentation.opponentResources.value.shields)
        assertTrue(DroidballOverlayPresentation.battleBadgeSeen.value)
        // A camera/QTE phase does not clear the last observed resources.
        DroidballOverlayPresentation.showBattleHud()
        assertEquals(2, DroidballOverlayPresentation.opponentResources.value.pokeBalls)
        DroidballOverlayPresentation.reset()
        assertEquals(HudOpponentResources(), DroidballOverlayPresentation.opponentResources.value)
        assertFalse(DroidballOverlayPresentation.battleBadgeSeen.value)
    }
}
