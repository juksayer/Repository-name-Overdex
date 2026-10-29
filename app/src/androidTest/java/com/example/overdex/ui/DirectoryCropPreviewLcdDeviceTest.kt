package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.preview.DirectoryCropImage
import com.example.overdex.battle.preview.DirectoryCropPreviewState
import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.ui.components.DirectoryCropPreviewLcd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class DirectoryCropPreviewLcdDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyAndLoadingStatesDoNotInventCaptureTime() {
        val state = mutableStateOf(DirectoryCropPreviewState(isLoading = true))
        showPreview(state)
        compose.onNodeWithText("Loading captured crops…").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-preview-timestamp").assertDoesNotExist()

        compose.runOnIdle { state.value = DirectoryCropPreviewState() }
        compose.onNodeWithText("Record a battle to check calibration").assertIsDisplayed()
        compose.onNodeWithTag("directory-crop-preview-timestamp").assertDoesNotExist()
    }

    @Test fun absentCropAndUnavailableImageRemainDistinctWithLastCapturedTime() {
        val image = quadrantBitmap()
        val state = mutableStateOf(DirectoryCropPreviewState(matchId = "recorded-match", crops = mapOf(
            DirectoryCropSlot.PLAYER_SPECIES to DirectoryCropImage(null, CAPTURE_TIME + 2_000, "missing-image"),
            DirectoryCropSlot.OPPONENT_HP to DirectoryCropImage(image, CAPTURE_TIME, "saved-hp"),
        )))
        showPreview(state)
        compose.onNodeWithTag(slotTag(DirectoryCropSlot.PLAYER_SPECIES)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Image unavailable"))
        compose.onNodeWithTag(slotTag(DirectoryCropSlot.PLAYER_HP)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not captured"))
        compose.onNodeWithText("Image unavailable").assertIsDisplayed()
        compose.onNodeWithTag(imageTag(DirectoryCropSlot.PLAYER_SPECIES)).assertDoesNotExist()
        compose.onNodeWithTag(imageTag(DirectoryCropSlot.OPPONENT_HP)).assertIsDisplayed()
        val timestamp = "Last captured ${formatTime(CAPTURE_TIME + 2_000)}"
        compose.onNodeWithText(timestamp).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText(timestamp).assertIsDisplayed()
    }

    @Test fun sevenUntintedCropsFitTheExistingLcdWithoutStretching() {
        val image = quadrantBitmap()
        val state = mutableStateOf(DirectoryCropPreviewState(matchId = "complete-match", crops =
            DirectoryCropSlot.entries.associateWith { slot ->
                DirectoryCropImage(image, CAPTURE_TIME + slot.ordinal * 1_000, "article-${slot.name}")
            }))
        showPreview(state)
        val stage = compose.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        val slots = DirectoryCropSlot.entries.associateWith { slot ->
            val bounds = compose.onNodeWithTag(slotTag(slot)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertContained(stage, bounds)
            val imageBounds = compose.onNodeWithTag(imageTag(slot)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertContained(bounds, imageBounds)
            assertTrue("$slot has usable image height", imageBounds.height > 10f)
            bounds
        }
        val pairs = listOf(
            DirectoryCropSlot.PLAYER_SPECIES to DirectoryCropSlot.OPPONENT_SPECIES,
            DirectoryCropSlot.PLAYER_HP to DirectoryCropSlot.OPPONENT_HP,
            DirectoryCropSlot.PLAYER_BALLS to DirectoryCropSlot.OPPONENT_BALLS,
        )
        pairs.forEach { (player, opponent) ->
            assertTrue("Sides stay separate", slots.getValue(player).right <= slots.getValue(opponent).left)
        }
        val rowOrder = listOf(DirectoryCropSlot.PLAYER_SPECIES, DirectoryCropSlot.PLAYER_HP,
            DirectoryCropSlot.PLAYER_BALLS, DirectoryCropSlot.ANNOUNCEMENT)
        rowOrder.zipWithNext().forEach { (above, below) ->
            assertTrue("Crop rows stay separate", slots.getValue(above).bottom <= slots.getValue(below).top)
        }
        assertTrue("Announcement spans both columns",
            slots.getValue(DirectoryCropSlot.ANNOUNCEMENT).width > slots.getValue(DirectoryCropSlot.PLAYER_SPECIES).width * 1.9f)
        compose.onNodeWithText("Last captured ${formatTime(CAPTURE_TIME + 6_000)}").assertIsDisplayed()

        val rendered = compose.onNodeWithTag(imageTag(DirectoryCropSlot.ANNOUNCEMENT))
            .captureToImage().asAndroidBitmap()
        val scale = min(rendered.width / image.width.toFloat(), rendered.height / image.height.toFloat())
        val width = image.width * scale
        val height = image.height * scale
        val left = (rendered.width - width) / 2f
        val top = (rendered.height - height) / 2f
        fun pixel(x: Float, y: Float) = rendered.getPixel((left + width * x).roundToInt(), (top + height * y).roundToInt())
        assertEquals("Red remains red", AndroidColor.RED, pixel(.25f, .25f))
        assertEquals("Blue remains blue", AndroidColor.BLUE, pixel(.75f, .25f))
        assertEquals("Green remains green", AndroidColor.GREEN, pixel(.25f, .75f))
        assertEquals("Yellow remains yellow", AndroidColor.YELLOW, pixel(.75f, .75f))
        assertTrue("Wide slot leaves room beside the fitted image", left > 2f)
        assertEquals("Fit preserves the black side margin", AndroidColor.BLACK,
            rendered.getPixel((left / 2f).roundToInt(), rendered.height / 2))
    }

    private fun showPreview(state: MutableState<DirectoryCropPreviewState>) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, fontScale = 1f)) {
                Box(Modifier.size(240.dp, 130.dp).background(Color(0xFF121510)).testTag(STAGE)) {
                    DirectoryCropPreviewLcd(state.value)
                }
            }
        }
    }

    private fun quadrantBitmap(): Bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) setPixel(x, y, when {
            y < height / 2 && x < width / 2 -> AndroidColor.RED
            y < height / 2 -> AndroidColor.BLUE
            x < width / 2 -> AndroidColor.GREEN
            else -> AndroidColor.YELLOW
        })
    }

    private fun assertContained(parent: Rect, child: Rect) {
        assertTrue("$child fits inside $parent", child.left >= parent.left && child.top >= parent.top &&
            child.right <= parent.right && child.bottom <= parent.bottom)
    }

    private fun formatTime(time: Long): String {
        val locale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
        return SimpleDateFormat("HH:mm:ss", locale).format(Date(time))
    }

    private fun slotTag(slot: DirectoryCropSlot) = "directory-crop-${slot.name.lowercase(Locale.ROOT)}"
    private fun imageTag(slot: DirectoryCropSlot) = "directory-crop-image-${slot.name.lowercase(Locale.ROOT)}"

    private companion object {
        const val STAGE = "directory-lcd-stage"
        const val CAPTURE_TIME = 1_793_299_200_000L
    }
}
