package com.example.overdex.ui

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.screens.FieldNotesScreen
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Actual journal assets, ViewModels, shell controls and protected CRT on a disposable emulator. */
class FieldNotesScreenDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("field_note_favorites", Context.MODE_PRIVATE)
    private var hadFavoriteKey = false
    private var savedFavorites: Set<String> = emptySet()
    private var preferencesIsolated = false
    private val pageTurns = AtomicInteger()
    private val exits = AtomicInteger()
    private val openedSpecies = AtomicInteger()

    @Before fun isolateAndRememberFavorites() {
        assertTrue("Run journal integration tests only on a disposable emulator, never a trainer's phone",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        hadFavoriteKey = preferences.contains("note_ids")
        savedFavorites = preferences.getStringSet("note_ids", emptySet()).orEmpty().toSet()
        preferencesIsolated = true
        assertTrue("The emulator's original favorites must be backed up before changing them",
            preferences.edit().remove("note_ids").commit())
    }

    @After fun restoreFavorites() {
        if (!preferencesIsolated) return
        // Dispose route-scoped readers before restoring the preference used by this test.
        compose.runOnIdle {
            compose.activity.setContent { }
            compose.activity.viewModelStore.clear()
        }
        val editor = preferences.edit()
        if (hadFavoriteKey) editor.putStringSet("note_ids", savedFavorites) else editor.remove("note_ids")
        assertTrue("Restore the emulator's pre-test favorites", editor.commit())
    }

    @Test fun physicalPageControlsAndFavoriteRemovalNeverLeaveAnEmptyBookStranded() {
        openJournal()
        title("The First Step")
        press("Left")
        assertEquals("A boundary press does not turn a page or play its sound", 0, pageTurns.get())

        press("Right")
        title("Patient Growth")
        assertEquals(1, pageTurns.get())
        press("Left")
        title("The First Step")
        press("Right")
        title("Patient Growth")
        assertEquals(3, pageTurns.get())

        press("SELECT")
        compose.waitUntil(timeoutMillis = 10_000) {
            "002-001" in preferences.getStringSet("note_ids", emptySet()).orEmpty()
        }
        press("START")
        title("Patient Growth")
        compose.onNodeWithText("1 / 1 · favorites").assertIsDisplayed()
        press("Left"); press("Right")
        assertEquals("The single favorite is one page, not a looping animation", 3, pageTurns.get())

        press("SELECT")
        compose.waitUntil(timeoutMillis = 10_000) {
            preferences.getStringSet("note_ids", emptySet()).orEmpty().isEmpty()
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("No favorites yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("field-notes-page").assertDoesNotExist()
        press("START")
        title("Patient Growth")
        compose.onNodeWithTag("field-notes-action:species").assertExists()
        assertEquals(0, exits.get())
    }

    @Test fun lcdKeyboardFindsAllDroidballNotesAndDetailsReturnToTheSameProtectedPage() {
        openJournal()
        title("The First Step")
        enterQuery("DROIDBALL")
        compose.onNodeWithText("4 notes · all species").assertIsDisplayed()
        press("A")
        compose.onNodeWithTag("field-notes-page").assertExists()
        compose.onNodeWithText("Quiet by the dock.").assertIsDisplayed()
        press("Right")
        compose.onNodeWithText("Quiet LED glowed.").assertIsDisplayed()
        press("Right")
        compose.onNodeWithText("Pages closed at dusk.").assertIsDisplayed()
        press("Right")
        compose.onNodeWithText("Last page of the book.").assertIsDisplayed()
        press("Left")
        compose.onNodeWithText("Pages closed at dusk.").assertIsDisplayed()

        val turnsBeforeTouch = pageTurns.get()
        compose.onNodeWithTag("field-notes-page").performTouchInput {
            click(center)
            swipeLeft()
            swipeUp()
        }
        compose.onNodeWithText("Pages closed at dusk.").assertIsDisplayed()
        assertEquals("The journal's CRT remains an observation surface", turnsBeforeTouch, pageTurns.get())
        assertEquals(0, openedSpecies.get())
        assertEquals(0, exits.get())

        action("species")
        compose.onNodeWithTag("field-notes-test-detail").assertIsDisplayed()
        assertEquals("Droidball uses the app catalogue ID, not source index 1026", 10001, openedSpecies.get())
        compose.onNodeWithTag("field-notes-test-detail").performTouchInput { click() }
        compose.onNodeWithText("Pages closed at dusk.").assertIsDisplayed()
        compose.onNodeWithTag("field-notes-query").assertDoesNotExist()
        assertEquals("Returning from details is not another page turn", turnsBeforeTouch, pageTurns.get())
    }

    @Test fun lessonSearchHighlightsThePassageAndKeepsTheEntryIdWhenFavoritesReordersTheBook() {
        openJournal()
        title("The First Step")
        enterQuery("ADAPTABILITY")
        compose.onNodeWithText("3 notes · all species").assertIsDisplayed()
        assertAdaptabilityResult()
        File(context.cacheDir, "field-notes-search-review.png").outputStream().use { output ->
            assertTrue("Keep the full shell and index for visual review",
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }

        press("A")
        title("The Bending Current")
        compose.onNodeWithTag("field-notes-entry-id").assertTextEquals("entry 061-001")
        press("SELECT")
        compose.waitUntil(timeoutMillis = 10_000) {
            "061-001" in preferences.getStringSet("note_ids", emptySet()).orEmpty()
        }
        press("START")
        title("The Bending Current")
        compose.onNodeWithText("1 / 1 · favorites").assertIsDisplayed()
        compose.onNodeWithTag("field-notes-entry-id").assertTextEquals("entry 061-001")

        enterQuery("ADAPTABILITY")
        compose.onNodeWithTag("field-notes-result:132-001").assertDoesNotExist()
        compose.onNodeWithTag("field-notes-result:656-001").assertDoesNotExist()
        assertAdaptabilityResult()
        press("A")
        title("The Bending Current")
        compose.onNodeWithTag("field-notes-entry-id").assertTextEquals("entry 061-001")
    }

    @Test fun nearbyExchangeReturnsThroughTheExchangeMenuToTheSameJournalPage() {
        openJournal()
        title("The First Step")
        press("Right")
        title("Patient Growth")
        compose.onNodeWithTag("field-notes-entry-id").assertTextEquals("entry 002-001")
        val turnsBeforeExchange = pageTurns.get()

        action("exchange")
        compose.onNodeWithTag("field-notes-exchange-crt").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction() and
            hasAnyAncestor(hasTestTag("field-notes-exchange-lcd")))
            .performScrollToNode(hasTestTag("field-notes-exchange-action:nearby"))
        compose.onNodeWithTag("field-notes-exchange-action:nearby")
            .assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithTag("field-notes-nearby-crt").assertIsDisplayed()
        compose.onNodeWithTag("field-notes-nearby-auth-code").assertDoesNotExist()
        // Merely opening this route must not choose receive/find, request permissions or use radios.
        press("B")
        compose.onNodeWithTag("field-notes-nearby-crt").assertDoesNotExist()
        compose.onNodeWithTag("field-notes-exchange-crt").assertIsDisplayed()
        press("B")
        title("Patient Growth")
        compose.onNodeWithTag("field-notes-entry-id").assertTextEquals("entry 002-001")
        assertEquals("Menu navigation is not a journal page turn", turnsBeforeExchange, pageTurns.get())
        assertEquals("Both Back presses stay inside the journal destination", 0, exits.get())
    }

    private fun assertAdaptabilityResult() {
        compose.onNodeWithTag("field-notes-result-id:061-001")
            .assertTextEquals("entry 061-001").assertIsDisplayed()
        val passage = compose.onNodeWithTag("field-notes-result-excerpt:061-001")
            .assertTextEquals("lesson · Adaptability.").assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue("The matched lesson word must be visibly highlighted, not merely present in the text",
            passage.spanStyles.any { span ->
                span.item.background != Color.Unspecified &&
                    passage.text.substring(span.start, span.end).equals("Adaptability", ignoreCase = true)
            })
    }

    private fun enterQuery(value: String) {
        action("search")
        compose.onNodeWithTag("Terminal key grid").assertIsDisplayed()
        value.forEach { letter ->
            compose.onNode(hasText(letter.toString(), substring = false) and
                hasAnyAncestor(hasTestTag("Terminal key grid")) and
                !hasAnyAncestor(hasContentDescription("End typing")), useUnmergedTree = true)
                .performTouchInput { click() }
        }
        compose.onNodeWithTag("field-notes-query").assertTextEquals("find: $value")
        compose.onNodeWithContentDescription("End typing").assertIsDisplayed().performTouchInput { click() }
    }

    private fun openJournal() {
        lateinit var pokedex: PokedexViewModel
        compose.runOnIdle { pokedex = ViewModelProvider(compose.activity)[PokedexViewModel::class.java] }
        compose.setContent {
            val navigation = rememberNavController()
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "field-notes-device-test") {
                Box(Modifier.size(360.dp, 760.dp)) {
                    NavHost(navigation, startDestination = "journal") {
                        composable("journal") {
                            FieldNotesScreen(
                                viewModel = pokedex,
                                filterSettings = FilterSettings(isEnabled = false),
                                onFilterSettingsChange = {},
                                onOpenSpecies = { id -> openedSpecies.set(id); navigation.navigate("species/$id") },
                                onOpenOwned = { error("This test does not open an owned specimen") },
                                onBack = { exits.incrementAndGet() },
                                onPageTurn = { pageTurns.incrementAndGet() },
                            )
                        }
                        composable("species/{id}") {
                            Text("return to journal", Modifier.testTag("field-notes-test-detail")
                                .clickable { navigation.popBackStack() })
                        }
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("field-notes-page").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun action(id: String) {
        compose.onNodeWithTag("field-notes-actions")
            .performScrollToNode(hasTestTag("field-notes-action:$id"))
        compose.onNodeWithTag("field-notes-action:$id").performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun title(value: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("field-notes-note-title") and hasText(value, substring = false))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("field-notes-note-title").assertTextEquals(value).assertIsDisplayed()
    }

    private fun press(label: String) {
        val target = if (label in listOf("Up", "Down", "Left", "Right"))
            compose.onNodeWithContentDescription(label) else compose.onNodeWithText(label)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }
}
