package com.example.overdex.ui

import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.BattleOverlay
import com.example.overdex.ui.components.LiveQteCalibrationWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises calibration UI without changing the device's saved calibration or recording a match. */
class LiveExitCalibrationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortTapExposesExitControlAndLongPressStillDragsHud() {
        var openings = 0
        var distance = Offset.Zero
        val presentation = DroidballOverlayPresentation
        try {
            presentation.reset()
            presentation.recordBattleBadgePresent()
            presentation.showBattleHud()
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    BattleOverlay(panelWidthPx = 415,
                        onDrag = { x, y -> distance += Offset(x, y) },
                        onExitIconCalibration = { openings++ })
                }
            }
            compose.onNodeWithText("EXIT ICON BOX").assertDoesNotExist()
            compose.onNodeWithContentDescription("Battle HUD").performTouchInput { click() }
            compose.onNodeWithText("EXIT ICON BOX").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, openings) }
            compose.onNodeWithText("EXIT ICON BOX").assertDoesNotExist()
            compose.onNodeWithContentDescription("Battle HUD").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(25f, 15f))
                up()
            }
            compose.runOnIdle { assertTrue("Long press still moves the HUD", distance.getDistance() > 0f) }
            compose.onNodeWithText("EXIT ICON BOX").assertDoesNotExist()
            compose.runOnIdle { assertEquals("Dragging does not open calibration", 1, openings) }
        } finally { presentation.reset() }
    }

    @Test fun exitGuideHasOwnLabelsAndSavesWithoutOfferingQteSampling(): Unit {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var guide: LiveQteCalibrationWindow? = null
        var saved: AnchorRegion? = null
        var screenWidth = 0
        val initial = AnchorRegion(.03f, .16f, .13f, .07f)
        try {
            compose.runOnUiThread {
                val activity = compose.activity
                val manager = activity.getSystemService(WindowManager::class.java)
                val bounds = manager.maximumWindowMetrics.bounds
                screenWidth = bounds.width()
                activity.setContentView(object : View(activity) {
                    override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(31, 76, 58)) }
                })
                guide = LiveQteCalibrationWindow(activity, manager, activity, activity, activity, scope,
                    bounds.width(), bounds.height(), initial,
                    saveRegion = { saved = it; true }, onClosed = { guide = null },
                    calibrationTitle = "Exit Icon", nudgeDescriptionPrefix = "Exit Icon", samplingEnabled = false)
                guide!!.show()
            }
            compose.onNodeWithText("EXIT ICON · drag panel").assertIsDisplayed()
            compose.onNodeWithContentDescription("Exit Icon guide").assertExists()
            compose.onNodeWithText("SAMPLE 10s").assertDoesNotExist()
            compose.onNodeWithText("SAVED 0").assertDoesNotExist()
            compose.onNodeWithText("QTE", substring = true).assertDoesNotExist()
            compose.onNodeWithText("ADJUST").performClick()
            compose.onNodeWithContentDescription("Exit Icon right").performClick()
            compose.onNodeWithText("SAVE & CLOSE").performClick()
            compose.waitUntil(5_000) { saved != null && guide == null }
            assertEquals(initial.x * screenWidth + 1f, saved!!.x * screenWidth, .01f)
            assertEquals(initial.y, saved!!.y, .00001f)
            assertEquals(initial.width, saved!!.width, .00001f)
            assertEquals(initial.height, saved!!.height, .00001f)
        } finally {
            compose.runOnUiThread { guide?.close() }
            scope.cancel()
        }
    }
}
