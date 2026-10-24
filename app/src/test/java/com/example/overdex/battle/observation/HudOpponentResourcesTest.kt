package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import org.junit.Assert.*
import org.junit.Test

class HudOpponentResourcesTest {
    @Test fun `badge invalidation follows actual count identity and faint changes without double counting`() {
        DroidballOverlayPresentation.reset()
        fun content() = DroidballOverlayPresentation.opponentBadgeContent.value
        fun balls(n: Int, at: Long) = DroidballOverlayPresentation.recordOpponentResources(
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, n, 3), at)
        fun species(name: String, at: Long) = DroidballOverlayPresentation.recordOpponentSpecies(
            name, null, emptyList(), emptyList(), observedAtNanos = at)
        val initial = content().revision
        balls(3, 85); species("Mankey", 86)
        assertEquals(initial + 2, content().revision)
        balls(3, 91); species("Mankey", 92)
        assertEquals("Repeated observations must not keep evicting the image", initial + 2, content().revision)
        balls(2, 98)
        DroidballOverlayPresentation.markOpponentFainted("Mankey", 98)
        val faint = content()
        assertEquals(98L, faint.changedAtNanos)
        assertEquals("Faint invalidation does not deduct a second ball", 2,
            DroidballOverlayPresentation.opponentResources.value.pokeBalls)
        DroidballOverlayPresentation.markOpponentFainted("Mankey", 99)
        balls(3, 90)
        assertEquals("Duplicate faint and late old count cannot invalidate fresh content", faint, content())
        species("Hitmonlee", 100)
        assertTrue(content().revision > faint.revision)
        val beforeShield = content().revision
        DroidballOverlayPresentation.recordOpponentResources(
            OpponentBattleResourceCountMeasured(OpponentBattleResource.SHIELDS, 1, 2), 101)
        assertEquals(beforeShield + 1, content().revision)
        DroidballOverlayPresentation.reset()
        assertTrue(content().revision > beforeShield)
        assertEquals(Long.MIN_VALUE, content().changedAtNanos)
    }

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
