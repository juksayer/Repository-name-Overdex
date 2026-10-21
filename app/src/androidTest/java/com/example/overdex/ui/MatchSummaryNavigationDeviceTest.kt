package com.example.overdex.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.archive.*
import com.example.overdex.ui.components.glassShield
import com.example.overdex.ui.screens.observatory.MatchSummaryScreen
import com.example.overdex.ui.screens.observatory.MatchSummaryRoute
import com.example.overdex.ui.screens.observatory.MatchArchiveViewerScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MatchSummaryNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(): MatchArchive {
        fun article(i: Int, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle("$i", "summary-test",
            i * 1_000L, i * 1_000L, "TEST", payload, emptyList(), null, i.toLong(), emptyList(), i * 1_000_000_000L)
        return MatchArchive(matchId = "summary-test", articles = listOf(
            article(0, ArchivedMatchStarted),
            article(1, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Galvantula", 596)),
            article(2, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)),
            article(3, ArchivedFastMoveIdentified("OPPONENT", "Sneasel", "Feint Attack", 1_000_000_000, null, 2, "TEST")),
            article(4, ArchivedChargedMoveEnergySpent("OPPONENT", "Sneasel", "Foul Play", 40, "TEST")),
            article(5, ArchivedMatchEnded("WIN"))))
    }

    @Test fun summaryScrollsBehindGlassAndEveryLcdActionIsButtonAccessible() {
        var up: () -> Unit = {}; var down: () -> Unit = {}; var left: () -> Unit = {}; var right: () -> Unit = {}
        var a: () -> Unit = {}; var b: () -> Unit = {}
        val actions = mutableListOf<String>()
        compose.setContent {
            var lcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
            Column {
                Box(Modifier.size(320.dp, 260.dp).glassShield()) {
                    MatchSummaryScreen(fixture(), fullExportAvailable = true,
                        onOpenMatches = { actions += "open" }, onInspect = { actions += "inspect" },
                        onExportCompact = { actions += "compact" }, onExportFull = { actions += "full" },
                        onBack = { actions += "back" }, onUp = { up = it }, onDown = { down = it },
                        onLeft = { left = it }, onRight = { right = it }, onA = { a = it }, onB = { b = it },
                        onLcdContentUpdate = { lcd = it })
                }
                Box(Modifier.size(220.dp, 150.dp)) { lcd?.invoke() }
            }
        }
        compose.onNodeWithText("YOU WIN").assertIsDisplayed()
        // A CRT drag must stay blocked, while the registered physical handler scrolls it.
        val before = compose.onNodeWithText("YOU WIN").fetchSemanticsNode().positionInRoot
        compose.onNodeWithTag("match-summary-content").performTouchInput { swipeUp() }
        assertEquals(before, compose.onNodeWithText("YOU WIN").fetchSemanticsNode().positionInRoot)
        repeat(8) { compose.runOnIdle { down() }; compose.waitForIdle() }
        compose.onNodeWithText("RECORDING").assertIsDisplayed()
        repeat(8) { compose.runOnIdle { up() }; compose.waitForIdle() }
        compose.onNodeWithText("YOU WIN").assertIsDisplayed()
        val labels = listOf("OPEN RECORDED MATCHES", "INSPECT TIMELINE / REPLAY", "EXPORT COMPACT", "EXPORT FULL", "BACK")
        labels.forEachIndexed { i, label ->
            if (i > 0) { compose.runOnIdle { right() }; compose.waitForIdle() }
            compose.onNodeWithText(label).assertIsDisplayed().assertIsSelected()
            compose.runOnIdle { a() }
        }
        compose.runOnIdle { assertEquals(listOf("open", "inspect", "compact", "full", "back"), actions); b() }
        assertEquals("back", actions.last())
        compose.runOnIdle { left() }; compose.waitForIdle()
        compose.onNodeWithText("EXPORT FULL").assertIsDisplayed().assertIsSelected()
    }

    @Test fun summaryRefreshesWhenCanonicalArchiveArrives() {
        val current = mutableStateOf<MatchArchive?>(null)
        compose.setContent { Box(Modifier.size(300.dp, 300.dp).glassShield()) { MatchSummaryScreen(current.value) } }
        compose.onNodeWithText("No recorded matches yet. Open Recorded Matches to browse available archives.").assertIsDisplayed()
        compose.runOnIdle { current.value = fixture() }
        compose.onNodeWithText("YOU WIN").assertIsDisplayed()
    }

    @Test fun liveSummaryIgnoresUnassignedSessionArticles() {
        val timeline = com.example.overdex.battle.reality.InMemoryRealityTimeline()
        val id = com.example.overdex.battle.observation.MatchId("live-summary")
        fun append(key: String, match: com.example.overdex.battle.observation.MatchId?, payload: com.example.overdex.battle.custody.TestimonyPayload) {
            timeline.append(com.example.overdex.battle.reality.RealityArticle(
                com.example.overdex.battle.reality.ArticleId(key), 1_000, 1_000,
                com.example.overdex.battle.custody.SourceId("TEST"), payload, matchId = match))
        }
        append("session", null, com.example.overdex.battle.custody.RawTestimony("stopped"))
        append("result", id, com.example.overdex.battle.custody.MatchEnded(com.example.overdex.model.BattleResult.WIN))
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val directory = remember { ArchiveDirectoryManager(context) }
            Box(Modifier.size(320.dp, 350.dp).glassShield()) {
                MatchSummaryRoute(MatchArchiveSource(id, timeline), directory, {}, { _, _ -> }, {},
                    {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("YOU WIN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("YOU WIN").assertIsDisplayed()
        assertEquals(2, timeline.getArticles().size)
    }

    @Test fun archiveDetailsAreReachableThroughThePhysicalActionCycle() {
        var right: () -> Unit = {}; var a: () -> Unit = {}; var b: () -> Unit = {}
        compose.setContent {
            var lcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
            Column {
                Box(Modifier.size(320.dp, 300.dp).glassShield()) {
                    MatchArchiveViewerScreen(fixture(), onBack = {}, onRight = { right = it }, onA = { a = it },
                        onB = { b = it }, onLcdContentUpdate = { lcd = it })
                }
                Box(Modifier.size(220.dp, 170.dp)) { lcd?.invoke() }
            }
        }
        repeat(3) { compose.runOnIdle { right() }; compose.waitForIdle() }
        compose.onNodeWithText("ARTICLE DETAILS").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { a() }; compose.waitForIdle()
        compose.onNodeWithText("CLOSE DETAILS").assertIsDisplayed()
        compose.runOnIdle { a() }; compose.waitForIdle()
        compose.onNodeWithText("ARTICLE DETAILS").assertIsDisplayed()
        compose.runOnIdle { a() }; compose.waitForIdle()
        compose.runOnIdle { b() }; compose.waitForIdle()
        compose.onNodeWithText("ARTICLE DETAILS").assertIsDisplayed()
    }
}
