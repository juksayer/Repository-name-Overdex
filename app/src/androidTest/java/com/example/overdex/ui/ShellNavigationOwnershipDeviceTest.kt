package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.ui.ODXFi.ODXFiShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** Real destination lifecycles own instrument Back, including duplicate routes and transitions. */
class ShellNavigationOwnershipDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationBackWorksWhenTheGlobalRouteStillNamesThePreviousScreen() {
        lateinit var navigation: NavHostController
        val entries = mutableMapOf<String, NavBackStackEntry>()
        val returns = mutableListOf<String>()
        // Deliberately keep this stale throughout navigation. Destination ownership
        // must come from NavHost, rather than whichever route the shell sees first.
        compose.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "desktop") {
                NavHost(navigation, startDestination = "desktop") {
                    composable("desktop") { entry ->
                        entries["desktop"] = entry
                        Shell(entry, onBack = { returns += "desktop" })
                    }
                    composable("tournaments") { entry ->
                        entries["tournaments"] = entry
                        Shell(entry, onBack = {
                            returns += "tournaments"
                            navigation.popBackStack()
                        })
                    }
                }
            }
        }
        awaitResumed { entries["desktop"] }
        compose.runOnIdle { navigation.navigate("tournaments") }
        awaitResumed { entries["tournaments"] }
        backFor(entries.getValue("tournaments")).performTouchInput { click() }
        awaitResumed { entries["desktop"] }
        compose.runOnIdle {
            assertEquals(listOf("tournaments"), returns)
            assertEquals("desktop", navigation.currentDestination?.route)
        }
        backFor(entries.getValue("desktop")).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("tournaments", "desktop"), returns) }
    }

    @Test fun sameRouteOutgoingAndDisposedShellCallbacksCannotReturnTheActiveEntry() {
        lateinit var navigation: NavHostController
        val entries = mutableMapOf<String, NavBackStackEntry>()
        val returns = mutableListOf<String>()
        compose.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "tournaments") {
                NavHost(navigation, startDestination = "tournaments") {
                    composable("tournaments") { entry ->
                        entries[entry.id] = entry
                        Shell(entry, onBack = { returns += entry.id })
                    }
                }
            }
        }
        awaitResumed { entries.values.firstOrNull() }
        val first = entries.values.single()
        // Capture the actual old button's callback. Calling it directly models a
        // queued event, without relying on overlap coordinates during an animation.
        val oldBack = backFor(first).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            navigation.navigate("tournaments")
            assertFalse("An outgoing entry must relinquish input synchronously",
                first.lifecycle.currentState == Lifecycle.State.RESUMED)
            oldBack()
            assertEquals(emptyList<String>(), returns)
        }
        awaitResumed { entries.values.firstOrNull { it.id != first.id } }
        val second = entries.values.first { it.id != first.id }
        compose.runOnIdle {
            assertNotEquals(first.id, second.id)
            oldBack()
            assertEquals("The old shell must stay inactive after its transition completes",
                emptyList<String>(), returns)
        }
        backFor(second).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(second.id), returns) }
        compose.runOnIdle { navigation.popBackStack() }
        awaitResumed { first }
        compose.runOnIdle {
            oldBack()
            assertEquals("A disposed shell callback stays stale even when its entry resumes in a new shell",
                listOf(second.id), returns)
        }
        backFor(first).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(second.id, first.id), returns) }
    }

    @Test fun standaloneShellKeepsItsUnnamedRouteHandoffAndStaleRouteGuard() {
        val route = mutableStateOf<String?>(null)
        var returns = 0
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides route.value) {
                ODXFiShell(showBattleOverlay = false, onB = { returns++ }) { Text("Standalone shell") }
            }
        }
        compose.runOnIdle { route.value = "standalone" }
        compose.onNodeWithTag("odxfi-button-B").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, returns); route.value = "another-screen" }
        compose.onNodeWithTag("odxfi-button-B").performTouchInput { click() }
        compose.runOnIdle { assertEquals("A standalone stale shell must still reject Back", 1, returns) }
    }

    @Composable
    private fun Shell(entry: NavBackStackEntry, onBack: () -> Unit) {
        Box(Modifier.testTag("shell-owner:${entry.id}")) {
            ODXFiShell(showBattleOverlay = false, onB = onBack) { Text("Destination ${entry.id}") }
        }
    }

    private fun backFor(entry: NavBackStackEntry) = compose.onNode(
        hasTestTag("odxfi-button-B") and hasAnyAncestor(hasTestTag("shell-owner:${entry.id}")),
    )

    private fun awaitResumed(entry: () -> NavBackStackEntry?) {
        // navigate()/popBackStack() above run in runOnIdle, after that helper's
        // synchronization point. Settle the newly scheduled composition and its
        // transition before polling an Android lifecycle value: unlike a semantics
        // query, reading the entry does not itself flush Compose/layout work.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { entry()?.lifecycle?.currentState == Lifecycle.State.RESUMED }
        compose.waitForIdle()
    }
}
