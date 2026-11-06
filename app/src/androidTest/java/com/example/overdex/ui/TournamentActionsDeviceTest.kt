package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.screens.TournamentAction
import com.example.overdex.ui.screens.TournamentActionButtons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Production LCD controls, with no account, network, capture, or saved-room side effects. */
class TournamentActionsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedActionScrollsIntoViewAndCanBeActivatedAtLargeFontScale() {
        val selected = mutableIntStateOf(0)
        val activated = AtomicInteger(-1)
        val actions = (0..13).map { index -> TournamentAction("ACTION $index") { activated.set(index) } }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                Box(Modifier.size(260.dp, 85.dp)) {
                    TournamentActionButtons(actions, selected.intValue, false, { selected.intValue = it }, Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithTag("tournament-action:0").assertIsDisplayed()
        // A physical Right selection changes this same state in the screen. It must reveal the
        // selected action automatically; the trainer should not need to discover a touch scroll.
        compose.runOnIdle { selected.intValue = 13 }
        compose.onNodeWithTag("tournament-action:13").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(13, activated.get()) }
        compose.runOnIdle { selected.intValue = 0 }
        compose.onNodeWithTag("tournament-action:0").assertIsDisplayed()
    }

    @Test fun disabledAndBusyActionsCannotInvokeTournamentChanges() {
        val calls = AtomicInteger()
        val busy = mutableStateOf(false)
        val actions = listOf(
            TournamentAction("WAITING FOR OPPONENT", false) { calls.incrementAndGet() },
            TournamentAction("READY") { calls.incrementAndGet() },
        )
        compose.setContent {
            Box(Modifier.size(280.dp, 100.dp)) {
                TournamentActionButtons(actions, 0, busy.value, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("tournament-action:0").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls.get()); busy.value = true }
        compose.onNodeWithTag("tournament-action:1").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls.get()); busy.value = false }
        compose.onNodeWithTag("tournament-action:1").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls.get()) }
    }

    @Test fun replacingActionsResetsScrollEvenWhenSelectionIndexStaysTheSame() {
        val selected = mutableIntStateOf(10)
        val shortList = mutableStateOf(false)
        compose.setContent {
            val actions = if (shortList.value) listOf(TournamentAction("BACK TO ROOM") {})
                else (0..11).map { TournamentAction("ACTION $it") {} }
            Box(Modifier.size(260.dp, 85.dp)) {
                TournamentActionButtons(actions, selected.intValue, false, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("tournament-action:10").assertIsDisplayed()
        compose.runOnIdle { shortList.value = true }
        compose.onNodeWithTag("tournament-action:0").assertIsDisplayed().assertTextContains("BACK TO ROOM", substring = true)
    }
}
