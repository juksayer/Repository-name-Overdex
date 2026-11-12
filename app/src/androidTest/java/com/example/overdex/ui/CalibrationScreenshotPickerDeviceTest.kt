package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.CalibrationScreenshotReference
import com.example.overdex.ui.screens.CalibrationSample
import com.example.overdex.ui.screens.CalibrationScreenshotPicker
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Actual file decoding and separate preview/commit behavior on a small native viewport. */
class CalibrationScreenshotPickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun browsingShowsTheWholeSelectedPictureAndOnlyUseCommitsIt() {
        withPictures { red, blue, _ ->
            val choices = mutableListOf<String>()
            var visible by mutableStateOf(true)
            var dismissals = 0
            compose.setContent {
                MaterialTheme {
                    if (visible) CalibrationScreenshotPicker(
                        samples = listOf(red, blue), selectedId = red.reference.id,
                        screenWidth = 1080, screenHeight = 2160,
                        onChoose = { choices += it },
                        onDismiss = { dismissals++; visible = false },
                    )
                }
            }
            awaitPreview()
            compose.onNodeWithTag("calibration-screenshot-name").assertTextEquals("red-portrait.png")
            assertPreviewColor(red = true)
            val previewArtifact = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "calibration-picker-preview.png")
            previewArtifact.outputStream().use { output ->
                assertTrue(compose.onNodeWithTag("calibration-screenshot-picker").captureToImage()
                    .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            compose.onNodeWithTag("calibration-screenshot-next").performClick()
            awaitPreview()
            compose.onNodeWithTag("calibration-screenshot-name").assertTextEquals("blue-square.png")
            compose.onNodeWithTag("calibration-screenshot-details").assertTextContains("160 × 160", substring = true)
                .assertTextContains("Other phone", substring = true)
            compose.onNodeWithTag("calibration-screenshot-warning").assertTextContains("Read only", substring = true)
            compose.onNodeWithTag("calibration-screenshot-row-${blue.reference.id}").assertIsSelected()
            assertPreviewColor(red = false)
            compose.runOnIdle { assertTrue("Highlighting must not commit a reference", choices.isEmpty()) }
            compose.onNodeWithTag("calibration-screenshot-use").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(listOf(blue.reference.id), choices) }
            compose.onNodeWithTag("calibration-screenshot-previous").performClick()
            awaitPreview()
            compose.onNodeWithTag("calibration-screenshot-cancel").performClick()
            compose.onNodeWithTag("calibration-screenshot-picker").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, dismissals)
                assertEquals("Cancel cannot replace the confirmed picture", listOf(blue.reference.id), choices)
            }
        }
    }

    @Test fun unreadablePictureClearsThePriorPreviewAndUnknownDimensionsRemainReviewable() {
        withPictures { red, blue, broken ->
            val unknown = blue.copy(reference = blue.reference.copy(id = "unknown", width = 0, height = 0))
            val choices = mutableListOf<String>()
            compose.setContent {
                MaterialTheme {
                    CalibrationScreenshotPicker(listOf(red, broken, unknown), red.reference.id, 1080, 2160,
                        onChoose = { choices += it }, onDismiss = {})
                }
            }
            awaitPreview()
            assertPreviewColor(red = true)
            compose.onNodeWithTag("calibration-screenshot-next").performClick()
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onNodeWithTag("calibration-screenshot-preview-status")
                        .assertTextContains("Could not display", substring = true)
                }.isSuccess
            }
            compose.onNodeWithTag("calibration-screenshot-name").assertTextEquals("unreadable.png")
            compose.onNodeWithTag("calibration-screenshot-use").assertIsNotEnabled()
            val pixels = compose.onNodeWithTag("calibration-screenshot-preview").captureToImage().toPixelMap()
            val middle = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("An unreadable picture must not leave the previous red image behind",
                middle.red < .6f || middle.green > .2f)
            compose.onNodeWithTag("calibration-screenshot-next").performClick()
            awaitPreview()
            compose.onNodeWithTag("calibration-screenshot-warning").assertTextContains("dimensions are unknown", substring = true)
            compose.onNodeWithTag("calibration-screenshot-use").assertIsEnabled()
            assertPreviewColor(red = false)
            compose.runOnIdle { assertTrue(choices.isEmpty()) }
        }
    }

    private fun awaitPreview() {
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("calibration-screenshot-preview-status").assertTextEquals("Preview ready")
            }.isSuccess
        }
        compose.onNodeWithTag("calibration-screenshot-image").assertIsDisplayed()
        compose.waitForIdle()
    }

    private fun assertPreviewColor(red: Boolean) {
        val pixels = compose.onNodeWithTag("calibration-screenshot-preview").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        if (red) assertTrue("The selected portrait PNG must be decoded into the preview", center.red > .85f && center.blue < .15f)
        else assertTrue("The highlighted square PNG must replace the portrait", center.blue > .85f && center.red < .15f)
    }

    private fun withPictures(block: (CalibrationSample, CalibrationSample, CalibrationSample) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "screenshot-picker-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val red = File(root, "red-portrait.png")
            val blue = File(root, "blue-square.png")
            writePng(red, 120, 240, android.graphics.Color.RED)
            writePng(blue, 160, 160, android.graphics.Color.BLUE)
            val broken = File(root, "unreadable.png").apply { writeText("Not an image") }
            block(
                CalibrationSample(red, CalibrationScreenshotReference("red", red.name, 120, 240, "This phone"), "YOUR SCREENSHOT"),
                CalibrationSample(blue, CalibrationScreenshotReference("blue", blue.name, 160, 160, "Other phone"), "YOUR SCREENSHOT"),
                CalibrationSample(broken, CalibrationScreenshotReference("broken", broken.name, 120, 240), "YOUR SCREENSHOT"),
            )
        } finally { root.deleteRecursively() }
    }

    private fun writePng(file: File, width: Int, height: Int, color: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
