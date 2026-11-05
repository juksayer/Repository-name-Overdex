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
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeyboardPanelGeometryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitTypingKeepsTheCompactSideColumnsAndCanReachEnd() {
        val keyboard = TerminalKeyboardController()
        val contentInstances = AtomicInteger()
        val entered = mutableListOf<String>()
        compose.setContent {
            Box(Modifier.size(411.dp, 800.dp)) {
                ODXFiShell(showBattleOverlay = false, keyboardController = keyboard,
                    onDown = { keyboard.handleDown() },
                    onKeyActivated = entered::add,
                    keyboardPreviewOnCrt = "SEARCH: RAI") {
                    val selected = remember { mutableIntStateOf(8) }
                    remember { contentInstances.incrementAndGet() }
                    Text("SELECTED CARD ${selected.intValue}")
                }
            }
        }
        compose.onNodeWithText("A").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        val before = listOf("Up", "Down", "Left", "Right").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        compose.runOnIdle { keyboard.open() }
        compose.onAllNodesWithText("A").onLast()
            .assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        val after = listOf("Up", "Down", "Left", "Right").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        assertEquals("Opening the keyboard must not resize or move the console", before, after)
        after.zipWithNext().forEach { (upper, lower) ->
            assertEquals(upper.center.x, lower.center.x, 0.5f)
            assertTrue("Direction keys must stay in a vertical column", upper.bottom <= lower.top)
        }
        val actions = listOf("A", "B", "SELECT", "START").map {
            compose.onAllNodesWithText(it).onLast().fetchSemanticsNode().boundsInRoot
        }
        actions.zipWithNext().forEach { (upper, lower) ->
            assertEquals(upper.center.x, lower.center.x, 0.5f)
            assertTrue("Action keys must stay in a vertical column", upper.bottom <= lower.top)
        }
        val grid = compose.onNodeWithTag("Terminal key grid").fetchSemanticsNode().boundsInRoot
        assertTrue("The LCD stays between the columns", after.first().right <= grid.left)
        assertTrue("The LCD stays between the columns", grid.right <= actions.first().left)
        compose.onNodeWithText("SEARCH: RAI").assertIsDisplayed()
        compose.onNodeWithText("Q").assertHeightIsAtLeast(26.dp)
        // The compact strip leaves enough room for the bottom row immediately,
        // before navigation scrolls the grid to the selected utility key.
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed()
        compose.onNodeWithText("P").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("P"), entered) }
        repeat(3) {
            compose.onNodeWithContentDescription("Down").performTouchInput { click() }
        }
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle {
            assertTrue("The compact LCD must still allow finishing input", !keyboard.isVisible)
            assertEquals("Typing must retain the existing CRT content", 1, contentInstances.get())
        }
        compose.onNodeWithText("A").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithText("SELECTED CARD 8").assertIsDisplayed()
    }

    @Test fun rotatedBinderTypingKeepsTheCompactFrameAndSideControls() {
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
            .assertIsDisplayed().assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithText("Q").assertIsDisplayed()
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed()
        compose.onNodeWithText("SEARCH: RAI").assertIsDisplayed()
        val grid = compose.onNodeWithTag("Terminal key grid").fetchSemanticsNode().boundsInRoot
        val up = compose.onNodeWithContentDescription("Up").fetchSemanticsNode().boundsInRoot
        val action = compose.onAllNodesWithText("A").onLast().fetchSemanticsNode().boundsInRoot
        assertTrue("Rotated LCD pixels must remain between the physical columns", up.right <= grid.left)
        assertTrue("Rotated LCD pixels must remain between the physical columns", grid.right <= action.left)
    }
}
