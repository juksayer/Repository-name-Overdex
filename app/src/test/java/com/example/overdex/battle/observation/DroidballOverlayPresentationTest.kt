package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballOverlayPresentationTest {

    @Test
    fun `maps session phases to their matching overlay layouts`() {
        DroidballOverlayPresentation.reset()
        DroidballOverlayPresentation.showSessionPhase(DroidballSessionPhase.ARMED)
        assertEquals(DroidballOverlayMode.PRE_BATTLE, DroidballOverlayPresentation.mode.value)

        DroidballOverlayPresentation.showSessionPhase(DroidballSessionPhase.COUNTDOWN)
        assertEquals(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayPresentation.mode.value)

        DroidballOverlayPresentation.showSessionPhase(DroidballSessionPhase.BATTLE_ACTIVE)
        assertEquals(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayPresentation.mode.value)

        DroidballOverlayPresentation.showSessionPhase(DroidballSessionPhase.RESULT)
        assertEquals(DroidballOverlayMode.RESULT, DroidballOverlayPresentation.mode.value)
    }

    @Test
    fun `stale armed phase cannot close a HUD opened by battle evidence`() {
        DroidballOverlayPresentation.reset()
        DroidballOverlayPresentation.showBattleHud()

        DroidballOverlayPresentation.showSessionPhase(DroidballSessionPhase.ARMED)

        assertEquals(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayPresentation.mode.value)
    }

    @Test
    fun `keeps a species ID with the opponent evidence for offline sprite rendering`() {
        DroidballOverlayPresentation.clearOpponentSpecies()
        DroidballOverlayPresentation.recordOpponentSpecies(
            speciesName = "Turtonator",
            speciesId = 776,
            possibleFastMoves = emptyList(),
            possibleChargedMoves = emptyList()
        )

        assertEquals(listOf(ObservedOpponentSpecies("Turtonator", 776)), DroidballOverlayPresentation.opponentSpecies.value)
    }
}
