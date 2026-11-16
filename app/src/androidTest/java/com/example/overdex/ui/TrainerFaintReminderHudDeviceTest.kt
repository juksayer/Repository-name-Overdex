package com.example.overdex.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.ui.components.BattleOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainerFaintReminderHudDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun spriteTapTogglesReminderWithoutCreatingAnAutomaticFaint() {
        val hud = DroidballOverlayPresentation
        try {
            hud.reset()
            hud.beginEnergyForecast("test-match")
            hud.recordOpponentSpecies("Dragonair", 148, emptyList(), emptyList())
            hud.recordBattleBadgePresent()
            hud.showBattleHud(userRequested = true)
            compose.setContent { Box(Modifier.fillMaxSize()) { BattleOverlay(panelWidthPx = 415) } }

            compose.onNodeWithContentDescription("Dragonair sprite").performClick()
            compose.runOnIdle {
                assertEquals(1, hud.trainerReportedFaints.value.size)
                assertFalse(hud.opponentSpecies.value.single().isFainted)
            }
            compose.onNodeWithContentDescription("Dragonair sprite").performClick()
            compose.runOnIdle { assertTrue(hud.trainerReportedFaints.value.isEmpty()) }
        } finally { hud.reset() }
    }
}
