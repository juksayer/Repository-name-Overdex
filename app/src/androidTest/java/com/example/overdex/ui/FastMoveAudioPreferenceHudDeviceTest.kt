package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.BattleOverlay
import org.junit.Rule
import org.junit.Test
import java.io.File

class FastMoveAudioPreferenceHudDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lowConfidenceLabelFitsWithoutReplacingAlternativesOrConfirmation() {
        val p = DroidballOverlayPresentation
        try {
            p.reset()
            p.recordOpponentSpecies("Dragonair", 148,
                listOf("Dragon Breath" to PokemonType.DRAGON, "Iron Tail" to PokemonType.STEEL), emptyList())
            p.recordOpponentFastMoveCandidates("Dragonair", listOf("Dragon Breath", "Iron Tail"), "Dragon Breath")
            p.recordBattleBadgePresent()
            p.showBattleHud()
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color(0xFF48827B))) { BattleOverlay(panelWidthPx = 415) }
            }
            compose.onNodeWithText("Dragon Breath\nFavored · low confidence").assertIsDisplayed()
            compose.onNodeWithText("Iron Tail").assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot().let { screenshot ->
                File(instrumentation.targetContext.filesDir, "audio-preference-hud.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            compose.runOnIdle {
                p.recordOpponentFastMove(FastMoveIdentified(ActivePokemonSide.OPPONENT, "Dragonair",
                    "Dragon Breath", 500_000_000L, null, 0, "AUDIO_REFERENCE"))
            }
            compose.onNodeWithText("✓ Dragon Breath").assertIsDisplayed()
            compose.onNodeWithText("Favored · low confidence", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Iron Tail").assertDoesNotExist()
        } finally { p.reset() }
    }
}
