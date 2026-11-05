package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.components.QwertyLayout
import com.example.overdex.ui.components.TerminalKeyboard
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.TokenLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises real pointer ownership, not only the offline path matcher. */
class TerminalSwipeKeyboardDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val words = listOf("PIKACHU", "PICHU", "RAICHU")
    private val emitted = mutableListOf<String>()

    private fun showKeyboard(
        layout: List<List<String>> = QwertyLayout,
        controller: TerminalKeyboardController? = null,
        keyboardModifier: Modifier = Modifier.size(360.dp, 280.dp),
    ) {
        compose.setContent {
            TerminalKeyboard(
                layout = controller?.layout ?: layout,
                currentRow = controller?.currentRow ?: 0,
                currentColumn = controller?.currentCol ?: 0,
                modifier = keyboardModifier,
                isFocused = controller?.isFocused ?: true,
                onKeyActivated = { key ->
                    if (controller == null) emitted += key else controller.activateKey(key, emitted::add)
                },
                swipeWords = words,
                inputRevision = controller?.inputRevision ?: 0,
            )
        }
        compose.waitForIdle()
        if (layout == QwertyLayout) {
            // The recognizer is prepared off the UI thread; an ordinary idle check
            // alone need not mean that gesture input has been installed yet.
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("Swipe suggestions") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Swipe typing ready"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Slide across letters · tap a name").assertDoesNotExist()
        }
    }

    private fun pathFor(letters: String): List<Offset> {
        val origin = compose.onNodeWithTag("Terminal key grid")
            .fetchSemanticsNode().boundsInRoot.topLeft
        return letters.map { letter ->
            compose.onNodeWithText(letter.toString(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center - origin
        }
    }

    private fun trace(letters: String, cancelAtEnd: Boolean = false) {
        val points = pathFor(letters)
        compose.onNodeWithTag("Terminal key grid").performTouchInput {
            down(points.first())
            for ((from, to) in points.zipWithNext()) {
                // Several real movement events per segment exercise touch-slop
                // ownership and avoid replacing a gesture with key-center jumps.
                for (step in 1..8) moveTo(from + (to - from) * (step / 8f))
            }
            if (cancelAtEnd) cancel() else up()
        }
    }

    private fun assertNoInsertionOrCandidate() {
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("A gesture inserted incidental keys: $emitted", emitted.isEmpty()) }
        for (word in words) {
            compose.onNodeWithContentDescription("Insert $word", useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithText("Finding names…").assertDoesNotExist()
    }

    @Test fun tappingOneLetterStillInsertsExactlyOneLetter() {
        showKeyboard()
        compose.onNodeWithText("P").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("P"), emitted) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun compactSuggestionChipDoesNotTakeTapsFromTheFirstLetterRow() {
        showKeyboard(keyboardModifier = Modifier.size(220.dp, 144.dp))
        trace("PIKACHU")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Q").performTouchInput { click(Offset(center.x, 2f)) }
        compose.runOnIdle { assertEquals(listOf("Q"), emitted) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun repeatedSwipesKeepKeysStillAndOnlyAnExplicitChoiceInsertsAName() {
        showKeyboard()
        fun letterBounds() = "QPAZM".map { letter ->
            compose.onNodeWithText(letter.toString(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        val originalBounds = letterBounds()
        trace("PIKACHU")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Showing candidates must not move the alphabet", originalBounds, letterBounds())
        // Start a new gesture while old candidates are visible. Clearing those
        // candidates must leave the measured letter positions unchanged.
        trace("PIKACHU")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("A second swipe must use the same key positions", originalBounds, letterBounds())
        compose.runOnIdle { assertTrue("Swipe emitted keys before choosing a name: $emitted", emitted.isEmpty()) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("PIKACHU"), emitted) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Choosing a name must not move the alphabet", originalBounds, letterBounds())
    }

    @Test fun cancelingANameGestureDoesNotInsertOrOfferItsPartialPath() {
        showKeyboard()
        trace("PIKACHU", cancelAtEnd = true)
        assertNoInsertionOrCandidate()
        // Cancellation must also leave the keyboard usable for a fresh tap.
        compose.onNodeWithText("S").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("S"), emitted) }
    }

    @Test fun instrumentTypingAndEndDiscardAnOlderSwipeSuggestion() {
        val keyboard = TerminalKeyboardController(keepVisibleAfterInput = true)
        keyboard.open()
        showKeyboard(controller = keyboard)
        trace("PIKACHU")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            // Q is the instrument's selected key. A must enter Q, not the old
            // swipe candidate, and that candidate must no longer be actionable.
            keyboard.handleA("", emitted::add)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("Q"), emitted) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true).assertDoesNotExist()

        trace("PIKACHU")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            keyboard.currentRow = QwertyLayout.lastIndex
            keyboard.currentCol = QwertyLayout.last().indexOf("END")
            keyboard.handleA("Q", emitted::add)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(keyboard.isVisible)
            assertTrue("END should return instrument navigation to the results", !keyboard.isFocused)
            assertEquals(listOf("Q"), emitted)
        }
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun addingASecondFingerCancelsTheNameGesture() {
        showKeyboard()
        val points = pathFor("PIKACHU")
        compose.onNodeWithTag("Terminal key grid").performTouchInput {
            down(pointerId = 0, position = points.first())
            moveTo(pointerId = 0, position = points[1])
            down(pointerId = 1, position = points[2])
            for (point in points.drop(2)) moveTo(pointerId = 0, position = point)
            up(pointerId = 1)
            up(pointerId = 0)
        }
        assertNoInsertionOrCandidate()
    }

    @Test fun anUnmatchedPathDoesNotBecomeAnUnrelatedNameOrKey() {
        showKeyboard()
        trace("QAZ")
        // Wait for the actual completed result rather than racing a comparison
        // that has not yet published its busy state.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("No name matched · tap to type").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoInsertionOrCandidate()
    }

    @Test fun tokenAndNumericGridsKeepTheirOrdinaryTapBehavior() {
        showKeyboard(TokenLayout)
        compose.onNodeWithTag("Swipe suggestions").assertDoesNotExist()
        compose.onNodeWithText("1").performTouchInput { click() }
        compose.onNodeWithText(",").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("1", ","), emitted) }
    }

    @Test fun rotatedKeyboardRecognizesTheSameWorldCoordinateGesture() {
        compose.setContent {
            Box(
                Modifier.size(420.dp).testTag("Rotated swipe test surface"),
                contentAlignment = Alignment.Center,
            ) {
                TerminalKeyboard(
                    layout = QwertyLayout,
                    currentRow = 0,
                    currentColumn = 0,
                    modifier = Modifier.size(360.dp, 280.dp).graphicsLayer { rotationZ = 90f },
                    onKeyActivated = emitted::add,
                    swipeWords = words,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("Swipe suggestions") and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Swipe typing ready"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Inject through an unrotated parent. These measured world coordinates
        // exercise Compose's real pointer transformation into the rotated LCD.
        val surface = compose.onNodeWithTag("Rotated swipe test surface")
        val origin = surface.fetchSemanticsNode().boundsInRoot.topLeft
        val points = "PIKACHU".map { letter ->
            compose.onNodeWithText(letter.toString(), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center - origin
        }
        surface.performTouchInput {
            down(points.first())
            for ((from, to) in points.zipWithNext()) {
                for (step in 1..8) moveTo(from + (to - from) * (step / 8f))
            }
            up()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertTrue("Rotated swipe inserted incidental keys: $emitted", emitted.isEmpty()) }
        compose.onNodeWithContentDescription("Insert PIKACHU", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("PIKACHU"), emitted) }
    }
}
