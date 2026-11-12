package com.example.overdex.ui

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.observation.QteIconSamplingStatus
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.LiveQteCalibrationWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Actual windows and source-space controls, with no service, match, or user-profile writes. */
class OpponentHandCalibrationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun handGuideUsesWholeDisplayCoordinatesAndHidesForSampling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val initial = BattleCalibration().opponentTrainerHandRegion
        val saved = AtomicReference<AnchorRegion?>()
        val sampled = AtomicReference<AnchorRegion?>()
        val latestDraft = AtomicReference<AnchorRegion?>()
        val closed = AtomicBoolean(false)
        val sampling = MutableStateFlow(QteIconSamplingStatus())
        val background = Color.rgb(31, 76, 58)
        var guide: LiveQteCalibrationWindow? = null
        lateinit var bounds: Rect

        compose.runOnUiThread {
            val activity = compose.activity
            val manager = activity.getSystemService(WindowManager::class.java)
            bounds = manager.maximumWindowMetrics.bounds
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(background) }
            })
            guide = LiveQteCalibrationWindow(
                activity, manager, activity, activity, activity, scope,
                bounds.width(), bounds.height(), initial,
                saveRegion = { saved.set(it); true },
                onClosed = { closed.set(true) },
                sampleRegion = {
                    sampled.set(it)
                    sampling.value = QteIconSamplingStatus(sampling = true, saved = 3)
                    true
                },
                samplingStatus = sampling,
                onRegionChanged = { latestDraft.set(it) },
                title = "OPPONENT HAND / POKÉ BALL",
                guideDescription = "Opponent Hand / Poké Ball guide",
                controlDescription = "Opponent hand",
                hideWhileSampling = true,
            )
            guide!!.show()
        }

        fun assertVisibleOutline(region: AnchorRegion) {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            // Overlay surface transactions can finish after Compose becomes idle.
            SystemClock.sleep(150)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(instrumentation.targetContext.cacheDir, "opponent-hand-guide.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val x = (region.x * bounds.width()).roundToInt()
                val y = (region.y * bounds.height()).roundToInt()
                val width = (region.width * bounds.width()).roundToInt()
                val height = (region.height * bounds.height()).roundToInt()
                // This uses physical display coordinates directly: a system-bar
                // inset must not shift the guessed Y=600/2400 guide downward.
                assertTrue("Thin purple edge at the whole-display position",
                    (y - 2..y + 2).any { row -> (x..x + minOf(12, width - 1)).any { col ->
                        val pixel = image.getPixel(col, row)
                        Color.red(pixel) > Color.green(pixel) && Color.blue(pixel) > Color.green(pixel)
                    } })
                assertEquals("Outline leaves the observation interior clear", background,
                    image.getPixel(x + width / 2, y + height / 2))
                assertEquals("Border remains thin", background,
                    image.getPixel(x + minOf(5, width / 2), y + minOf(5, height / 2)))
            } finally { image.recycle() }
        }

        try {
            compose.onNodeWithText("OPPONENT HAND / POKÉ BALL · drag panel").assertIsDisplayed()
            compose.onNodeWithContentDescription("Opponent Hand / Poké Ball guide").assertIsDisplayed()
            assertVisibleOutline(initial)
            assertEquals(initial, latestDraft.get())

            compose.onNodeWithText("ADJUST").performClick()
            compose.onNodeWithContentDescription("Opponent hand left").performClick()
            compose.onNodeWithContentDescription("Opponent hand down").performClick()
            val moved = requireNotNull(latestDraft.get())
            assertEquals(initial.x * bounds.width() - 1f, moved.x * bounds.width(), .01f)
            assertEquals(initial.y * bounds.height() + 1f, moved.y * bounds.height(), .01f)
            assertEquals(initial.width, moved.width)
            assertEquals(initial.height, moved.height)

            compose.onNodeWithText("MOVE").performClick()
            compose.onNodeWithText("SIZE").assertIsDisplayed()
            compose.onNodeWithContentDescription("Opponent hand right").performClick()
            compose.onNodeWithContentDescription("Opponent hand down").performClick()
            val resized = requireNotNull(latestDraft.get())
            assertEquals(moved.x, resized.x)
            assertEquals(moved.y, resized.y)
            assertEquals(initial.width * bounds.width() + 1f, resized.width * bounds.width(), .01f)
            assertEquals(initial.height * bounds.height() + 1f, resized.height * bounds.height(), .01f)
            compose.onNodeWithText("VIEW ONLY").performClick()
            assertVisibleOutline(resized)

            val sampleButton = compose.onNodeWithText("SAMPLE 10s").fetchSemanticsNode()
            val panelPoint = sampleButton.positionOnScreen + androidx.compose.ui.geometry.Offset(
                sampleButton.size.width / 2f, sampleButton.size.height / 2f)
            compose.onNodeWithText("SAMPLE 10s").performClick()
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(150)
            assertEquals("Sample receives the current unsaved draft", resized, sampled.get())
            assertNull("Sampling must not save calibration", saved.get())
            val hiddenImage = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val x = (resized.x * bounds.width()).roundToInt()
                val y = (resized.y * bounds.height()).roundToInt()
                assertEquals("Guide outline is absent from source pixels while sampling", background,
                    hiddenImage.getPixel(x, y))
                assertEquals("Controls also leave the sampled game surface", background,
                    hiddenImage.getPixel(panelPoint.x.roundToInt(), panelPoint.y.roundToInt()))
            } finally { hiddenImage.recycle() }

            compose.runOnUiThread { sampling.value = QteIconSamplingStatus(sampling = false, saved = 3) }
            compose.onNodeWithText("OPPONENT HAND / POKÉ BALL · drag panel").assertIsDisplayed()
            compose.onNodeWithText("SAVED 3").assertIsDisplayed()
            assertVisibleOutline(resized)
            compose.onNodeWithText("SAVE & CLOSE").performClick()
            compose.waitUntil(5_000) { saved.get() != null && closed.get() }
            assertEquals("Save keeps the latest draft exactly", resized, saved.get())
        } finally {
            compose.runOnUiThread { guide?.close() }
            scope.cancel()
        }
    }
}
