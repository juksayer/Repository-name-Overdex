package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.ui.screens.MainMenuPhase
import com.example.overdex.ui.screens.MainMenuScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercise the visible boot-to-directory handoff without real startup or storage. */
class MainMenuBootDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val trainer = TrainerIdentity(
        displayName = "Test Trainer",
        trainerId = UUID.fromString("12345678-1234-1234-1234-123456789abc"),
        createdAt = Instant.EPOCH,
    )

    @Test fun bootTextClearsBeforeRowsAppearOneAtATimeAndBecomeInteractive() {
        compose.mainClock.autoAdvance = false
        val rows = directoryRows(5)
        val booted = mutableStateOf(false)
        val phase = AtomicReference(MainMenuPhase.BOOT)
        val completed = AtomicInteger()
        val chosen = AtomicReference<String?>(null)
        compose.setContent {
            Box(Modifier.size(320.dp, 600.dp)) {
                MainMenuScreen(
                    hasBootedInSession = booted.value,
                    onBootComplete = { completed.incrementAndGet(); booted.value = true },
                    visibleNodes = rows,
                    selectedPath = rows.first().path,
                    trainerIdentity = trainer,
                    onPhaseChange = phase::set,
                    onNodeSelected = { chosen.set(it.path) },
                )
            }
        }

        advanceUntil("completed boot text") { textExists("TRAINER: TEST TRAINER") }
        compose.onNodeWithText("overdex boot sequence...").assertIsDisplayed()
        compose.onNodeWithText("overdex ready").assertIsDisplayed()
        compose.onNodeWithText("TRAINER: TEST TRAINER").assertIsDisplayed()
        compose.onNodeWithText("ID: 12345678").assertIsDisplayed()
        assertTrue(shownPaths().isEmpty())
        assertEquals(0, completed.get())

        advanceUntil("directory build") { phase.get() == MainMenuPhase.MENU_BUILD }
        assertBootTextAbsent()
        val prefixes = linkedSetOf<Int>()
        var previousCount = 0
        advanceUntil("directory completion") {
            val paths = shownPaths()
            assertEquals("Only a prefix of the directory may be drawn", rows.take(paths.size).map { it.path }, paths)
            assertTrue("A row must not disappear during the reveal", paths.size >= previousCount)
            previousCount = paths.size
            if (paths.isNotEmpty() && paths.size < rows.size) prefixes += paths.size
            if (phase.get() == MainMenuPhase.MENU_BUILD) {
                assertBootTextAbsent()
            }
            if (paths.size < rows.size) {
                assertEquals("Completion must wait for all rows", 0, completed.get())
            }
            phase.get() == MainMenuPhase.READY && completed.get() == 1
        }
        assertTrue("Several distinct prefixes must be visible, rather than the whole directory at once", prefixes.size >= 2)
        assertEquals(rows.map { it.path }, shownPaths())
        compose.onNodeWithTag("directory-node:${rows.last().path}").performClick()
        assertEquals(rows.last().path, chosen.get())
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals("The ready-state handoff must complete exactly once", 1, completed.get())
        assertBootTextAbsent()
    }

    @Test fun anAlreadyBootedSessionShowsTheWholeLiveDirectoryImmediately() {
        compose.mainClock.autoAdvance = false
        val rows = mutableStateOf(directoryRows(3))
        val phase = AtomicReference(MainMenuPhase.BOOT)
        val completed = AtomicInteger()
        compose.setContent {
            Box(Modifier.size(320.dp, 600.dp)) {
                MainMenuScreen(
                    hasBootedInSession = true,
                    onBootComplete = { completed.incrementAndGet() },
                    visibleNodes = rows.value,
                    selectedPath = rows.value.first().path,
                    trainerIdentity = trainer,
                    onPhaseChange = phase::set,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(MainMenuPhase.READY, phase.get())
        assertEquals(rows.value.map { it.path }, shownPaths())
        assertBootTextAbsent()

        compose.runOnIdle { rows.value = directoryRows(5) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(rows.value.map { it.path }, shownPaths())
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        assertEquals(0, completed.get())
        assertBootTextAbsent()
    }

    @Test fun directoryRefreshAndViewportResizeDoNotRestartBootOrLoseRevealedRows() {
        compose.mainClock.autoAdvance = false
        val rows = mutableStateOf(directoryRows(4))
        val height = mutableStateOf(600.dp)
        val booted = mutableStateOf(false)
        val phase = AtomicReference(MainMenuPhase.BOOT)
        val completed = AtomicInteger()
        compose.setContent {
            Box(Modifier.size(320.dp, height.value)) {
                MainMenuScreen(
                    hasBootedInSession = booted.value,
                    onBootComplete = { completed.incrementAndGet(); booted.value = true },
                    visibleNodes = rows.value,
                    selectedPath = rows.value.first().path,
                    trainerIdentity = trainer,
                    onPhaseChange = phase::set,
                )
            }
        }
        advanceUntil("first partial directory prefix") {
            phase.get() == MainMenuPhase.MENU_BUILD && shownPaths().size in 1 until rows.value.size
        }
        val alreadyDrawn = shownPaths()
        compose.runOnIdle {
            rows.value = directoryRows(6)
            height.value = 520.dp
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(MainMenuPhase.MENU_BUILD, phase.get())
        assertBootTextAbsent()
        assertEquals(alreadyDrawn, shownPaths().take(alreadyDrawn.size))

        var previousCount = alreadyDrawn.size
        advanceUntil("refreshed directory completion") {
            val paths = shownPaths()
            assertTrue("A live refresh must not reset the revealed row count", paths.size >= previousCount)
            assertEquals(rows.value.take(paths.size).map { it.path }, paths)
            previousCount = paths.size
            assertBootTextAbsent()
            if (completed.get() > 0) assertEquals(rows.value.map { it.path }, paths)
            phase.get() == MainMenuPhase.READY && completed.get() == 1
        }
        assertEquals(rows.value.map { it.path }, shownPaths())
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(1, completed.get())
    }

    private fun directoryRows(count: Int) = (1..count).map { index ->
        FlattenedNode(
            node = ActionNode("Entry $index", InstrumentCommand.OpenSearch),
            depth = 0,
            path = "/Entry $index",
            isExpanded = false,
        )
    }

    private fun shownPaths(): List<String> = compose.onAllNodes(
        SemanticsMatcher("directory row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("directory-node:") == true
        },
    ).fetchSemanticsNodes().map {
        it.config[SemanticsProperties.TestTag].removePrefix("directory-node:")
    }

    private fun textExists(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertBootTextAbsent() {
        compose.onNodeWithText("overdex boot sequence...").assertDoesNotExist()
        compose.onNodeWithText("overdex ready").assertDoesNotExist()
        compose.onNodeWithText("TRAINER: TEST TRAINER").assertDoesNotExist()
        compose.onNodeWithText("ID: 12345678").assertDoesNotExist()
    }

    private fun advanceUntil(description: String, condition: () -> Boolean) {
        repeat(500) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
        }
        fail("Did not reach $description after advancing the boot presentation")
    }
}
