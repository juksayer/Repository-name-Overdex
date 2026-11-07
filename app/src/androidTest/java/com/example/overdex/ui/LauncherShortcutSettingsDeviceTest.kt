package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.navigation.DesktopPointerController
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.LauncherShortcutConfiguration
import com.example.overdex.model.navigation.LauncherShortcutLocation
import com.example.overdex.model.navigation.OverdextopAction
import com.example.overdex.model.navigation.OverdextopCatalog
import com.example.overdex.model.navigation.OverdextopController
import com.example.overdex.model.navigation.OverdextopDirection
import com.example.overdex.model.navigation.OverdextopSurface
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.OverdextopCrt
import com.example.overdex.ui.components.OverdextopLcd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Visibility settings must remain reachable through the handheld's real controls. */
class LauncherShortcutSettingsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortcutCheckboxesCanBeReachedByButtonsAndChangedByATheLcdAndTheTrackpad() {
        val controller = OverdextopController()
        val pointer = DesktopPointerController()
        compose.setContent { Handheld(controller, pointer) }

        // Enter settings through the launcher, without bypassing the CRT shield.
        press("Down"); press("Down"); press("A")
        navigateListTo(controller, "settings")
        press("A")
        navigateListTo(controller, "shortcuts")
        press("A")
        assertEquals(OverdextopSurface.SHORTCUTS, controller.surface)
        navigateListTo(controller, "pokedex")
        press("Left")
        checkbox("pokedex", "desktop").assertIsOn()
        press("A")
        checkbox("pokedex", "desktop").assertIsOff()
        compose.onNodeWithTag("overdextop-lcd-shortcut-desktop").performTouchInput { click() }
        checkbox("pokedex", "desktop").assertIsOn()

