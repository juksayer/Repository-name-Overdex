package com.example.overdex.ui

import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchivePackageWriter
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.ui.components.glassShield
import com.example.overdex.ui.screens.observatory.MatchArchiveDirectoryScreen
import com.example.overdex.ui.screens.observatory.MatchArchiveViewerScreen
import com.example.overdex.ui.screens.observatory.MatchSummaryRoute
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real ZIP packages and production screens, isolated from the emulator's saved matches. */
class MatchHistoryNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    // Keep the fixture below the real FileProvider root while giving its manager an isolated directory.
    private val root = File(base.filesDir, "match_archives/history-navigation-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir(): File = root
    }
    private var up: () -> Unit = {}
    private var down: () -> Unit = {}
    private var right: () -> Unit = {}
    private var a: () -> Unit = {}
    private var b: () -> Unit = {}
    private val showContent = mutableStateOf(true)

    @Before fun useTheApplicationsRealFileProviderRoot() {
        // FileProvider caches its strategy by authority across tests. Initializing it
        // from a wrapper would restrict that cache to the first fixture's filesDir.
        FileProvider.getUriForFile(base, "${base.packageName}.archive-files",
            File(base.filesDir, "match_archives/provider-root-placeholder.odxmatch.zip"))
    }

    @After fun removeOnlyThisTestsFixtures() {
        compose.runOnIdle { showContent.value = false }
        compose.waitForIdle()
        root.deleteRecursively()
    }

    @Test fun olderHistorySelectionOwnsItsSummaryAndReplayAndSurvivesReturning() {
        val old = archive("older-loss", 1_700_000_000_000L, "LOSS", "Bulbasaur", 1)
        val recent = archive("newer-win", 1_700_086_400_000L, "WIN", "Charmander", 4)
        // Deliberately contradict both filename order and file modification order.
        val oldFile = save("z-older.odxmatch.zip", old, 1_800_000_000_000L)
        save("a-newer.odxmatch.zip", recent, 1_600_000_000_000L)
        compose.setContent { if (showContent.value) NavigationHarness(competingLiveSource()) }

        awaitText("DIRECTORY")
        press { a() } // Directory → Match History through the registered instrument control.
        // A LazyColumn need not compose an off-screen older row. First selection and the
        // next Down selection prove the actual order without assuming both rows fit.
        awaitRow("newer-win")
        compose.onNodeWithTag("match-history-row-newer-win").assertIsSelected()
        press { down() }
        awaitRow("older-loss")
        compose.onNodeWithTag("match-history-row-older-loss").assertIsSelected()
        press { a() }
        awaitText("GOOD EFFORT")
        compose.onNodeWithText("YOU WIN").assertDoesNotExist()
        compose.onNodeWithText("ID: older-loss\nEXPORTS: DOWNLOADS/ODXMATCHES").assertExists()
        compose.onNodeWithText("Bulbasaur").assertIsDisplayed()

        // The same saved snapshot and URI, rather than the current live WIN, reach inspection.
        press { right() }
        press { a() }
        awaitText("[ MATCH ARCHIVE ]")
        compose.onNodeWithText("ID: older-loss").assertIsDisplayed()
        press { a() } // OPEN REPLAY is the initial archive action.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Bulbasaur").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Bulbasaur").assertIsDisplayed()
        compose.onNodeWithContentDescription("Charmander").assertDoesNotExist()
        press { b() } // Replay → Timeline.
        awaitText("[ MATCH ARCHIVE ]")
        press { b() } // Timeline → the older selected summary.
        awaitText("GOOD EFFORT")
        compose.onNodeWithText("ID: older-loss\nEXPORTS: DOWNLOADS/ODXMATCHES").assertExists()

        // Insert a match while this summary is open. The history's saved URI must survive index shifts.
        save("b-newest.odxmatch.zip", archive("newest-tie", 1_700_172_800_000L, "TIE", "Pikachu", 25))
        press { b() }
        awaitRow("older-loss")
        compose.onNodeWithTag("match-history-row-older-loss").assertIsSelected()
        press { up() }; press { up() }
        awaitRow("newest-tie")
        compose.onNodeWithTag("match-history-row-newest-tie").assertIsSelected()
        press { down() }; press { down() }
        compose.onNodeWithTag("match-history-row-older-loss").assertIsSelected()
        press { a() }
        awaitText("GOOD EFFORT")

        // The summary's history action returns to the existing list instead of stacking another copy.
        press { a() }
        awaitRow("older-loss")
        compose.onNodeWithTag("match-history-row-older-loss").assertIsSelected()
        press { b() }
        awaitText("DIRECTORY")
        assertTrue("Viewing must not remove the original package", oldFile.isFile)
    }

    @Test fun unavailableHistorySelectionDoesNotSubstituteLiveOrLatestMatch() {
        save("latest.odxmatch.zip", archive("available-win", 1_700_086_400_000L, "WIN", "Charmander", 4))
        val missing = File(root, "match_archives/missing.odxmatch.zip")
        val uri = FileProvider.getUriForFile(base, "${base.packageName}.archive-files", missing)
        compose.setContent { if (showContent.value) NavigationHarness(competingLiveSource(), initiallySelected = uri) }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Unable to read the selected match:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("YOU WIN").assertDoesNotExist()
        compose.onNodeWithText("INSPECT TIMELINE / REPLAY").assertDoesNotExist()
        compose.onNodeWithText("ID: available-win", substring = true).assertDoesNotExist()
        compose.onNodeWithText("ID: competing-live", substring = true).assertDoesNotExist()
        compose.onNodeWithText("OPEN MATCH HISTORY").assertIsDisplayed()
        press { a() }
        awaitRow("available-win")
        press { a() }
        awaitText("YOU WIN")
        compose.onNodeWithText("ID: available-win\nEXPORTS: DOWNLOADS/ODXMATCHES").assertExists()
    }

    @Composable
    private fun NavigationHarness(source: MatchArchiveSource, initiallySelected: Uri? = null) {
        CompositionLocalProvider(LocalContext provides context) {
            val nav = rememberNavController()
            val activeEntry by nav.currentBackStackEntryAsState()
            val directory = remember { ArchiveDirectoryManager(context) }
            var inspected by remember { mutableStateOf<MatchArchive?>(null) }
            val initial = initiallySelected?.let { "match_summary?archiveUri=${Uri.encode(it.toString())}" }
                ?: "main_menu"
            Box(Modifier.size(340.dp, 560.dp)) {
                NavHost(nav, startDestination = initial) {
                    composable("main_menu") { entry ->
                        DestinationSurface(activeEntry?.id == entry.id) { controls, _ ->
                            SideEffect {
                                controls.a = { nav.navigate("match_archive_directory") }
                                controls.b = { }
                            }
                            Text("DIRECTORY")
                        }
                    }
                    composable("match_archive_directory") { entry ->
                        DestinationSurface(activeEntry?.id == entry.id) { controls, publishLcd ->
                            MatchArchiveDirectoryScreen(directory,
                                onBack = { nav.popBackStack() },
                                onOpenArchive = { uri ->
                                    nav.navigate("match_summary?archiveUri=${Uri.encode(uri.toString())}")
                                },
                                onUp = { controls.up = it }, onDown = { controls.down = it },
                                onA = { controls.a = it }, onB = { controls.b = it },
                                onLcdContentUpdate = publishLcd)
                        }
                    }
                    composable("match_summary?archiveUri={archiveUri}", arguments = listOf(
                        navArgument("archiveUri") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )) { entry ->
                        DestinationSurface(activeEntry?.id == entry.id) { controls, publishLcd ->
                            MatchSummaryRoute(source, directory,
                                onOpenMatches = {
                                    if (!nav.popBackStack("match_archive_directory", false)) {
                                        nav.navigate("match_archive_directory") { launchSingleTop = true }
                                    }
                                },
                                onInspect = { archive, uri ->
                                    val selected = entry.arguments?.getString("archiveUri")?.let(Uri::parse)
                                    assertEquals("Inspection must retain the selected archive URI", selected, uri)
                                    inspected = archive
                                    nav.navigate("match_archive_viewer")
                                },
                                onBack = { nav.popBackStack() },
                                onUp = { controls.up = it }, onDown = { controls.down = it }, onLeft = {},
                                onRight = { controls.right = it }, onA = { controls.a = it }, onB = { controls.b = it },
                                onLcdContentUpdate = publishLcd,
                                selectedArchiveUri = entry.arguments?.getString("archiveUri")?.let(Uri::parse))
                        }
                    }
                    composable("match_archive_viewer") { entry ->
                        DestinationSurface(activeEntry?.id == entry.id) { controls, publishLcd ->
                            MatchArchiveViewerScreen(requireNotNull(inspected),
                                onBack = { nav.popBackStack() },
                                onUp = { controls.up = it }, onDown = { controls.down = it },
                                onRight = { controls.right = it }, onA = { controls.a = it }, onB = { controls.b = it },
                                onLcdContentUpdate = publishLcd,
                                resolveSpeciesId = { name -> mapOf("Bulbasaur" to 1, "Squirtle" to 7)[name] })
                        }
                    }
                }
            }
        }
    }

    private class Controls {
        var up: () -> Unit = {}
        var down: () -> Unit = {}
        var right: () -> Unit = {}
        var a: () -> Unit = {}
        var b: () -> Unit = {}
    }

    /** MainActivity gives each destination its own shell, LCD, and registered handlers. */
    @Composable
    private fun DestinationSurface(
        active: Boolean,
        content: @Composable (Controls, ((@Composable () -> Unit)?) -> Unit) -> Unit,
    ) {
        val controls = remember { Controls() }
        var lcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
        SideEffect {
            if (active) {
                up = { controls.up() }
                down = { controls.down() }
                right = { controls.right() }
                a = { controls.a() }
                b = { controls.b() }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).glassShield()) { content(controls) { lcd = it } }
            Box(Modifier.fillMaxWidth().height(220.dp)) { lcd?.invoke() }
        }
    }

    private fun press(action: () -> Unit) {
        compose.runOnIdle(action)
        compose.waitForIdle()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun awaitRow(matchId: String) {
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("match-history-row-$matchId").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            val packages = File(root, "match_archives").listFiles().orEmpty()
                .joinToString { "${it.name} (${it.length()} bytes)" }
            throw AssertionError(
                "History row $matchId was not composed. Fixture packages: $packages\n" +
                    compose.onRoot(useUnmergedTree = true).printToString(),
                failure,
            )
        }
    }

    private fun save(name: String, archive: MatchArchive, modified: Long = archive.articles.first().perceivedAt): File =
        File(root, "match_archives/$name").apply {
            parentFile!!.mkdirs()
            outputStream().use { MatchArchivePackageWriter.write(archive, it) }
            check(setLastModified(modified))
        }

    private fun archive(id: String, at: Long, result: String, player: String, speciesId: Int): MatchArchive {
        fun article(i: Int, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
            "$id-$i", id, at + i * 1_000, at + i * 1_000, "TEST", payload,
            emptyList(), null, i.toLong(), emptyList(), i * 1_000_000_000L)
        return MatchArchive(matchId = id, articles = listOf(
            article(0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", player, speciesId)),
            article(1, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Squirtle", 7)),
            article(2, ArchivedMatchStarted), article(3, ArchivedMatchEnded(result))))
    }

    private fun competingLiveSource(): MatchArchiveSource {
        val id = MatchId("competing-live")
        val timeline = InMemoryRealityTimeline()
        timeline.append(RealityArticle(ArticleId("live-start"), 1_800_000_000_000L, 1_800_000_000_000L,
            SourceId("TEST"), MatchStarted, matchId = id))
        timeline.append(RealityArticle(ArticleId("live-end"), 1_800_000_001_000L, 1_800_000_001_000L,
            SourceId("TEST"), MatchEnded(BattleResult.WIN), matchId = id))
        return MatchArchiveSource(id, timeline)
    }
}
