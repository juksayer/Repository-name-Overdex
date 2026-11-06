package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.OverdextopAction
import com.example.overdex.model.navigation.OverdextopController
import com.example.overdex.model.navigation.OverdextopDirection
import com.example.overdex.model.navigation.OverdextopSurface
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.OverdextopCrt
import com.example.overdex.ui.components.OverdextopLcd
import com.example.overdex.ui.screens.MainMenuPhase
import com.example.overdex.ui.screens.MainMenuScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** The alternate launcher must still behave as a handheld with a protected CRT. */
class OverdextopDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstBootClearsBeforeShortcutsRevealInOrderAndReturningDoesNotReplayBoot() {
        compose.mainClock.autoAdvance = false
        val controller = OverdextopController()
        val expectedIds = controller.entries.map { it.id }
        val booted = mutableStateOf(false)
        val showingHome = mutableStateOf(true)
        val homeInstance = mutableStateOf(0)
        val completed = AtomicInteger()
        val phase = AtomicReference(MainMenuPhase.BOOT)
        val controlsReady = AtomicBoolean(false)
        val trainer = TrainerIdentity(
            displayName = "Desktop Trainer",
            trainerId = UUID.fromString("12345678-1234-1234-1234-123456789abc"),
            createdAt = Instant.EPOCH,
        )
        compose.setContent {
            Box(Modifier.size(320.dp, 600.dp)) {
                if (showingHome.value) key(homeInstance.value) {
                    MainMenuScreen(
                        hasBootedInSession = booted.value,
                        onBootComplete = { completed.incrementAndGet(); booted.value = true },
                        trainerIdentity = trainer,
                        onPhaseChange = phase::set,
                        revealEntryCount = controller.entries.size,
                        homeContent = { count, ready ->
                            SideEffect { controlsReady.set(ready) }
                            OverdextopCrt(controller = controller, revealCount = count, onEntryActivated = {})
                        },
                    )
                } else Text("another program")
            }
        }

        advanceUntil("completed boot text") { textExists("TRAINER: DESKTOP TRAINER") }
        compose.onNodeWithText("overdex boot sequence...").assertIsDisplayed()
        compose.onNodeWithText("overdex ready").assertIsDisplayed()
        compose.onNodeWithText("TRAINER: DESKTOP TRAINER").assertIsDisplayed()
        assertTrue(shownShortcutIds().isEmpty())
        assertEquals(0, completed.get())
        assertTrue("Home controls must remain disabled during boot", !controlsReady.get())

        advanceUntil("desktop reveal") { phase.get() == MainMenuPhase.MENU_BUILD }
        assertBootTextAbsent()
        val prefixes = linkedSetOf<Int>()
        var previousCount = 0
        advanceUntil("interactive desktop") {
            val shown = shownShortcutIds()
            assertEquals("Only an ordered prefix may be drawn", expectedIds.take(shown.size), shown)
            assertTrue("Revealed shortcuts must not disappear", shown.size >= previousCount)
            previousCount = shown.size
            if (shown.isNotEmpty() && shown.size < expectedIds.size) prefixes += shown.size
            if ("programs" in shown) {
                assertEquals("Programs must appear after all desktop shortcuts", expectedIds, shown)
            }
            assertBootTextAbsent()
            if (shown.size < expectedIds.size) {
                assertEquals("Boot completion must wait for every shortcut", 0, completed.get())
                assertTrue("Partially drawn desktops must not enable controls", !controlsReady.get())
            }
            phase.get() == MainMenuPhase.READY && completed.get() == 1 && controlsReady.get()
        }
        assertTrue("The desktop must be drawn in several distinct steps", prefixes.size >= 2)
        assertEquals(expectedIds, shownShortcutIds())

        // Dispose the first home composition, then recreate it as actual program return does.
        compose.runOnIdle { showingHome.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("another program").assertIsDisplayed()
        compose.runOnIdle { homeInstance.value++; showingHome.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(MainMenuPhase.READY, phase.get())
        assertEquals(expectedIds, shownShortcutIds())
        assertBootTextAbsent()
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        assertEquals("Returning home must not complete boot again", 1, completed.get())
    }

    @Test fun directionButtonsSelectDesktopShortcutsAndAOpensTheExistingProgram() {
        val controller = OverdextopController()
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, actions::add) }