        press("SELECT")
        compose.onNodeWithTag("overdextop-trackpad").assertIsDisplayed()
        movePointerToCheckbox(pointer, "pokedex", "programs")
        compose.waitUntil(timeoutMillis = 5_000) {
            controller.selectedPointerTargetId == "shortcut:pokedex:programs"
        }
        checkbox("pokedex", "programs").assertIsOn().performTouchInput { click() }
        checkbox("pokedex", "programs").assertIsOn()
        compose.onNodeWithTag("overdextop-trackpad").performTouchInput { click() }
        checkbox("pokedex", "programs").assertIsOff()
        checkbox("pokedex", "desktop").assertIsOn()
    }

    @Test fun eachDestinationCanAppearInBothLocationsEitherLocationOrNeither() {
        val controller = OverdextopController()
        compose.setContent { Handheld(controller) }
        fun checkLocations(desktop: Boolean, programs: Boolean) {
            compose.runOnIdle {
                controller.replaceShortcuts(controller.shortcuts
                    .withVisibility("pokedex", LauncherShortcutLocation.DESKTOP, desktop)
                    .withVisibility("pokedex", LauncherShortcutLocation.PROGRAMS, programs))
                controller.home()
            }
            assertEntryPresence("pokedex", desktop)
            compose.onNodeWithTag("overdextop-programs-button").performTouchInput { click() }
            assertEntryPresence("pokedex", programs)
        }
        checkLocations(desktop = true, programs = true)
        checkLocations(desktop = true, programs = false)
        checkLocations(desktop = false, programs = true)
        checkLocations(desktop = false, programs = false)
    }

    @Test fun manyDesktopPinsAndARaggedLastRowStayReachableAndFullyVisible() {
        val ids = OverdextopCatalog.destinations.map { it.id }.let {
            if (it.size % 3 == 0) it.dropLast(1) else it
        }
        assertTrue("Exercise several rows and a partial last row", ids.size > 6 && ids.size % 3 != 0)
        var config = LauncherShortcutConfiguration()
        OverdextopCatalog.destinations.forEach { entry ->
            config = config.withVisibility(entry.id, LauncherShortcutLocation.DESKTOP, entry.id in ids)
        }
        val controller = OverdextopController(config)
        val pointer = DesktopPointerController().apply { setEnabled(true) }
        compose.setContent { Handheld(controller, pointer) }

        repeat((ids.size - 1) / 3) { press("Down") }
        repeat((ids.size - 1) % 3) { press("Right") }
        val lastId = ids.last()
        val row = compose.onNodeWithTag("overdextop-entry:$lastId").assertIsSelected()
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val crt = compose.onNodeWithTag("overdextop-crt").getUnclippedBoundsInRoot()
        assertTrue("The complete final pin must fit inside the CRT after scrolling",
            row.left >= crt.left && row.right <= crt.right && row.top >= crt.top && row.bottom <= crt.bottom)
        compose.waitUntil(timeoutMillis = 5_000) { pointer.hoveredTargetId == lastId }
        press("Down")
        compose.onNodeWithTag("overdextop-entry:programs").assertIsSelected().assertIsDisplayed()
        press("Up")
        compose.onNodeWithTag("overdextop-entry:$lastId").assertIsSelected().assertIsDisplayed()
    }

    @Test fun hidingTheSelectedPinKeepsAValidSelectionAndCannotLaunchTheHiddenProgram() {
        val controller = OverdextopController().apply { check(select("pokedex")) }
        val launched = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, onAction = launched::add) }
        compose.runOnIdle {
            controller.replaceShortcuts(controller.shortcuts.withVisibility("pokedex", LauncherShortcutLocation.DESKTOP, false))
        }
        compose.onNodeWithTag("overdextop-entry:pokedex").assertDoesNotExist()
        compose.runOnIdle {
            assertNotEquals("pokedex", controller.selectedEntry.id)
            assertTrue(controller.selectedEntry in controller.entries)
        }
        compose.onNodeWithTag("overdextop-entry:${controller.selectedEntry.id}").assertIsSelected().assertIsDisplayed()
        val visibleAction = controller.selectedEntry.action
        assertTrue("A remaining visible program should still be executable", visibleAction is OverdextopAction.OpenProgram)
        press("A")
        compose.runOnIdle {
            assertEquals(listOf(visibleAction), launched)
            assertTrue("A removed desktop entry must not remain executable through stale selection",
                OverdextopAction.OpenProgram(InstrumentCommand.OpenSearch) !in launched)
        }
    }

    @Test fun hidingEveryOptionalShortcutStillAllowsSettingsAndRestoringAPin() {
        var config = LauncherShortcutConfiguration()
        OverdextopCatalog.destinations.forEach { entry ->
            config = config.withVisibility(entry.id, LauncherShortcutLocation.DESKTOP, false)
                .withVisibility(entry.id, LauncherShortcutLocation.PROGRAMS, false)
        }
        val controller = OverdextopController(config)
        compose.setContent { Handheld(controller) }
        compose.onNodeWithTag("overdextop-entry:programs").assertIsSelected().assertIsDisplayed()
        press("A")
        compose.onNodeWithTag("overdextop-entry:settings").assertIsSelected().assertIsDisplayed()
        press("A")
        navigateListTo(controller, "shortcuts")
        press("A")
        navigateListTo(controller, "pokedex")
        checkbox("pokedex", "desktop").assertIsOff()
        compose.onNodeWithTag("overdextop-lcd-shortcut-desktop").assertIsDisplayed().performTouchInput { click() }
        checkbox("pokedex", "desktop").assertIsOn()
        press("B"); press("B"); press("B")
        compose.onNodeWithTag("overdextop-desktop").assertIsDisplayed()
        compose.onNodeWithTag("overdextop-entry:pokedex").assertIsDisplayed()
    }

    @Test fun largeTextKeepsBothShortcutControlsInsideTheRealLcdIncludingLockedSettings() {
        val controller = OverdextopController().apply {
            openShortcuts()
            check(select("settings"))
            selectShortcutLocation(LauncherShortcutLocation.PROGRAMS)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Handheld(controller)
            }
        }
        val lcd = compose.onNodeWithTag("overdextop-lcd").getUnclippedBoundsInRoot()
        listOf("overdextop-input-mode", "overdextop-lcd-shortcut-desktop", "overdextop-lcd-shortcut-programs").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("The entire $tag control must fit inside the real LCD at large font size",
                bounds.left >= lcd.left && bounds.right <= lcd.right && bounds.top >= lcd.top && bounds.bottom <= lcd.bottom)
        }
        compose.onNodeWithTag("overdextop-lcd-shortcut-programs").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("overdextop-lcd-shortcut-desktop").assertIsEnabled().assertIsOff()
            .performTouchInput { click() }
        compose.onNodeWithTag("overdextop-lcd-shortcut-desktop").assertIsOn()
        compose.onNodeWithTag("overdextop-lcd-shortcut-programs").assertIsOn().assertIsNotEnabled()
    }

    private fun checkbox(id: String, location: String) = compose.onNodeWithTag("overdextop-shortcut:$id:$location")

    private fun assertEntryPresence(id: String, visible: Boolean) {
        val node = compose.onNodeWithTag("overdextop-entry:$id")
        if (visible) node.assertExists() else node.assertDoesNotExist()
    }

    private fun navigateListTo(controller: OverdextopController, id: String) {
        val target = controller.entries.indexOfFirst { it.id == id }
        assertTrue("The requested destination must remain reachable: $id", target >= 0)
        val delta = target - controller.selectedIndex
        repeat(kotlin.math.abs(delta)) { press(if (delta > 0) "Down" else "Up") }
        assertEquals(id, controller.selectedEntry.id)
    }

    private fun movePointerToCheckbox(pointer: DesktopPointerController, id: String, location: String) {
        val crt = compose.onNodeWithTag("overdextop-crt").fetchSemanticsNode().boundsInRoot
        val target = checkbox(id, location).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            pointer.moveBy((target.center.x - crt.left - pointer.pixelX) / pointer.viewportWidth,
                (target.center.y - crt.top - pointer.pixelY) / pointer.viewportHeight, 1f, 1f)
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Handheld(
        controller: OverdextopController,
        pointerController: DesktopPointerController? = null,
        onAction: (OverdextopAction) -> Unit = {},
    ) {
        val pointer = pointerController ?: remember { DesktopPointerController() }
        fun activate() {
            when (val action = controller.confirm()) {
                is OverdextopAction.ChangeShortcutLocation -> controller.replaceShortcuts(
                    controller.shortcuts.withVisibility(action.destinationId, action.location, action.visible))
                null -> Unit
                else -> onAction(action)
            }
        }
        fun move(direction: OverdextopDirection) {
            controller.move(direction)
            if (pointer.enabled) pointer.centerOnTarget(controller.selectedPointerTargetId)
        }
        fun togglePointer() { if (pointer.toggle()) pointer.centerOnTarget(controller.selectedPointerTargetId) }
        CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "launcher-shortcut-settings-test") {
            Box(Modifier.size(360.dp, 700.dp)) {
                ODXFiShell(
                    showBattleOverlay = false,
                    filterSettings = FilterSettings(isEnabled = false),
                    onUp = { move(OverdextopDirection.UP) }, onDown = { move(OverdextopDirection.DOWN) },
                    onLeft = { move(OverdextopDirection.LEFT) }, onRight = { move(OverdextopDirection.RIGHT) },
                    onA = ::activate, onB = { controller.back() }, onSelect = ::togglePointer,
                    lcdContent = {
                        OverdextopLcd(controller = controller, onOpen = ::activate,
                            onPrograms = { controller.openPrograms() }, onExplorer = { onAction(OverdextopAction.OpenExplorer) },
                            homeStyle = HomeScreenStyle.OVERDEXTOP, trackpadMode = pointer.enabled,
                            onToggleTrackpad = ::togglePointer,
                            onPointerDrag = { dx, dy, width, height -> pointer.moveBy(dx, dy, width, height) },
                            onPointerTap = { pointer.tap()?.let { if (controller.select(it)) activate() } })
                    },
                ) {
                    OverdextopCrt(controller = controller, pointerController = pointer,
                        onEntryActivated = { if (controller.select(it.id)) activate() })
                }
            }
        }
    }

    private fun press(key: String) {
        val node = if (key in listOf("Up", "Down", "Left", "Right")) compose.onNodeWithContentDescription(key)
            else compose.onNodeWithText(key)
        node.performTouchInput { click() }
        compose.waitForIdle()
    }
}
