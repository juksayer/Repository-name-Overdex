package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.preview.DirectoryCalibrationFrame
import com.example.overdex.battle.preview.DirectoryCalibrationFrameOrigin
import com.example.overdex.battle.preview.DirectoryCropImage
import com.example.overdex.battle.preview.DirectoryCropPreviewState
import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.battle.preview.directoryCropRegion
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.DirectoryCalibrationSnapshot
import com.example.overdex.ui.components.DirectoryCropEditDirection
import com.example.overdex.ui.components.DirectoryCropEditSave
import com.example.overdex.ui.components.DirectoryCropEditor
import com.example.overdex.ui.components.DirectoryCropPreviewLcd
import com.example.overdex.ui.components.rememberDirectoryCropEditor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose gestures and source pixels, through the same editor routes as the shell buttons. */
class DirectoryCropEditorDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var editor: DirectoryCropEditor
    private val saveCalls = AtomicInteger()
    private val loadCalls = AtomicInteger()
    private val calibrationLoads = AtomicInteger()
    private val requests = mutableListOf<DirectoryCropEditSave>()
    private val calibration = BattleCalibration(playerSpeciesNameRegion = ORIGINAL)
    private val currentCalibration = AtomicReference(calibration)
    private val enabled = mutableStateOf(true)

    @Test fun reviewPromptSelectsTheFirstUnverifiedCropAndOpensTheExistingCorrectionRoute() {
        showEditor()
        compose.onNodeWithTag("directory-crop-review").performClick()
        awaitSource()
        compose.onNodeWithTag("directory-crop-player_species").assertIsSelected()
        compose.onNodeWithText("Your name · Current crop").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, saveCalls.get())
            assertTrue(editor.confirm())
        }
        compose.onNodeWithTag("directory-crop-position-editor").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-cancel").performClick()
        compose.runOnIdle { assertEquals(0, saveCalls.get()) }
    }

    @Test fun tapThenAAndDragPansRealSourcePixelsWhileBCancelsWithoutSaving() {
        showEditor()
        assertEquals(0, loadCalls.get())
        assertCenterColor("directory-crop-image-player_species", AndroidColor.GREEN)
        choosePlayerName()
        compose.onNodeWithText("Saved battle reference").assertIsDisplayed()
        compose.runOnIdle { assertTrue(editor.confirm()) } // Physical A route.
        compose.onNodeWithTag("directory-crop-position-editor").assertIsDisplayed()
        assertCenterColor("directory-crop-drag-preview", AndroidColor.RED)

        dragPreviewRight()
        compose.runOnIdle {
            val draft = requireNotNull(editor.controls.draft)
            assertTrue("Drag changes the source X coordinate", draft.region.x > ORIGINAL.x)
            assertEquals(ORIGINAL.y, draft.region.y, .0001f)
            assertEquals("Position editing cannot resize width", ORIGINAL.width, draft.region.width, 0f)
            assertEquals("Position editing cannot resize height", ORIGINAL.height, draft.region.height, 0f)
        }
        // Blue exists only outside the original red crop in the full source.
        // Stretching or shifting the historical green crop cannot pass this.
        assertCenterColor("directory-crop-drag-preview", AndroidColor.BLUE)
        compose.onNodeWithTag("directory-crop-cancel").performClick()
        compose.onNodeWithTag("directory-crop-position-editor").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(editor.editing)
            assertTrue(editor.focused)
            assertEquals(ORIGINAL, editor.snapshot?.calibration?.directoryCropRegion(DirectoryCropSlot.PLAYER_SPECIES))
            assertEquals(0, saveCalls.get())
            assertTrue(editor.back()) // A second B leaves LCD focus.
        }
        compose.waitForIdle()
        assertCenterColor("directory-crop-image-player_species", AndroidColor.GREEN)
        assertEquals("One source load is sufficient for the entire draft", 1, loadCalls.get())
    }

    @Test fun immediateTwiceAClaimsOneSaveAndConsumesFurtherAWhilePersistenceIsPending() {
        val finishSave = CompletableDeferred<Boolean>()
        showEditor(save = { _, request ->
            saveCalls.incrementAndGet()
            requests += request
            finishSave.await()
        })
        choosePlayerName()
        compose.runOnIdle {
            assertTrue(editor.confirm())
            assertTrue(editor.direction(DirectoryCropEditDirection.RIGHT))
        }
        val expected = compose.runOnIdle { requireNotNull(editor.controls.draft).region }
        compose.runOnIdle {
            assertTrue(editor.confirm())
            assertTrue(editor.confirm()) // Same UI turn, before the save coroutine can start.
            assertTrue(editor.saving)
        }
        compose.waitUntil(5_000) { editor.saving && saveCalls.get() == 1 }
        compose.runOnIdle {
            assertTrue(editor.confirm())
            assertTrue(editor.confirm())
            assertEquals(1, saveCalls.get())
            finishSave.complete(true)
        }
        compose.waitUntil(5_000) { !editor.saving && !editor.editing }
        compose.runOnIdle {
            assertEquals(listOf(DirectoryCropEditSave(DirectoryCropSlot.PLAYER_SPECIES, expected)), requests)
            assertEquals(expected, editor.snapshot?.calibration?.directoryCropRegion(DirectoryCropSlot.PLAYER_SPECIES))
            assertTrue(editor.focused)
            assertEquals("Position saved", editor.notice)
        }
    }

    @Test fun BAfterAConflictRefreshesCalibrationForTheNextDraftAndKeepsTheFrozenSource() {
        showEditor(save = { _, request ->
            saveCalls.incrementAndGet(); requests += request; false
        })
        choosePlayerName()
        val originalFrame = compose.runOnIdle { editor.frame }
        compose.runOnIdle {
            assertTrue(editor.confirm())
            assertTrue(editor.direction(DirectoryCropEditDirection.RIGHT))
        }
        compose.onNodeWithTag("directory-crop-save").performClick()
        compose.waitUntil(5_000) { saveCalls.get() == 1 && !editor.saving && editor.notice?.startsWith("Could not save") == true }
        val externallyChanged = ORIGINAL.copy(x = .35f, y = .3f)
        compose.runOnIdle {
            assertTrue("A conflict preserves the unsaved draft", editor.editing)
            currentCalibration.set(calibration.copy(playerSpeciesNameRegion = externallyChanged))
            assertTrue(editor.back())
        }
        compose.waitUntil(5_000) {
            !editor.loading && calibrationLoads.get() == 2 &&
                editor.snapshot?.calibration?.directoryCropRegion(DirectoryCropSlot.PLAYER_SPECIES) == externallyChanged
        }
        compose.runOnIdle {
            assertSame("Refreshing profile coordinates does not replace the frozen pixels", originalFrame, editor.frame)
            assertEquals(1, loadCalls.get())
            assertTrue(editor.confirm())
            assertEquals(externallyChanged, editor.controls.draft?.original)
            assertEquals(externallyChanged, editor.controls.draft?.region)
            assertEquals(1, saveCalls.get())
        }
    }

    @Test fun disablingTheEditorDuringASaveReleasesFocusAndSourceAfterPersistenceCompletes() {
        val finishSave = CompletableDeferred<Boolean>()
        showEditor(save = { _, request ->
            saveCalls.incrementAndGet(); requests += request; finishSave.await()
        })
        choosePlayerName()
        compose.runOnIdle {
            assertTrue(editor.confirm())
            assertTrue(editor.direction(DirectoryCropEditDirection.RIGHT))
        }
        compose.onNodeWithTag("directory-crop-save").performClick()
        compose.waitUntil(5_000) { editor.saving && saveCalls.get() == 1 }
        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("An in-flight save keeps its immutable draft until completion", editor.saving)
            assertNotNull(editor.snapshot)
            assertNotNull(editor.frame)
            finishSave.complete(true)
        }
        compose.waitUntil(5_000) { !editor.saving && !editor.focused && editor.frame == null && editor.snapshot == null }
        compose.runOnIdle {
            assertFalse(editor.editing)
            assertNull(editor.notice)
            assertEquals(1, saveCalls.get())
            assertEquals(1, loadCalls.get())
        }
    }

    @Test fun arrowsEnterAndSelectLcdSlotsThenMoveOneSourcePixelAndBReturnsFocus() {
        showEditor()
        compose.runOnIdle {
            assertFalse(editor.direction(DirectoryCropEditDirection.RIGHT))
            assertFalse(editor.back())
            assertFalse(editor.enter(atLastVisibleEntry = false))
            assertTrue(editor.enter(atLastVisibleEntry = true))
        }
        awaitSource()
        compose.onNodeWithTag("directory-crop-player_species").assertIsSelected()
        compose.runOnIdle { assertTrue(editor.direction(DirectoryCropEditDirection.RIGHT)) }
        compose.onNodeWithTag("directory-crop-opponent_species").assertIsSelected()
        compose.runOnIdle {
            assertTrue(editor.direction(DirectoryCropEditDirection.LEFT))
            assertTrue(editor.confirm())
            assertTrue(editor.direction(DirectoryCropEditDirection.RIGHT))
            assertTrue(editor.direction(DirectoryCropEditDirection.DOWN))
            val moved = requireNotNull(editor.controls.draft)
            assertEquals(ORIGINAL.x + 1f / SOURCE_WIDTH, moved.region.x, .00001f)
            assertEquals(ORIGINAL.y + 1f / SOURCE_HEIGHT, moved.region.y, .00001f)
            assertTrue(editor.back())
            assertTrue(editor.focused)
            assertFalse(editor.editing)
            assertTrue(editor.back())
            assertFalse(editor.focused)
            assertFalse(editor.back())
            assertEquals(0, saveCalls.get())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("directory-crop-player_species").assertIsNotSelected()
    }

    private fun showEditor(
        save: suspend (DirectoryCalibrationSnapshot, DirectoryCropEditSave) -> Boolean = { _, request ->
            saveCalls.incrementAndGet(); requests += request; true
        },
    ) {
        val fullSource = Bitmap.createBitmap(SOURCE_WIDTH, SOURCE_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, if (x < 60) AndroidColor.RED else AndroidColor.BLUE)
            }
        }
        val historical = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.GREEN) }
        val state = DirectoryCropPreviewState("historical-match", mapOf(
            DirectoryCropSlot.PLAYER_SPECIES to DirectoryCropImage(historical, 123, "original-crop")))
        compose.setContent {
            editor = rememberDirectoryCropEditor(
                enabled = enabled.value,
                loadFrame = {
                    loadCalls.incrementAndGet()
                    DirectoryCalibrationFrame(fullSource, DirectoryCalibrationFrameOrigin.SAVED_BATTLE_REFERENCE)
                },
                loadCalibration = {
                    calibrationLoads.incrementAndGet()
                    DirectoryCalibrationSnapshot(currentCalibration.get(), "test-profile")
                },
                saveCalibration = save,
            )
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = 1f)) {
                Box(Modifier.size(320.dp, 200.dp).background(Color.Black)) {
                    DirectoryCropPreviewLcd(state, editor = editor)
                }
            }
        }
    }

    private fun choosePlayerName() {
        compose.onNodeWithTag("directory-crop-player_species").performClick().assertIsSelected()
        awaitSource()
    }

    private fun awaitSource() {
        compose.waitUntil(5_000) { editor.frame != null && editor.snapshot != null && !editor.loading }
        compose.waitForIdle()
    }

    private fun dragPreviewRight() {
        compose.onNodeWithTag("directory-crop-drag-preview").performTouchInput {
            val start = Offset(width * .15f, height * .5f)
            swipe(start, start + Offset(height * .9f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
    }

    private fun assertCenterColor(tag: String, color: Int) {
        val rendered = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        assertEquals("$tag displays the expected original source color", color,
            rendered.getPixel(rendered.width / 2, rendered.height / 2))
    }

    private companion object {
        const val SOURCE_WIDTH = 200
        const val SOURCE_HEIGHT = 200
        val ORIGINAL = AnchorRegion(x = .1f, y = .2f, width = .2f, height = .2f)
    }
}
