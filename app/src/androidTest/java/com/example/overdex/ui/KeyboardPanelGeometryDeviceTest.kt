package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.TerminalKeyboardController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeyboardPanelGeometryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitTypingUsesFullWidthAndReturnsToBrowsingGeometry() {
        val keyboard = TerminalKeyboardController()
        compose.setContent {
            Box(Modifier.size(411.dp, 800.dp)) {
                ODXFiShell(showBattleOverlay = false, keyboardController = keyboard,
                    keyboardPreviewOnCrt = "SEARCH: RAI") {
                    val selected = remember { mutableIntStateOf(8) }
                    Text("SELECTED CARD ${selected.intValue}")
                }
            }
        }
        compose.onNodeWithText("A").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        compose.runOnIdle { keyboard.open() }
        compose.onAllNodesWithText("A").onLast()
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Q").assertIsDisplayed().assertWidthIsAtLeast(30.dp)
        val end = compose.onNodeWithContentDescription("End typing").fetchSemanticsNode().boundsInRoot
        val actions = compose.onAllNodesWithText("A").onLast().fetchSemanticsNode().boundsInRoot
        assertTrue("The full-width LCD must stay above the relocated controls", end.bottom <= actions.top)
        compose.onNodeWithText("SEARCH: RAI").assertIsDisplayed()
        compose.runOnIdle { keyboard.handleB() }
        compose.onNodeWithText("A").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithText("SELECTED CARD 8").assertIsDisplayed()
    }

    @Test fun rotatedBinderTypingHasWideKeysAndReachableSideControls() {
        val keyboard = TerminalKeyboardController()
        keyboard.open()
        compose.setContent {
            Box(Modifier.size(411.dp, 800.dp)) {
                ODXFiShell(showBattleOverlay = false, isBinderMode = true,
                    keyboardController = keyboard, keyboardPreviewOnCrt = "SEARCH: RAI") {
                    Text("BINDER")
                }
            }
        }
        compose.onAllNodesWithText("A").onLast()
            .assertIsDisplayed().assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        // After the quarter-turn, a key's logical width is its physical height.
        compose.onNodeWithText("Q").assertIsDisplayed().assertHeightIsAtLeast(30.dp)
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed()
        compose.onNodeWithText("SEARCH: RAI").assertIsDisplayed()
    }
}
