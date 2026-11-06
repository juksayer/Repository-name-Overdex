package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.OwnedPokemonBinder
import com.example.overdex.model.navigation.DesktopPointerController
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.OverdextopAction
import com.example.overdex.model.navigation.OverdextopController
import com.example.overdex.model.navigation.OverdextopDirection
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.OverdextopCrt
import com.example.overdex.ui.components.OverdextopLcd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise actual LCD pointer gestures through the existing protected handheld shell. */
class DesktopTrackpadDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingInputModesKeepsTheHardwareFixedAndPhysicalNavigationStillWorks() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController()
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, pointer, actions::add) }
        val hardware = hardwareBounds()
        compose.onNodeWithTag("overdextop-trackpad").assertDoesNotExist()
        compose.onNodeWithTag("overdextop-pointer").assertDoesNotExist()

        compose.onNodeWithTag("overdextop-input-mode").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("overdextop-trackpad").assertIsDisplayed()
        compose.onNodeWithTag("overdextop-pointer").assertExists()
        compose.runOnIdle {
            assertTrue(pointer.enabled)
            check(controller.select("droidball"))
        }
        assertEquals("Changing input must not move the handheld hardware", hardware, hardwareBounds())
        press("Down")
        compose.onNodeWithTag("overdextop-entry:binders").assertIsSelected()
        press("A")
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.ALL))), actions)
        }

        press("SELECT")
        compose.onNodeWithTag("overdextop-trackpad").assertDoesNotExist()
        compose.onNodeWithTag("overdextop-pointer").assertDoesNotExist()
        compose.onNodeWithTag("overdextop-open").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!pointer.enabled) }
        assertEquals(hardware, hardwareBounds())
    }

    @Test fun lcdMotionIsRelativeAcrossFingerContactsAndDragReleaseNeverActivates() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, pointer, actions::add) }
        compose.onNodeWithTag("overdextop-trackpad").assertIsDisplayed()
        compose.runOnIdle {
            // Begin on empty CRT heading space so a stationary tap cannot open a program.
            pointer.moveBy(0.15f - pointer.x, 0.02f - pointer.y, 1f, 1f)
        }
        val before = pointer.x to pointer.y
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput {
            click(Offset(width * 0.85f, height * 0.85f))
        }
        compose.runOnIdle {
            assertEquals("A contact's absolute location must not warp the pointer", before, pointer.x to pointer.y)
            assertTrue(actions.isEmpty())
        }

        compose.onNodeWithTag("overdextop-trackpad").performTouchInput {
            swipe(
                start = Offset(width * 0.1f, height * 0.1f),
                end = Offset(width * 0.35f, height * 0.35f),
                durationMillis = 240,
            )
        }
        val afterFirst = pointer.x to pointer.y
        compose.runOnIdle {
            assertTrue("A rightward LCD drag must move the CRT pointer right", pointer.x > before.first)
            assertTrue("A downward LCD drag must move the CRT pointer down", pointer.y > before.second)
            assertTrue("Releasing a drag must not activate the hovered program", actions.isEmpty())
        }
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput {
            swipe(
                start = Offset(width * 0.65f, height * 0.15f),
                end = Offset(width * 0.8f, height * 0.3f),
                durationMillis = 240,
            )
        }
        compose.runOnIdle {
            assertTrue("A new finger contact must continue relative to the previous pointer position", pointer.x > afterFirst.first)
            assertTrue(pointer.y > afterFirst.second)
            assertTrue("Neither drag may turn its release into a tap", actions.isEmpty())
        }
    }

    @Test fun measuredCrtTargetCanBeHoveredAndOpenedFromTheLcdWhileDirectCrtTouchStaysBlocked() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, pointer, actions::add) }
        movePointerToMeasuredTarget(pointer, "current_team")
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "current_team" }
        compose.onNodeWithTag("overdextop-entry:current_team").assertIsSelected()
        compose.runOnIdle {
            assertEquals("The target must come from actual rendered entry geometry", "current_team", pointer.hoveredTargetId)
            assertTrue("Hovering must not activate a program", actions.isEmpty())
        }

        compose.onNodeWithTag("overdextop-entry:pokedex").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("GlassShield still owns CRT input in pointer mode", "current_team", controller.selectedEntry.id)
            assertTrue(actions.isEmpty())
        }
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenCurrentTeam)), actions)
        }
    }

    @Test fun anActualLcdDragCanHoverAProgramAndOnlyASubsequentTapOpensIt() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, pointer, actions::add) }
        movePointerToMeasuredTarget(pointer, "droidball")
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "droidball" }

        val crt = compose.onNodeWithTag("overdextop-crt").fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithTag("overdextop-entry:droidball").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("overdextop-entry:current_team").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput {
            // Split a long relative movement into contacts without crossing the pad edge.
            val delta = Offset((target.center.x - source.center.x) / crt.width * width,
                (target.center.y - source.center.y) / crt.height * height)
            repeat(2) {
                swipe(start = Offset(width * 0.1f, height * 0.5f),
                    end = Offset(width * 0.1f, height * 0.5f) + delta / 2f,
                    durationMillis = 240)
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "current_team" }
        compose.runOnIdle {
            assertEquals("current_team", pointer.hoveredTargetId)
            assertTrue("The drag releases must leave the selected program unopened", actions.isEmpty())
        }
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenCurrentTeam)), actions)
        }
    }

    @Test fun aPhysicalMenuSelectionScrollsIntoViewAndCentersThePointerOnTheRevealedRow() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        compose.setContent { Handheld(controller, pointer, {}) }

        compose.onNodeWithTag("overdextop-programs-button").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("overdextop-programs").assertIsDisplayed()
        repeat(controller.entries.lastIndex) { press("Down") }
        val id = controller.entries.last().id
        compose.onNodeWithTag("overdextop-entry:$id").assertIsSelected().assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) { pointer.hoveredTargetId == id }
        val crt = compose.onNodeWithTag("overdextop-crt").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("overdextop-entry:$id").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertEquals("The pointer must use the final visible row geometry", target.center.x - crt.left, pointer.pixelX, 1f)
            assertEquals("Deferred centering must follow scrolling", target.center.y - crt.top, pointer.pixelY, 1f)
        }
        press("B")
        compose.onNodeWithTag("overdextop-desktop").assertIsDisplayed()
        compose.onNodeWithTag("overdextop-trackpad").assertIsDisplayed()
    }

    @Test fun togglingOrResizingDoesNotTurnAnOldHoverIntoANewSelection() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        val size = mutableStateOf(360.dp to 540.dp)
        compose.setContent {
            Box(Modifier.size(size.value.first, size.value.second)) {
                OverdextopCrt(controller = controller, pointerController = pointer, onEntryActivated = {})
            }
        }
        movePointerToMeasuredTarget(pointer, "pokedex")
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "pokedex" }
        compose.runOnIdle {
            // Only a new pointer motion may select. Layout and visibility are not motion.
            check(controller.select("current_team"))
            pointer.setEnabled(false)
        }
        compose.onNodeWithTag("overdextop-pointer").assertDoesNotExist()
        compose.runOnIdle { pointer.setEnabled(true) }
        compose.onNodeWithTag("overdextop-pointer").assertExists()
        compose.onNodeWithTag("overdextop-entry:current_team").assertIsSelected()
        compose.runOnIdle { size.value = 320.dp to 480.dp }
        compose.waitForIdle()
        compose.onNodeWithTag("overdextop-entry:current_team").assertIsSelected()
        compose.runOnIdle { assertEquals("current_team", controller.selectedEntry.id) }
    }

    @Test fun returningToTheCrtOrReattachingThePointerDoesNotReplayItsOldMotion() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        val showCrt = mutableStateOf(true)
        val attachPointer = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(360.dp, 540.dp)) {
                if (showCrt.value) {
                    OverdextopCrt(
                        controller = controller,
                        pointerController = if (attachPointer.value) pointer else null,
                        onEntryActivated = {},
                    )
                } else Text("another module")
            }
        }
        movePointerToMeasuredTarget(pointer, "pokedex")
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "pokedex" }
        val oldMotion = pointer.motionRevision
        assertTrue("The return must exercise a previously used pointer", oldMotion > 0)

        compose.runOnIdle { showCrt.value = false }
        compose.onNodeWithTag("overdextop-crt").assertDoesNotExist()
        compose.onNodeWithText("another module").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Leaving the CRT must release its old click targets", null, pointer.tap())
            check(controller.select("current_team"))
            showCrt.value = true
        }
        compose.onNodeWithTag("overdextop-entry:current_team").assertIsSelected().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Recreating the CRT must retain the selection made while away", "current_team", controller.selectedEntry.id)
            assertEquals("Returning is not pointer motion", oldMotion, pointer.motionRevision)
        }

        // Reattach to an existing CRT as well as recreating the whole module.
        compose.runOnIdle { attachPointer.value = false }
        compose.onNodeWithTag("overdextop-pointer").assertDoesNotExist()
        compose.runOnIdle {
            check(controller.select("binders"))
            attachPointer.value = true
        }
        compose.onNodeWithTag("overdextop-entry:binders").assertIsSelected().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Attaching an old pointer must not consume its historical motion again", "binders", controller.selectedEntry.id)
            assertEquals(oldMotion, pointer.motionRevision)
        }
        movePointerToMeasuredTarget(pointer, "pokedex")
        compose.waitUntil(timeoutMillis = 5_000) { controller.selectedEntry.id == "pokedex" }
        compose.runOnIdle {
            assertTrue("Fresh pointer motion after reattachment must still select", pointer.motionRevision > oldMotion)
        }
    }

    private fun movePointerToMeasuredTarget(pointer: DesktopPointerController, id: String) {
        val crt = compose.onNodeWithTag("overdextop-crt").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("overdextop-entry:$id").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            pointer.moveBy(
                deltaXPx = (target.center.x - crt.left - pointer.pixelX) / pointer.viewportWidth,
                deltaYPx = (target.center.y - crt.top - pointer.pixelY) / pointer.viewportHeight,
                lcdWidthPx = 1f,
                lcdHeightPx = 1f,
            )
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Handheld(
        controller: OverdextopController,
        pointer: DesktopPointerController,
        onAction: (OverdextopAction) -> Unit,
    ) {
        fun openSelected() { controller.confirm()?.let(onAction) }
        fun moveSelection(direction: OverdextopDirection) {
            controller.move(direction)
            if (pointer.enabled) pointer.centerOnTarget(controller.selectedEntry.id)
        }
        fun toggleInput() {
            if (pointer.toggle()) pointer.centerOnTarget(controller.selectedEntry.id)
        }
        CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "desktop-trackpad-test") {
            Box(Modifier.size(360.dp, 700.dp)) {
                ODXFiShell(
                    showBattleOverlay = false,
                    filterSettings = FilterSettings(isEnabled = false),
                    onUp = { moveSelection(OverdextopDirection.UP) },
                    onDown = { moveSelection(OverdextopDirection.DOWN) },
                    onLeft = { moveSelection(OverdextopDirection.LEFT) },
                    onRight = { moveSelection(OverdextopDirection.RIGHT) },
                    onA = ::openSelected,
                    onB = { controller.back() },
                    onSelect = ::toggleInput,
                    lcdContent = {
                        OverdextopLcd(
                            controller = controller,
                            onOpen = ::openSelected,
                            onPrograms = { controller.openPrograms() },
                            onExplorer = { onAction(OverdextopAction.OpenExplorer) },
                            homeStyle = HomeScreenStyle.OVERDEXTOP,
                            trackpadMode = pointer.enabled,
                            onToggleTrackpad = ::toggleInput,
                            onPointerDrag = { dx, dy, width, height -> pointer.moveBy(dx, dy, width, height) },
                            onPointerTap = {
                                pointer.tap()?.let { id -> if (controller.select(id)) openSelected() }
                            },
                        )
                    },
                ) {
                    OverdextopCrt(controller = controller, pointerController = pointer, onEntryActivated = {
                        controller.select(it.id)
                        openSelected()
                    })
                }
            }
        }
    }

    private fun hardwareBounds() = buildMap {
        put("Droidball", compose.onNodeWithContentDescription("Droidball").getUnclippedBoundsInRoot())
        put("CRT", compose.onNodeWithTag("overdextop-crt").getUnclippedBoundsInRoot())
        put("LCD", compose.onNodeWithTag("overdextop-lcd").getUnclippedBoundsInRoot())
        listOf("PWR", "OBS", "LINK", "A", "B", "SELECT", "START").forEach {
            put(it, compose.onNodeWithText(it).getUnclippedBoundsInRoot())
        }
        listOf("Up", "Down", "Left", "Right").forEach {
            put(it, compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot())
        }
    }

    private fun press(key: String) {
        val target = if (key in listOf("Up", "Down", "Left", "Right")) compose.onNodeWithContentDescription(key)
            else compose.onNodeWithText(key)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }
}
