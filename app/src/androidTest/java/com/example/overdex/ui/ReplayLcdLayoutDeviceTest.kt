package com.example.overdex.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayCorrectionKind
import com.example.overdex.battle.replay.ReplayCorrectionSide
import com.example.overdex.battle.replay.ReplayTrainerCorrection
import com.example.overdex.battle.replay.ReplayTrainerCorrectionStore
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import com.example.overdex.ui.screens.observatory.formatReplayTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Exercises the real LCD viewport and case buttons with an isolated Replay fixture. */
class ReplayLcdLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactPixelLcdAtDefaultFontScale() = verifyMenuLabels(344, 1f)

    @Test fun compactPixelLcdAt130Percent() = verifyMenuLabels(344, 1.3f)

    @Test fun compactPixelLcdAt200Percent() = verifyMenuLabels(344, 2f)

    @Test fun compactMoondropLcdAtDefaultFontScale() = verifyMenuLabels(360, 1f)

    @Test fun compactMoondropLcdAt130Percent() = verifyMenuLabels(360, 1.3f)

    @Test fun compactMoondropLcdAt200Percent() = verifyMenuLabels(360, 2f)

    @Test fun fullMatchClockAndScrubberStartAtGoEvenWithLongSetup() {
        val fixtureArchive = testArchive()
        val leadIn = 197 * SECOND
        val matchStart = leadIn + SECOND
        val setup = fixtureArchive.articles.first().copy(
            articleId = "setup", monotonicTimeNanos = SECOND, payload = ArchivedRawText("Choose your party"),
        )
        val archive = fixtureArchive.copy(articles = listOf(setup) + fixtureArchive.articles.map {
            it.copy(monotonicTimeNanos = requireNotNull(it.monotonicTimeNanos) + leadIn)
        })
        val model = MatchReplayModel(archive)
        assertEquals(SECOND, model.recordingStartNanos)
        val duration = model.endNanos - matchStart
        val fixture = mountReplay(360, 1f, archive)
        lcdText("00:00.0 / ${formatReplayTime(duration)}").assertIsDisplayed()
        pressDirection("Up")
        compose.runOnIdle { assertEquals(matchStart, fixture.cursor) }

        lcdDescription("Replay timeline").performScrollTo().performTouchInput { click(center) }
        compose.runOnIdle {
            assertTrue(kotlin.math.abs(fixture.cursor - (matchStart + duration / 2)) < 250_000_000L)
        }
        openView("Menu")
        click("Playback settings")
        click("Restart Replay")
        compose.runOnIdle { assertEquals(matchStart, fixture.cursor) }
    }

    @Test fun latestPlaybackAndEventDetailsUseSeparateInitiatorColumns() {
        val archive = testArchive()
        val playerSwitch = archive.articles.first().copy(
            articleId = "player-switch", monotonicTimeNanos = 6 * SECOND,
            payload = ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323),
        )
        mountReplay(360, 1f, archive.copy(articles = archive.articles + playerSwitch))
        compose.onNode(hasText("Match start") and hasAnyAncestor(hasTestTag("replay-events-shared"))).assertIsDisplayed()
        repeat(6) { pressDirection("Down") }

        val player = compose.onNode(hasText("Active change → Camerupt") and
            hasAnyAncestor(hasTestTag("replay-events-player")))
        val opponent = compose.onNode(hasText("Active change → Alolan Ninetales") and
            hasAnyAncestor(hasTestTag("replay-events-opponent")))
        fun checkColumns() {
            player.assertIsDisplayed().assertReplayTextFits()
            opponent.assertIsDisplayed().assertReplayTextFits()
            assertTrue(player.getUnclippedBoundsInRoot().right < opponent.getUnclippedBoundsInRoot().left)
        }

        checkColumns()
        openView("Events")
        checkColumns()
    }

    @Test fun caseButtonsNavigateViewsAndTouchTimelineScrubsWithoutChangingOtherViews() {
        val fixture = mountReplay(360, 1f)
        lcdText("Paused").assertIsDisplayed()
        pressDirection("Down")
        compose.runOnIdle { assertEquals(2 * SECOND, fixture.cursor) }
        pressDirection("Up")
        compose.runOnIdle { assertEquals(SECOND, fixture.cursor) }

        focusAndActivate(lcdText("Events"))
        assertReadableAction(lcdDescription("Next replay event"))
        pressDirection("Down")
        val eventCursor = compose.runOnIdle { fixture.cursor }
        assertTrue("Down should seek to the next event on Events", eventCursor > SECOND)
        ensureVisible(lcdText("Ninetales", substring = true)).performTouchInput {
            swipe(Offset(1f, centerY), Offset(right - 1f, centerY))
        }
        compose.runOnIdle { assertEquals("Event text must not scrub Replay", eventCursor, fixture.cursor) }
        pressCaseButton("B")
        lcdText("Paused").assertIsDisplayed()
        compose.runOnIdle { assertEquals(eventCursor, fixture.cursor) }
        focusAndActivate(lcdText("Menu"))
        focusAndActivate(lcdText("Playback settings"))
        assertReadableText("Playback speed 1.0×")
        pressCaseButton("B")
        assertReadableAction(lcdText("Playback settings"))
        pressCaseButton("B")
        lcdText("Paused").assertIsDisplayed()
        compose.runOnIdle { assertEquals(eventCursor, fixture.cursor) }

        val timeline = lcdDescription("Replay timeline")
        timeline.performScrollTo().performTouchInput {
            swipe(center, Offset(right - 1f, centerY))
        }
        compose.runOnIdle { assertTrue("Dragging the timeline should advance Replay", fixture.cursor > eventCursor) }

        compose.mainClock.autoAdvance = false
        lcdText("Play").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        lcdText("Events").performClick()
        compose.mainClock.advanceTimeByFrame()
        lcdText("Playback").performClick()
        compose.mainClock.advanceTimeByFrame()
        lcdText("Playing").assertIsDisplayed()
        lcdText("Pause").performClick()
        compose.mainClock.advanceTimeByFrame()
        pressCaseButton("B")
        assertEquals(1, fixture.exits)
    }

    @Test fun replayMenuActionsReachPlaybackSettingsCorrectionsFilesAndClipTools() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("match_replay", Context.MODE_PRIVATE)
        val savedPreferences = preferences.all
        val archive = testArchive()
        val correction = ReplayTrainerCorrection(UUID.randomUUID().toString(), archive.matchId, 2 * SECOND,
            ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", authorName = "LCD test trainer",
            createdAtMillis = 0)
        val temporary = File(context.cacheDir, archive.matchId).apply { check(mkdir()) }
        val privateContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = temporary
        }
        try {
            ReplayTrainerCorrectionStore(temporary).add(correction)
            val fixture = mountReplay(360, 1f, archive, privateContext)
            openView("Menu")
            click("Playback settings")
            val speed = lcdDescription("Replay speed")
            speed.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { setSpeed ->
                assertTrue(setSpeed(2f))
            }
            compose.runOnIdle { assertEquals(2f, fixture.speed, 0f) }

            click("Reset speed to 1×")
            compose.runOnIdle { assertEquals(1f, fixture.speed, 0f) }
            val blips = preferences.getBoolean("event_blips_enabled", false)
            val sounds = preferences.getBoolean("attack_sfx_enabled", true)
            click("Event blips: ${if (blips) "On" else "Off"}")
            click("Attack sounds: ${if (sounds) "On" else "Off"}")
            compose.runOnIdle {
                assertEquals(!blips, preferences.getBoolean("event_blips_enabled", false))
                assertEquals(!sounds, preferences.getBoolean("attack_sfx_enabled", true))
            }

            pressCaseButton("B")
            click("Trainer corrections")
            click("Add event")
            compose.onNodeWithText("Add a missing event").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            click("Export corrections")
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, fixture.pickers.last().action)
            click("Import corrections")
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, fixture.pickers.last().action)
            assertTrue("application/json" in fixture.pickers.last().getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty())
            click("Pair match")
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, fixture.pickers.last().action)
            assertTrue("application/zip" in fixture.pickers.last().getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty())
            assertEquals(3, fixture.pickers.size)

            pressCaseButton("B")
            click("Clips")
            click("Mark start")
            pressCaseButton("B")
            openView("Playback")
            pressDirection("Down")
            openView("Menu")
            click("Clips")
            click("End and save")
            compose.onNodeWithText("Save replay clip").assertIsDisplayed()
            compose.onNodeWithText("CANCEL").performClick()
            click("Clip library")
            assertEquals(1, fixture.clipLibraries)
        } finally {
            val edits = preferences.edit()
            for (key in listOf("event_blips_enabled", "attack_sfx_enabled")) {
                val oldValue = savedPreferences[key] as? Boolean
                if (oldValue == null) edits.remove(key) else edits.putBoolean(key, oldValue)
            }
            edits.apply()
            temporary.deleteRecursively()
        }
    }

    private fun verifyMenuLabels(width: Int, fontScale: Float) {
        val fixture = mountReplay(width, fontScale)
        val saveScreenshots = width == 360 && fontScale == 1f
        assertReadableText("Paused")
        for (view in listOf("Playback", "Events", "Menu")) {
            if (lcdUsesViewPicker()) assertReadableAction(lcdText("Change view"))
            openView(view)
            when (view) {
                "Playback" -> listOf("−1 sec", "Play", "+1 sec").forEach { assertReadableAction(lcdText(it)) }
                "Events" -> {
                    val previous = lcdDescription("Previous replay event")
                    val next = lcdDescription("Next replay event")
                    assertReadableAction(previous)
                    assertReadableAction(next)
                    next.performClick()
                    assertEquals(5 * SECOND, compose.runOnIdle { fixture.cursor })
                    ensureVisible(lcdText("Ninetales", substring = true)).assertReplayTextFits()
                    if (saveScreenshots) {
                        captureLcd("events")
                        focusAction(previous)
                        focusAction(next)
                    }
                }
            }
            if (saveScreenshots && view != "Events") {
                captureLcd(view.lowercase())
                if (view == "Playback") {
                    val views = listOf("Playback", "Events", "Menu")
                    val transport = listOf("−1 sec", "Play", "+1 sec")
                    assertCompactPlaybackRows(views, transport)
                    (views + transport).forEach { focusAction(lcdText(it)) }
                }
            }
        }

        val sections = mapOf(
            "Playback settings" to listOf("Slower by 0.1×", "Faster by 0.1×", "Reset speed to 1×",
                "Event blips:", "Attack sounds:", "Restart Replay"),
            "Trainer corrections" to listOf("Add event", "Export corrections", "Import corrections", "Pair match"),
            "Clips" to listOf("Mark start", "End and save", "Clip library"),
            "Match details" to emptyList(),
        )
        for ((section, actions) in sections) {
            val group = lcdText(section)
            assertReadableAction(group)
            if (saveScreenshots) focusAndActivate(group) else group.performClick()
            val menuBack = lcdText("Back to Menu")
            assertReadableAction(menuBack)
            if (saveScreenshots) focusAction(menuBack)
            actions.forEach {
                val action = lcdText(it, substring = it.endsWith(":"))
                assertReadableAction(action)
                if (saveScreenshots) focusAction(action)
            }
            if (section == "Match details") assertReadableText("Estimated energy")
            pressCaseButton("B")
        }
        assertReadableAction(lcdText("Exit Replay"))
        if (saveScreenshots) focusAction(lcdText("Exit Replay"))
    }

    private fun mountReplay(
        width: Int,
        scale: Float,
        archive: MatchArchive = testArchive(),
        privateContext: Context? = null,
    ): Fixture {
        val fixture = Fixture(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = scale),
                LocalContext provides (privateContext ?: LocalContext.current),
                LocalActivityResultRegistryOwner provides fixture,
            ) {
                var lcd by remember { mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null) }
                var up: () -> Unit = {}
                var down: () -> Unit = {}
                var left: () -> Unit = {}
                var right: () -> Unit = {}
                var a: () -> Unit = {}
                var b: () -> Unit = {}
                Box(Modifier.size(width.dp, 690.dp)) {
                    ODXFiShell(showBattleOverlay = false, lcdContent = lcd, lcdEdgeToEdgeContent = true,
                        onUp = { up() }, onDown = { down() }, onLeft = { left() }, onRight = { right() },
                        onA = { a() }, onB = { b() },
                    ) {
                        MatchReplayScreen(archive,
                            onBack = { fixture.exits++ }, onLcdContentUpdate = { lcd = it },
                            onUp = { up = it }, onDown = { down = it }, onLeft = { left = it }, onRight = { right = it },
                            onA = { a = it }, onB = { b = it },
                            onPlaybackSpeedChange = { fixture.speed = it }, onOpenEditor = { fixture.clipLibraries++ },
                            pairedReplayScene = { scene, at -> fixture.cursor = at; scene },
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Paused")).fetchSemanticsNodes().isNotEmpty() }
        return fixture
    }

    private fun focusAndActivate(action: SemanticsNodeInteraction) {
        focusAction(action)
        pressCaseButton("A")
    }

    private fun focusAction(action: SemanticsNodeInteraction) {
        repeat(12) {
            if (action.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true) {
                action.assertIsDisplayed()
                assertFullyVisibleAction(action)
                return
            }
            pressDirection("Right")
        }
        error("The case buttons could not focus the LCD action")
    }

    private fun lcdUsesViewPicker(): Boolean =
        compose.onAllNodes(hasText("Change view") and hasAnyAncestor(hasTestTag(LCD_SURFACE)))
            .fetchSemanticsNodes().isNotEmpty()

    private fun openView(name: String) {
        if (lcdUsesViewPicker()) click("Change view")
        val tab = lcdText(name)
        assertReadableAction(tab)
        tab.performClick()
    }

    private fun click(name: String) = ensureVisible(lcdText(name)).performClick()

    private fun pressCaseButton(label: String) = compose.onNodeWithText(label).performClick()

    private fun pressDirection(label: String) = compose.onNodeWithContentDescription(label).performClick()

    private fun lcdText(label: String, substring: Boolean = false) =
        compose.onNode(hasText(label, substring = substring) and hasAnyAncestor(hasTestTag(LCD_SURFACE)))

    private fun lcdDescription(label: String) =
        compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag(LCD_SURFACE)))

    private fun assertReadableText(label: String) = ensureVisible(lcdText(label)).assertReplayTextFits()

    private fun assertReadableAction(action: SemanticsNodeInteraction) {
        ensureVisible(action).assertReplayTextFits()
        assertFullyVisibleAction(action)
    }

    private fun assertFullyVisibleAction(action: SemanticsNodeInteraction) {
        val bounds = action.getUnclippedBoundsInRoot()
        val visibleBounds = action.getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        val width = bounds.right - bounds.left
        assertTrue("Focused control can be fully revealed", visibleBounds.bottom - visibleBounds.top >= height - 1.dp)
        assertTrue("Control must fit within the LCD width", visibleBounds.right - visibleBounds.left >= width - 1.dp)
    }

    private fun assertCompactPlaybackRows(views: List<String>, transport: List<String>) {
        fun rowHeight(labels: List<String>): Dp {
            val bounds = labels.map { label ->
                val control = lcdText(label)
                control.assertIsDisplayed()
                assertFullyVisibleAction(control)
                control.getUnclippedBoundsInRoot()
            }
            return bounds.maxOf { it.bottom } - bounds.minOf { it.top }
        }
        val panel = compose.onNodeWithTag(LCD_SURFACE).getUnclippedBoundsInRoot()
        val height = rowHeight(views) + rowHeight(transport)
        assertTrue("Navigation and transport should use at most one third of the LCD ($height)",
            height <= (panel.bottom - panel.top) / 3 + 1.dp)
    }

    private fun captureLcd(view: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "replay-lcd-$view.png").outputStream().use {
            compose.onNodeWithTag(LCD_SURFACE).captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun ensureVisible(node: SemanticsNodeInteraction) = node.performScrollTo().assertIsDisplayed()

    private fun testArchive(): MatchArchive {
        val id = "replay-lcd-${UUID.randomUUID()}"
        fun event(key: String, time: Long, payload: ArchivedTestimonyPayload) =
            ArchivedRealityArticle(key, id, 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), time)
        return MatchArchive(matchId = id, mediaIncluded = false, articles = listOf(
            event("go", SECOND, ArchivedMatchStarted),
            event("player", 2 * SECOND, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776)),
            event("opponent", 2 * SECOND, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134)),
            event("switch", 5 * SECOND, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Alolan Ninetales", 38)),
            event("return", 11 * SECOND, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134)),
            event("end", 41 * SECOND, ArchivedMatchEnded("WIN")),
        ))
    }

    private class Fixture(context: Context) : ActivityResultRegistryOwner {
        val pickers = mutableListOf<Intent>()
        var cursor = -1L
        var speed = 1f
        var exits = 0
        var clipLibraries = 0
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                                         input: I, options: ActivityOptionsCompat?) {
                pickers += contract.createIntent(context, input)
                dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            }
        }
    }

    private companion object {
        const val LCD_SURFACE = "instrument-lcd-surface"
        const val SECOND = 1_000_000_000L
    }
}

internal fun SemanticsNodeInteraction.assertReplayTextFits(): SemanticsNodeInteraction {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue("Expected a rendered label to measure", layouts.isNotEmpty())
    for (layout in layouts) {
        val label = layout.layoutInput.text.text
        val measurement = "${layout.size} / ${layout.multiParagraph.width}×${layout.multiParagraph.height} · $label"
        for (line in 0 until layout.lineCount) {
            assertFalse("LCD text ellipsized: $measurement", layout.isLineEllipsized(line))
            // Compose's paragraph bounds are fractional while its allocated size is integral.
            assertTrue("LCD text cropped horizontally: $measurement",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("LCD text cropped vertically: $measurement", layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        assertEquals(measurement, label.length, layout.getLineEnd(layout.lineCount - 1))
    }
    return this
}
