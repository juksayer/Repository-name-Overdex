package com.example.overdex.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.theme.OverdexTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the actual shell tap wiring without opening trainer screens or capture. */
class DroidballAcknowledgementDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun resetJourney() = DroidballJourney.reset()

    @Test fun shellTapTiltsBlinksAndCancelsWhenTheBodyStopsBeingInteractive() {
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        DroidballJourney.reset()
        val interactive = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverdexTheme {
                ODXFiShell(isLogoInteractive = interactive.value) { }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val body = compose.onNodeWithContentDescription("Droidball")
        body.assertHasClickAction()
        val resting = body.captureToImage()
        body.performClick()
        compose.mainClock.advanceTimeBy(500L)
        assertFalse("A real shell tap visibly tilts the current body", samePixels(resting, body.captureToImage()))
        compose.mainClock.advanceTimeBy(532L)
        val blink = body.captureToImage()
        compose.mainClock.advanceTimeBy(80L)
        val openEyesAtSameTilt = body.captureToImage()
        assertFalse("The tilted body closes then reopens its eyes", samePixels(blink, openEyesAtSameTilt))
        compose.mainClock.advanceTimeBy(80L)
        assertTrue("The shell tap produces a second blink", samePixels(blink, body.captureToImage()))
        compose.mainClock.advanceTimeBy(210L)
        compose.mainClock.advanceTimeBy(1200L)
        assertTrue("The acknowledgement returns completely to rest", samePixels(resting, body.captureToImage()))

        body.performClick()
        compose.mainClock.advanceTimeBy(500L)
        compose.runOnIdle { interactive.value = false }
        compose.mainClock.advanceTimeByFrame()
        body.assertHasNoClickAction()
        assertTrue("Disabling the shell interaction immediately discards its tilt", samePixels(resting, body.captureToImage()))
        compose.mainClock.advanceTimeBy(2600L)
        compose.runOnIdle { interactive.value = true }
        compose.mainClock.advanceTimeByFrame()
        body.assertHasClickAction()
        assertTrue("Re-enabling cannot replay an old tap", samePixels(resting, body.captureToImage()))

        body.performClick()
        compose.mainClock.advanceTimeBy(450L)
        compose.runOnIdle { DroidballJourney.depart() }
        compose.mainClock.advanceTimeByFrame()
        body.assertHasNoClickAction()
        compose.runOnIdle { DroidballJourney.reset() }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("A journey takes priority and cannot leave an old acknowledgement behind",
            samePixels(resting, body.captureToImage()))
    }

    @Test fun consecutiveShellTapsDiminishAndFifthTapSleepsUntilTheNextJourney() {
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        DroidballJourney.reset()
        var confirms = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverdexTheme { ODXFiShell(isLogoInteractive = true, onA = { confirms++ }) { } }
        }
        compose.mainClock.advanceTimeByFrame()
        val body = compose.onNodeWithContentDescription("Droidball")
        val resting = body.captureToImage()
        repeat(2) {
            body.performClick()
            compose.mainClock.advanceTimeBy(500L)
            assertFalse("Each of the first two taps still tilts", samePixels(resting, body.captureToImage()))
            compose.mainClock.advanceTimeBy(2100L)
            assertTrue(samePixels(resting, body.captureToImage()))
        }

        body.performClick() // Third: upright, two blinks.
        val uprightBlink = firstChangedFrame(body, resting)
        if (!samePixels(resting, uprightBlink, ignoreEyes = true)) {
            saveFailureFrame("resting", resting)
            saveFailureFrame("third-tap", uprightBlink)
        }
        assertTrue("Third tap changes only the eyes: ${changedBounds(resting, uprightBlink)}",
            samePixels(resting, uprightBlink, ignoreEyes = true))
        compose.mainClock.advanceTimeBy(80L)
        assertTrue(samePixels(resting, body.captureToImage()))
        compose.mainClock.advanceTimeBy(80L)
        assertTrue("Third tap blinks twice", samePixels(uprightBlink, body.captureToImage()))
        compose.mainClock.advanceTimeBy(160L)

        body.performClick() // Fourth: upright, one blink.
        assertTrue(samePixels(uprightBlink, firstChangedFrame(body, resting)))
        compose.mainClock.advanceTimeBy(80L)
        assertTrue(samePixels(resting, body.captureToImage()))
        compose.mainClock.advanceTimeBy(80L)
        assertTrue("Fourth tap does not blink a second time", samePixels(resting, body.captureToImage()))
        compose.mainClock.advanceTimeBy(160L)

        body.performClick() // Fifth: close eyes, settle tilted, ignore further taps.
        compose.mainClock.advanceTimeBy(1100L)
        body.assertHasNoClickAction()
        val asleep = body.captureToImage()
        assertFalse(samePixels(resting, asleep))
        body.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(3000L)
        assertTrue("A sixth physical tap cannot wake or restart the body", samePixels(asleep, body.captureToImage()))
        compose.onNodeWithText("A").performClick()
        compose.runOnIdle { assertEquals("The instrument controls remain usable", 1, confirms) }

        compose.runOnIdle { DroidballJourney.depart() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { DroidballJourney.reset() }
        compose.mainClock.advanceTimeByFrame()
        body.assertHasClickAction()
        assertTrue("Returning from a journey clears the sleeping tap state", samePixels(resting, body.captureToImage()))
    }

    private fun firstChangedFrame(body: SemanticsNodeInteraction, resting: ImageBitmap): ImageBitmap {
        // The frame coroutine starts after composition; wait for the actual eyelid
        // frame instead of assuming a fixed device-specific first-frame delay.
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            val frame = body.captureToImage()
            if (!samePixels(resting, frame)) return frame
        }
        throw AssertionError("No blink appeared within the first 16 frames")
    }

    private fun changedBounds(first: ImageBitmap, second: ImageBitmap): String {
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        val changed = (0 until first.height).flatMap { y ->
            (0 until first.width).filter { x -> a[x, y] != b[x, y] }.map { x -> x to y }
        }
        return "${first.width}x${first.height}, changed x=${changed.minOfOrNull { it.first }}..${changed.maxOfOrNull { it.first }}, " +
            "y=${changed.minOfOrNull { it.second }}..${changed.maxOfOrNull { it.second }}"
    }

    private fun saveFailureFrame(name: String, bitmap: ImageBitmap) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        java.io.File(directory, "droidball-$name.png").outputStream().use {
            bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun samePixels(first: ImageBitmap, second: ImageBitmap, ignoreEyes: Boolean = false): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (ignoreEyes && x.toFloat() / first.width in 0.29f..0.71f &&
                y.toFloat() / first.height in 0.38f..0.54f) continue
            if (a[x, y] != b[x, y]) return false
        }
        return true
    }
}
