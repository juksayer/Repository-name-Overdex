package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.ui.components.glassShield
import com.example.overdex.ui.screens.observatory.MatchComparisonScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MatchComparisonDeviceTest {
    @get:Rule val compose = createComposeRule()
    private class Controls {
        var up: () -> Unit = {}; var down: () -> Unit = {}
        var left: () -> Unit = {}; var right: () -> Unit = {}
        var a: () -> Unit = {}; var b: () -> Unit = {}
    }

    private fun fixture(id: String, count: Int = 3, result: String? = "WIN"): MatchArchive {
        fun article(key: String, at: Long, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
            key, id, 1_700_000_000_000L + at, 1_700_000_000_000L + at, "TEST", payload,
            emptyList(), null, at, emptyList(), at * 1_000_000L)
        return MatchArchive(matchId = id, articles = buildList {
            add(article("start", 0, ArchivedMatchStarted))
            add(article("player", 1_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)))
            add(article("opponent", 2_000, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)))
            repeat(count) { n -> add(article("hit-$n", 3_000L + n * 1_000,
                ArchivedFastMoveUseObserved("use-$n", "PLAYER", "OPPONENT",
                    evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST"))) }
            add(article("charge", 20_000, ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST")))
            if (result != null) add(article("result", 30_000, ArchivedMatchEnded(result)))
        })
    }

    private fun show(entries: State<List<MatchComparisonEntry>>, controls: Controls,
        opened: MutableList<String>, onBack: () -> Unit = {}, tall: Boolean = false) {
        compose.setContent {
            var lcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
            Column {
                Box(Modifier.size(320.dp, if (tall) 420.dp else 280.dp).glassShield()) {
                    MatchComparisonScreen(entries.value, onOpenMatch = { opened += it }, onBack = onBack,
                        onUp = { controls.up = it }, onDown = { controls.down = it },
                        onLeft = { controls.left = it }, onRight = { controls.right = it },
                        onA = { controls.a = it }, onB = { controls.b = it },
                        onLcdContentUpdate = { lcd = it })
                }
                Box(Modifier.size(240.dp, 170.dp)) { lcd?.invoke() }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("match-comparison-card-${entries.value.first().matchId}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun scrollPosition(): Float = compose.onNodeWithTag("match-comparison-content")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    @Test fun comparisonUsesSharedScaleAndEveryLcdActionHasPhysicalAccess() {
        val entries = mutableStateOf(listOf(MatchComparisonEntry("one", fixture("one", 3)),
            MatchComparisonEntry("two", fixture("two", 8, "LOSS"))))
        val controls = Controls(); val opened = mutableListOf<String>(); var backs = 0
        show(entries, controls, opened, { backs++ }, tall = true)
        compose.onNodeWithTag("comparison-one-player-0-fast-bar")
            .assertContentDescriptionEquals("FAST: 3 recorded uses; scale 0 to 8")
        compose.onNodeWithTag("comparison-two-player-0-fast-bar")
            .assertContentDescriptionEquals("FAST: 8 recorded uses; scale 0 to 8")
        compose.onNodeWithText("OPEN MATCH").assertIsDisplayed().assertIsSelected()
        saveScreenshot("comparison-ui-check.png")
        compose.runOnIdle { controls.a() }
        assertEquals(listOf("one"), opened)
        compose.runOnIdle { controls.left() }; compose.waitForIdle()
        compose.onNodeWithText("NEXT MATCH").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { controls.a() }; compose.waitForIdle()
        compose.onNodeWithTag("match-comparison-card-two").assertIsSelected()
        compose.runOnIdle { controls.right() }; compose.waitForIdle()
        compose.runOnIdle { controls.a() }
        assertEquals(listOf("one", "two"), opened)
        compose.runOnIdle { controls.left(); controls.left() }; compose.waitForIdle()
        compose.onNodeWithText("PREV MATCH").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { controls.a() }; compose.waitForIdle()
        compose.onNodeWithTag("match-comparison-card-one").assertIsSelected()
        compose.runOnIdle { controls.left() }; compose.waitForIdle()
        compose.onNodeWithText("BACK").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { controls.a(); controls.b() }
        assertEquals(2, backs)
    }

    @Test fun glassBlocksCrtDraggingButButtonsScrollEveryCardAndRefreshDoesNotJump() {
        val entries = mutableStateOf((1..4).map { n -> MatchComparisonEntry("match-$n", fixture("match-$n", n)) })
        val controls = Controls(); val opened = mutableListOf<String>()
        show(entries, controls, opened)
        val before = scrollPosition()
        compose.onNodeWithTag("match-comparison-content").performTouchInput { swipeUp() }
        assertEquals(before, scrollPosition())
        repeat(4) { compose.runOnIdle { controls.down() }; compose.waitForIdle() }
        val scrolled = scrollPosition()
        assertTrue(scrolled > before)
        compose.runOnIdle { entries.value = entries.value.map { entry ->
            if (entry.matchId == "match-1") entry.copy(archive = fixture("match-1", 9)) else entry
        } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("FAST: 9 recorded uses; scale 0 to 9")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        assertEquals(scrolled, scrollPosition())
        compose.onNodeWithTag("match-comparison-card-match-1").assertIsSelected()
        repeat(20) { compose.runOnIdle { controls.down() }; compose.waitForIdle() }
        compose.onNodeWithTag("comparison-match-4-player-0-charged-bar").assertIsDisplayed()
        saveScreenshot("comparison-ui-scrolled.png")
        repeat(30) { compose.runOnIdle { controls.up() }; compose.waitForIdle() }
        compose.onNodeWithText("RECORDED PLAYER MOVE USES").assertIsDisplayed()
    }

    @Test fun anUnavailableMatchCannotOpenAndRetainedRecordWithRefreshErrorCan() {
        val entries = mutableStateOf(listOf(MatchComparisonEntry("selected", null, "Try again later")))
        val controls = Controls(); val opened = mutableListOf<String>()
        show(entries, controls, opened)
        compose.onNodeWithText("OPEN MATCH").assertIsNotEnabled()
        compose.runOnIdle { controls.a() }
        assertTrue(opened.isEmpty())
        compose.runOnIdle { entries.value = listOf(MatchComparisonEntry("selected", fixture("selected", 0, null), "Try again later")) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("comparison-selected-player-0-fast-bar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("OPEN MATCH").assertIsEnabled()
        compose.onNodeWithText("MATCH 1 · RESULT NOT RECORDED").assertExists()
        compose.onNodeWithText("Latest refresh unavailable: Try again later. Showing the retained record.").assertExists()
        compose.runOnIdle { controls.a() }
        assertEquals(listOf("selected"), opened)
    }
}
