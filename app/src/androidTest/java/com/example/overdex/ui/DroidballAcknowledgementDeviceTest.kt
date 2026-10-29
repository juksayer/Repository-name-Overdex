package com.example.overdex.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.theme.OverdexTheme
import org.junit.After
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

    private fun samePixels(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (a[x, y] != b[x, y]) return false
        }
        return true
    }
}