        selected("droidball")
        press("Down")
        selected("binders")
        press("Right")
        selected("match_history")
        press("Up")
        selected("pokedex")
        press("Right")
        selected("current_team")
        press("Left")
        selected("pokedex")
        press("Right")
        press("A")

        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenCurrentTeam)), actions)
        }
    }

    @Test fun programsMenuUsesDirectionButtonsAndBReturnsToTheDesktopSelection() {
        val controller = OverdextopController()
        check(controller.select("pokedex"))
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, actions::add) }

        compose.onNodeWithTag("overdextop-programs-button").performTouchInput { click() }
        compose.onNodeWithTag("overdextop-programs").assertIsDisplayed()
        val target = controller.entries.indexOfFirst { it.id == "match_history" }
        assertTrue("Match history must be available from programs", target >= 0)
        repeat(target) { press("Down") }
        selected("match_history")
        press("A")
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenBattleHistory)), actions)
        }
        press("B")
        compose.onNodeWithTag("overdextop-desktop").assertIsDisplayed()
        compose.onNodeWithTag("overdextop-programs").assertDoesNotExist()
        selected("pokedex")
    }

    @Test fun explorerHasItsOwnActionAndDoesNotLaunchABattleProgram() {
        val controller = OverdextopController()
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, actions::add) }

        compose.onNodeWithTag("overdextop-explorer-button").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(OverdextopAction.OpenExplorer), actions) }
        compose.runOnIdle { check(controller.select("explorer")) }
        press("A")
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenExplorer, OverdextopAction.OpenExplorer), actions)
            assertEquals(OverdextopSurface.DESKTOP, controller.surface)
        }
    }

    @Test fun glassShieldBlocksDesktopTouchesWhileTheLcdOpenControlRemainsUsable() {
        val controller = OverdextopController()
        check(controller.select("current_team"))
        val actions = mutableListOf<OverdextopAction>()
        compose.setContent { Handheld(controller, actions::add) }

        // Pointer injection is deliberate: a semantics click would bypass GlassShield.
        compose.onNodeWithTag("overdextop-entry:pokedex").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("CRT touches must not alter selection", "current_team", controller.selectedEntry.id)
            assertTrue("CRT touches must not launch programs", actions.isEmpty())
        }
        compose.onNodeWithTag("overdextop-open").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(OverdextopAction.OpenProgram(InstrumentCommand.OpenCurrentTeam)), actions)
        }
    }

    @Test fun changingTheCrtContentDoesNotMoveDroidballTheLightsOrThePhysicalControls() {
        val showDesktop = mutableStateOf(false)
        val controller = OverdextopController()
        compose.setContent { Handheld(controller, {}, showDesktop.value) }
        val before = hardwareBounds()
        compose.runOnIdle { showDesktop.value = true }
        compose.onNodeWithTag("overdextop-desktop").assertIsDisplayed()
        assertEquals("The desktop belongs inside the existing handheld shell", before, hardwareBounds())
        compose.onNodeWithText("oDX-Fi").assertIsDisplayed()
    }

    @Test fun theLastProgramsEntryStaysFullyVisibleOnASmallCrtWithLargeText() {
        val controller = OverdextopController().apply { openPrograms() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Box(Modifier.size(280.dp, 260.dp).testTag("overdextop-viewport")) {
                    OverdextopCrt(controller = controller, onEntryActivated = {})
                }
            }
        }
        compose.runOnIdle {
            repeat(controller.entries.lastIndex) { controller.move(OverdextopDirection.DOWN) }
        }
        compose.waitForIdle()
        val last = controller.entries.last()
        val row = compose.onNodeWithTag("overdextop-entry:${last.id}")
            .assertIsSelected().assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("overdextop-viewport").getUnclippedBoundsInRoot()
        assertTrue("The selected menu entry must stay completely inside the CRT",
            row.top >= viewport.top && row.bottom <= viewport.bottom &&
                row.left >= viewport.left && row.right <= viewport.right)
        compose.onNodeWithText(last.title).assertIsDisplayed()
    }

    @Test fun aSmallLargeTextLcdKeepsAllActionsVisibleWhileItsDescriptionScrolls() {
        val controller = OverdextopController().apply {
            openSettings()
            check(select("home_overdextop"))
        }
        val actions = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Box(Modifier.size(280.dp, 140.dp).testTag("overdextop-lcd-viewport").padding(horizontal = 32.dp)) {
                    OverdextopLcd(
                        controller = controller,
                        onOpen = { actions += "open" },
                        onPrograms = { actions += "programs" },
                        onExplorer = { actions += "explorer" },
                        homeStyle = HomeScreenStyle.OVERDEXTOP,
                    )
                }
            }
        }
        val tags = listOf("overdextop-open", "overdextop-programs-button", "overdextop-explorer-button")
        fun buttonBounds() = tags.map { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        val before = buttonBounds()
        val viewport = compose.onNodeWithTag("overdextop-lcd-viewport").getUnclippedBoundsInRoot()
        before.forEach {
            assertTrue("Each action must remain completely inside the LCD",
                it.left >= viewport.left && it.right <= viewport.right &&
                    it.top >= viewport.top && it.bottom <= viewport.bottom)
        }
        val summary = compose.onNodeWithTag("overdextop-lcd-summary")
        assertTrue("The large-text summary must exercise an actual scrolling viewport",
            summary.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        summary.performTouchInput { swipeUp() }
        assertEquals("Scrolling the description must not move the action row", before, buttonBounds())
        tags.forEach { compose.onNodeWithTag(it).performTouchInput { click() } }
        compose.runOnIdle { assertEquals(listOf("open", "programs", "explorer"), actions) }
    }

    @Composable
    private fun Handheld(
        controller: OverdextopController,
        onAction: (OverdextopAction) -> Unit,
        showDesktop: Boolean = true,
    ) {
        fun openSelected() { controller.confirm()?.let(onAction) }
        CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "overdextop-test") {
            Box(Modifier.size(360.dp, 700.dp)) {
                ODXFiShell(
                    showBattleOverlay = false,
                    filterSettings = FilterSettings(isEnabled = false),
                    onUp = { controller.move(OverdextopDirection.UP) },
                    onDown = { controller.move(OverdextopDirection.DOWN) },
                    onLeft = { controller.move(OverdextopDirection.LEFT) },
                    onRight = { controller.move(OverdextopDirection.RIGHT) },
                    onA = ::openSelected,
                    onB = { controller.back() },
                    onSelect = { controller.openPrograms() },
                    lcdContent = {
                        if (showDesktop) {
                            OverdextopLcd(
                                controller = controller,
                                onOpen = ::openSelected,
                                onPrograms = { controller.openPrograms() },
                                onExplorer = { onAction(OverdextopAction.OpenExplorer) },
                                homeStyle = HomeScreenStyle.OVERDEXTOP,
                            )
                        } else Text("directory preview")
                    },
                ) {
                    if (showDesktop) {
                        OverdextopCrt(controller = controller, onEntryActivated = {
                            controller.select(it.id)
                            openSelected()
                        })
                    } else Text("directory tree")
                }
            }
        }
    }

    private fun selected(id: String) {
        compose.onNodeWithTag("overdextop-entry:$id").assertIsSelected().assertIsDisplayed()
    }

    private fun shownShortcutIds(): List<String> = compose.onAllNodes(
        SemanticsMatcher("desktop shortcut") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("overdextop-entry:") == true
        },
    ).fetchSemanticsNodes().map {
        it.config[SemanticsProperties.TestTag].removePrefix("overdextop-entry:")
    }

    private fun textExists(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertBootTextAbsent() {
        compose.onNodeWithText("overdex boot sequence...").assertDoesNotExist()
        compose.onNodeWithText("overdex ready").assertDoesNotExist()
        compose.onNodeWithText("TRAINER: DESKTOP TRAINER").assertDoesNotExist()
        compose.onNodeWithText("ID: 12345678").assertDoesNotExist()
    }

    private fun advanceUntil(description: String, condition: () -> Boolean) {
        repeat(500) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
        }
        fail("Did not reach $description while advancing the home presentation")
    }

    private fun hardwareBounds() = buildMap {
        put("Droidball", compose.onNodeWithContentDescription("Droidball").getUnclippedBoundsInRoot())
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
