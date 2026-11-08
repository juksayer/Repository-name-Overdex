package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.overdex.battle.preview.DirectoryCalibrationFrame
import com.example.overdex.battle.preview.DirectoryCalibrationFrameOrigin
import com.example.overdex.battle.preview.DirectoryCalibrationScreenGeometry
import com.example.overdex.battle.preview.DirectoryCropPreviewState
import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.data.BattleCalibration
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import com.example.overdex.model.navigation.TreeState
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.*
import com.example.overdex.ui.screens.MainMenuScreen
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual shell taps/holds, the production LCD controller, and the existing real crop editor. */
class DirectoryLcdModeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var lcd: DirectoryLcdController
    private lateinit var cropEditor: DirectoryCropEditor
    private lateinit var directoryTree: InstrumentTree
    // The shell subcomposes its CRT/LCD. Their state reads need not re-run the
    // harness parent's SideEffect, so inspect the live tree rather than a copied snapshot.
    private val treeState: TreeState get() = directoryTree.getState()
    private val sourceLoads = AtomicInteger()
    private val saves = AtomicInteger()
    private val unhandledCommands = mutableListOf<InstrumentCommand>()
    private val navigationTrace = mutableListOf<String>()

    @Test fun normalDownAtTheLastEntryDoesNotEnterCalibrationAndAExplicitlyOpensIt() {
        showDirectory()
        compose.onNodeWithTag("directory-selection-preview").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-preview").assertDoesNotExist()
        pressDownAndExpect("/BATTLE")
        pressDownAndExpect("/Calibration")
        repeat(4) { pressDownAndExpect("/Calibration") }
        compose.runOnIdle {
            assertEquals("/Calibration", treeState.selectedPath)
            assertFalse(lcd.calibrationOpen)
            assertFalse(cropEditor.focused)
            assertEquals("Ordinary directory previews must not decode a full battle frame", 0, sourceLoads.get())
        }
        press("A")
        awaitSource()
        compose.onNodeWithTag("directory-selection-preview").assertDoesNotExist()
        compose.onNodeWithTag("directory-crop-preview").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-player_species").assertIsSelected()
        press("A")
        compose.onNodeWithTag("directory-crop-position-editor").assertIsDisplayed()
        press("Right")
        compose.runOnIdle { assertTrue(cropEditor.controls.draft?.dirty == true) }
        press("B") // Cancel the position draft, retaining the calibration preview.
        compose.runOnIdle { assertTrue(lcd.calibrationOpen); assertFalse(cropEditor.editing) }
        press("B") // Return to the selected directory utility.
        compose.onNodeWithTag("directory-selection-preview").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-preview").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(lcd.calibrationOpen)
            assertEquals("/Calibration", treeState.selectedPath)
            assertEquals("Cancelling must never write a calibration", 0, saves.get())
            assertTrue(unhandledCommands.isEmpty())
        }
    }

    @Test fun holdingALaunchShortcutOpensCalibrationWithoutAlsoLaunchingDroidball() {
        showDirectory()
        compose.onNodeWithText("A", substring = false).performTouchInput { longClick(durationMillis = 800) }
        awaitSource()
        compose.runOnIdle {
            assertEquals("/Launch Droidball", treeState.selectedPath)
            assertTrue(lcd.calibrationOpen)
            assertEquals(DirectoryCropSlot.PLAYER_SPECIES, cropEditor.controls.selectedSlot)
            assertTrue("The release after Hold A must not also trigger the short Launch action", unhandledCommands.isEmpty())
        }
        compose.onNodeWithTag("directory-crop-preview").assertIsDisplayed()
        press("Up") // The first slot's Up returns to the selection preview.
        compose.onNodeWithTag("directory-selection-preview").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-preview").assertDoesNotExist()
        compose.runOnIdle { assertFalse(lcd.calibrationOpen) }
        press("A")
        compose.runOnIdle {
            assertEquals("A normal tap must keep its existing launch action", listOf(InstrumentCommand.LaunchDroidball), unhandledCommands)
            assertFalse(lcd.calibrationOpen)
            assertEquals(0, saves.get())
        }
    }

    @Test fun pendingSaveKeepsTheModeAndDraftUntilOneWriteCompletes() {
        val finishSave = CompletableDeferred<Boolean>()
        showDirectory(save = { finishSave.await() })
        compose.onNodeWithText("A", substring = false).performTouchInput { longClick(durationMillis = 800) }
        awaitSource()
        press("A"); press("Right"); press("A")
        compose.waitUntil(5_000) { cropEditor.saving && saves.get() == 1 }
        compose.runOnIdle {
            val pendingDraft = requireNotNull(cropEditor.controls.draft)
            val pendingFrame = cropEditor.frame
            val pendingSnapshot = cropEditor.snapshot
            assertFalse("A pending calibration write cannot close its LCD", lcd.close(cropEditor))
            assertTrue(lcd.back(cropEditor))
            assertTrue(lcd.direction(DirectoryCropEditDirection.UP, cropEditor))
            assertTrue(lcd.holdA(InstrumentCommand.LaunchDroidball, cropEditor))
            assertTrue(lcd.activate(InstrumentCommand.OpenDirectoryCalibration, cropEditor))
            repeat(3) { assertTrue(lcd.confirm(cropEditor)) }
            assertTrue(lcd.calibrationOpen)
            assertTrue(cropEditor.focused)
            assertTrue(cropEditor.saving)
            assertEquals("Pending navigation must retain the claimed immutable draft", pendingDraft, cropEditor.controls.draft)
            assertSame(pendingFrame, cropEditor.frame)
            assertSame(pendingSnapshot, cropEditor.snapshot)
            assertEquals("Repeated A must not claim another save", 1, saves.get())
            assertTrue("The calibration shortcut must never invoke the launch workflow", unhandledCommands.isEmpty())
        }
        compose.onNodeWithTag("directory-crop-position-editor").assertIsDisplayed()
        compose.runOnIdle { finishSave.complete(true) }
        compose.waitUntil(5_000) { !cropEditor.saving && !cropEditor.editing }
        compose.runOnIdle {
            assertTrue(lcd.calibrationOpen)
            assertEquals(1, saves.get())
        }
        press("B")
        compose.onNodeWithTag("directory-selection-preview").assertIsDisplayed()
        compose.runOnIdle { assertFalse(lcd.calibrationOpen) }
        press("A")
        compose.runOnIdle { assertEquals(listOf(InstrumentCommand.LaunchDroidball), unhandledCommands) }
    }

    private fun showDirectory(save: suspend () -> Boolean = { true }) {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        compose.setContent {
            val tree = remember { InstrumentTree(listOf(
                ActionNode("Launch Droidball", InstrumentCommand.LaunchDroidball),
                DirectoryNode("BATTLE", listOf(
                    ActionNode("Current Team", InstrumentCommand.OpenCurrentTeam),
                    ActionNode("Match History", InstrumentCommand.OpenBattleHistory),
                )),
                ActionNode("Calibration", InstrumentCommand.OpenDirectoryCalibration),
            )) }
            var state by remember { mutableStateOf(tree.getState()) }
            val mode = remember { DirectoryLcdController() }
            val editor = rememberDirectoryCropEditor(enabled = mode.calibrationOpen,
                loadFrame = {
                    sourceLoads.incrementAndGet()
                    DirectoryCalibrationFrame(source, DirectoryCalibrationFrameOrigin.SAVED_BATTLE_REFERENCE,
                        sourceGeometry = DirectoryCalibrationScreenGeometry(200, 200))
                },
                loadCalibration = { DirectoryCalibrationSnapshot(BattleCalibration(), "isolated-device-test",
                    DirectoryCalibrationScreenGeometry(200, 200), profileMatchesDevice = true) },
                saveCalibration = { _, _ -> saves.incrementAndGet(); save() })
            SideEffect { lcd = mode; cropEditor = editor; directoryTree = tree }
            fun update(action: () -> Unit) { action(); state = tree.getState() }
            fun selectedCommand() = (state.visibleNodes.firstOrNull { it.path == state.selectedPath }?.node as? ActionNode)?.command
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "main_menu") {
                ODXFiShell(showBattleOverlay = false,
                    lcdContent = {
                        if (mode.calibrationOpen) DirectoryCropPreviewLcd(DirectoryCropPreviewState(), editor = editor)
                        else DirectorySelectionPreviewLcd(state.visibleNodes, state.selectedPath)
                    }, lcdEdgeToEdgeContent = true,
                    onUp = { if (!mode.direction(DirectoryCropEditDirection.UP, editor)) update { tree.moveSelection(-1) } },
                    onDown = {
                        val before = tree.getState().selectedPath
                        val calibrationConsumed = mode.direction(DirectoryCropEditDirection.DOWN, editor)
                        if (!calibrationConsumed) update { tree.moveSelection(1) }
                        navigationTrace += "Down: $before -> ${tree.getState().selectedPath}; calibrationConsumed=$calibrationConsumed"
                    },
                    onLeft = { if (!mode.direction(DirectoryCropEditDirection.LEFT, editor)) update { tree.collapseOrParent() } },
                    onRight = { if (!mode.direction(DirectoryCropEditDirection.RIGHT, editor)) update { tree.openOrEnterSelected() } },
                    onA = {
                        if (!mode.confirm(editor)) update {
                            tree.executeSelected()?.let { command -> if (!mode.activate(command, editor)) unhandledCommands += command }
                        }
                    },
                    onALong = { mode.holdA(selectedCommand(), editor) },
                    onB = { if (!mode.back(editor)) update { tree.collapseOrParent() } },
                ) {
                    MainMenuScreen(true, {}, state.visibleNodes, if (mode.calibrationOpen) "" else state.selectedPath)
                }
            }
        }
    }

    private fun awaitSource() {
        compose.waitUntil(5_000) { cropEditor.frame != null && cropEditor.snapshot != null && !cropEditor.loading }
        compose.waitForIdle()
    }

    private fun press(label: String) {
        val button = if (label in listOf("Up", "Down", "Left", "Right")) compose.onNodeWithContentDescription(label)
            else compose.onNodeWithText(label, substring = false)
        button.performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun pressDownAndExpect(path: String) {
        val beforeEvents = compose.runOnIdle { navigationTrace.size }
        press("Down")
        val evidence = compose.runOnIdle { treeState.selectedPath to navigationTrace.toList() }
        if (evidence.first != path || evidence.second.size != beforeEvents + 1) {
            val tree = compose.onRoot(useUnmergedTree = true).printToString()
            fail("A physical Down tap must navigate exactly once to $path; selected=${evidence.first}; " +
                "callbacksBefore=$beforeEvents; trace=${evidence.second}\n$tree")
        }
        // Check the actual CRT selection as well as the backing tree state.
        compose.onNodeWithTag("directory-node:$path").assertIsSelected()
    }
}
