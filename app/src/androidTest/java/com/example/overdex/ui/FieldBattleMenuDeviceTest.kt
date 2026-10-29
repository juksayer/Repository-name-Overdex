package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.observation.DroidballOverlayMode
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.DroidballSignal
import com.example.overdex.ui.components.FieldBattleMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Pixel 4 Team Select card: 930 x 484 physical pixels at 440 dpi, font scale 1.0. */
class FieldBattleMenuDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startAndStopStayVisibleInSeparateTouchTargets() {
        showMenu(DroidballOverlayMode.SEEKING_TEAM_SELECT)

        val start = buttonBounds("START BATTLE HUD")
        val stop = buttonBounds("STOP SESSION")
        assertSeparated(start, stop)
        tapAndExpect("START BATTLE HUD", DroidballSignal.OpenBattleHudRequested)
        tapAndExpect("STOP SESSION", DroidballSignal.StopSessionRequested)
    }

    @Test fun readyMenuShowsBattleInstructionsAndHideWithoutAnotherStart() {
        showMenu(DroidballOverlayMode.BATTLE_READY)

        compose.onNodeWithText("BATTLE HUD READY").assertIsDisplayed()
        compose.onNodeWithText(
            "Start the battle in Pokémon GO. The HUD will appear when the battle is visible."
        ).assertIsDisplayed()
        compose.onNodeWithText("START BATTLE HUD").assertDoesNotExist()
        buttonBounds("HIDE HUD MENU")
        val hide = buttonBounds("HIDE")
        val stop = buttonBounds("STOP SESSION")
        assertSeparated(hide, stop)
    }

    @Test fun teamConfirmationYesAndNoAreVisibleAndTappable() {
        showMenu(DroidballOverlayMode.TEAM_SELECT, listOf("Raichu", "Dragonair", "Sneasel"))

        compose.onNodeWithText("IS THIS YOUR TEAM?").assertIsDisplayed()
        compose.onNodeWithText("Raichu · Dragonair · Sneasel").assertIsDisplayed()
        val yes = buttonBounds("YES")
        val no = buttonBounds("NO / RESCAN")
        val stop = buttonBounds("STOP SESSION")
        assertSeparated(yes, no)
        assertSeparated(stop, yes)
        assertSeparated(stop, no)
        tapAndExpect("YES", DroidballSignal.ConfirmInferredPlayerTeamRequested)
        tapAndExpect("NO / RESCAN", DroidballSignal.RejectInferredPlayerTeamRequested)
    }

    private fun showMenu(mode: DroidballOverlayMode, inferredTeam: List<String?> = emptyList()) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(PIXEL_DENSITY, fontScale = 1f)) {
                Box(Modifier.requiredSize((WIDTH_PX / PIXEL_DENSITY).dp,
                    (HEIGHT_PX / PIXEL_DENSITY).dp).testTag(STAGE)) {
                    FieldBattleMenu(
                        mode = mode,
                        inferredTeam = inferredTeam,
                        confirmed = false,
                        configuredTeam = emptyList(),
                        teamEditingAllowed = true,
                        widthPx = WIDTH_PX,
                        heightPx = HEIGHT_PX,
                    )
                }
            }
        }
        val stage = compose.onNodeWithTag(STAGE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Reference card width", WIDTH_PX.toFloat(), stage.width, 1f)
        assertEquals("Reference card height", HEIGHT_PX.toFloat(), stage.height, 1f)
    }

    private fun buttonBounds(label: String): Rect {
        val stage = compose.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().fetchSemanticsNode().boundsInRoot
        assertTrue("$label is entirely inside the card: $bounds within $stage",
            bounds.left >= stage.left && bounds.top >= stage.top &&
                bounds.right <= stage.right && bounds.bottom <= stage.bottom)
        assertTrue("$label keeps its complete 48 dp touch height", bounds.height >= 48f * PIXEL_DENSITY - 1f)
        return bounds
    }

    private fun assertSeparated(first: Rect, second: Rect) {
        assertTrue("Touch targets overlap: $first and $second",
            first.right <= second.left || second.right <= first.left ||
                first.bottom <= second.top || second.bottom <= first.top)
    }

    private fun tapAndExpect(label: String, expected: DroidballSignal) {
        val received = CopyOnWriteArrayList<DroidballSignal>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            DroidballService.signals.collect { received.add(it) }
        }
        try {
            compose.onNodeWithText(label).performTouchInput { click() }
            compose.waitUntil { expected in received }
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val STAGE = "field-menu-card"
        const val WIDTH_PX = 930
        const val HEIGHT_PX = 484
        const val PIXEL_DENSITY = 2.75f
    }
}
