package com.example.overdex.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.navigation.MatchSummaryHandoff
import com.example.overdex.ui.navigation.MatchSummaryReturnEffect
import com.example.overdex.ui.screens.SignalObservatoryScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsAndMatchReturnDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stopAndOrdinaryButtonSequencesNeverOpenHiddenSettings() {
        var stops = 0
        val inputs = mutableListOf<String>()
        compose.setContent {
            ODXFiShell(instrumentState = ObservationSessionState.SERVICE_ACTIVE,
                deploymentState = InstrumentDeploymentState.OBSERVING, showBattleOverlay = false,
                onStart = { stops++ }, onUp = { inputs += "UP" }, onDown = { inputs += "DOWN" },
                onLeft = { inputs += "LEFT" }, onRight = { inputs += "RIGHT" },
                onB = { inputs += "B" }, onA = { inputs += "A" }) {}
        }
        compose.onNodeWithText("STOP").performClick()
        compose.runOnIdle { assertEquals(1, stops) }
        val sequence = listOf("Up", "Up", "Down", "Down", "Left", "Right", "Left", "Right", "B", "A")
        sequence.forEach { key ->
            if (key in listOf("A", "B")) compose.onNodeWithText(key).performClick()
            else compose.onNodeWithContentDescription(key).performClick()
        }
        compose.onNodeWithText("FILTER SETTINGS").assertDoesNotExist()
        compose.onNodeWithText("OPEN RESEARCHER MODE").assertDoesNotExist()
        compose.runOnIdle { assertEquals(sequence.map { it.uppercase() }, inputs) }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun resultWaitsForUserReturnAndDoesNotStealTheNextResume() {
        lateinit var owner: Owner
        compose.runOnUiThread { owner = Owner(); owner.registry.currentState = Lifecycle.State.STARTED }
        val handoff = MatchSummaryHandoff()
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MatchSummaryReturnEffect(handoff.pending,
                    onOpenSummary = { opened += it.matchId.value },
                    onHandled = { handoff.acknowledge(it) })
            }
        }
        compose.runOnIdle {
            handoff.offer(MatchArchiveSource(MatchId("first"), InMemoryRealityTimeline()))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(opened.isEmpty()); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil { opened.size == 1 }
        compose.runOnIdle {
            assertNull(handoff.pending.value)
            owner.registry.currentState = Lifecycle.State.STARTED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("first"), opened) }
    }

    @Test fun explicitObservatorySettingsUsePhysicalButtonsAndCurrentValues() {
        var up: () -> Unit = {}; var down: () -> Unit = {}; var a: () -> Unit = {}; var b: () -> Unit = {}
        var backCount = 0
        compose.setContent {
            var filters by remember { mutableStateOf(FilterSettings(isEnabled = false)) }
            ODXFiShell(showBattleOverlay = false, filterSettings = filters,
                onUp = { up() }, onDown = { down() }, onA = { a() }, onB = { b() }) {
                SignalObservatoryScreen(filters, { filters = it }, onBack = { backCount++ },
                    onUp = { up = it }, onDown = { down = it }, onA = { a = it }, onB = { b = it })
            }
        }
        compose.onNodeWithText("SIGNAL OBSERVATORY").assertIsDisplayed()
        compose.onNodeWithText("Match Summary").assertIsDisplayed()
        // Debug builds expose Probe, Match Summary, Calibration, then Filters.
        repeat(3) { compose.onNodeWithContentDescription("Down").performClick() }
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("[ ON ]").assertIsDisplayed()
        compose.onNodeWithText("B").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
        compose.onNodeWithText("OPEN RESEARCHER MODE").assertDoesNotExist()
    }
}
