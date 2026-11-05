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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeyboardPanelGeometryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitTypingKeepsWideKeysAboveDirectionalAndActionClusters() {
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
        val up = compose.onNodeWithContentDescription("Up").fetchSemanticsNode().boundsInRoot
        val down = compose.onNodeWithContentDescription("Down").fetchSemanticsNode().boundsInRoot
        val left = compose.onNodeWithContentDescription("Left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription("Right").fetchSemanticsNode().boundsInRoot
        val select = compose.onNodeWithText("SELECT").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithText("START").fetchSemanticsNode().boundsInRoot
        val b = compose.onAllNodesWithText("B").onLast().fetchSemanticsNode().boundsInRoot
        assertTrue("The full-width LCD must stay above every instrument control", end.bottom <= up.top)
        assertTrue("Up and Down share the middle of the directional cross", kotlin.math.abs(up.center.x - down.center.x) < 1f)
        assertTrue("Up is above Left/Right", up.bottom <= left.top && up.bottom <= right.top)
        assertTrue("Down is below Left/Right", down.top >= left.bottom && down.top >= right.bottom)
        assertTrue("Left/Right share a row", kotlin.math.abs(left.center.y - right.center.y) < 1f)
        assertTrue("Directions stay left of the action cross", right.right <= select.left)
        assertTrue("Start and A share the middle of the action cross", kotlin.math.abs(start.center.x - actions.center.x) < 1f)
        assertTrue("Start is above Select/B", start.bottom <= select.top && start.bottom <= b.top)
        assertTrue("A is below Select/B", actions.top >= select.bottom && actions.top >= b.bottom)
        assertTrue("Select/B share a row", kotlin.math.abs(select.center.y - b.center.y) < 1f)
        assertTrue("Select is left of B", select.right <= b.left)
        assertTrue("The LCD is above the action cross", end.bottom <= start.top)
        compose.onNodeWithText("SEARCH: RAI").assertIsDisplayed()
        compose.runOnIdle { keyboard.handleB() }
        compose.onNodeWithText("A").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithText("SELECTED CARD 8").assertIsDisplayed()
    }

    @Test fun rearrangedControlsKeepTheirDirectionAndContextualAction() {
        val keyboard = TerminalKeyboardController().apply { open() }
        val actions = mutableListOf<String>()
        compose.setContent {
            Box(Modifier.size(411.dp, 800.dp)) {
                ODXFiShell(
                    showBattleOverlay = false,
                    keyboardController = keyboard,
                    onUp = { actions += "up" },
                    onDown = { actions += "down" },
                    onLeft = { actions += "left" },
                    onRight = { actions += "right" },
                    onA = { actions += "a" },
                    onB = { actions += "b" },
                    onSelect = { actions += "select" },
                    onStart = { actions += "done" },
                    startLabel = "DONE",
                ) { Text("EDITING") }
            }
        }
        listOf("Up", "Down", "Left", "Right").forEach {
            compose.onNodeWithContentDescription(it).performTouchInput { click() }
        }
        compose.onAllNodesWithText("A").onLast().performTouchInput { click() }
        compose.onAllNodesWithText("B").onLast().performTouchInput { click() }
        compose.onNodeWithText("SELECT").performTouchInput { click() }
        compose.onNodeWithText("DONE").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("up", "down", "left", "right", "a", "b", "select", "done"), actions)
        }
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
