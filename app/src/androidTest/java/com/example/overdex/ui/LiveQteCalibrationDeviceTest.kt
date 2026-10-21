package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.droidballOverlayWindowFlags
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.BattleOverlay
import com.example.overdex.ui.components.LiveQteCalibrationWindow
import com.example.overdex.ui.screens.MatchCalibrationScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.overdex.battle.observation.QteIconSamplingStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Exercises actual overlay windows and touch routing, without starting a match or changing saved boxes. */
class LiveQteCalibrationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun guideUsesAbsoluteCoordinatesAndViewOnlyPassesTouchesThrough(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val touches = AtomicInteger()
        var guide: LiveQteCalibrationWindow? = null
        var saved: AnchorRegion? = null
        var sampled: AnchorRegion? = null
        val sampling = MutableStateFlow(QteIconSamplingStatus())
        lateinit var hud: ComposeView
        lateinit var manager: WindowManager
        lateinit var bounds: android.graphics.Rect
        compose.runOnUiThread {
            val activity = compose.activity
            manager = activity.getSystemService(WindowManager::class.java)
            bounds = manager.maximumWindowMetrics.bounds
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(31, 76, 58)) }
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.action == MotionEvent.ACTION_UP) touches.incrementAndGet()
                    return true
                }
            })
            DroidballOverlayPresentation.reset()
            DroidballOverlayPresentation.showBattleHud()
            hud = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent { BattleOverlay(panelWidthPx = 415) }
            }
            guide = LiveQteCalibrationWindow(activity, manager, activity, activity, activity, scope,
                bounds.width(), bounds.height(), AnchorRegion(0.375f, 0.45f, 0.25f, 0.15f),
                saveRegion = { saved = it; true }, onClosed = { guide = null },
                sampleRegion = { sampled = it; sampling.value = QteIconSamplingStatus(true, 3); true },
                samplingStatus = sampling)

            manager.addView(hud, WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, droidballOverlayWindowFlags(false),
                android.graphics.PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; x = bounds.width() - 440; y = 300
            })
        }
        fun tapScreen(x: Float, y: Float) {
            compose.waitForIdle()
            // WindowManager flags reach InputDispatcher via a surface transaction,
            // independently of Compose becoming idle after a semantic button click.
            instrumentation.waitForIdleSync()
            SystemClock.sleep(150)
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
            try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up) }
            finally { down.recycle(); up.recycle() }
            compose.waitForIdle()
        }
        try {
            compose.runOnUiThread { guide!!.show() }
            compose.onNodeWithText("VIEW ONLY · taps pass through box").assertIsDisplayed()
            compose.waitForIdle()
            val image = instrumentation.uiAutomation.takeScreenshot()!!
            try {
                File(instrumentation.targetContext.cacheDir, "qte-live-guide.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                // Look at the real screen, not Compose root coordinates: adding
                // the status-bar inset would put the purple edge 84 px too low.
                val x = (bounds.width() * .375f).toInt()
                val y = (bounds.height() * .45f).toInt()
                assertTrue((y - 2..y + 2).any { row -> (x..x + 12).any { col ->
                    val pixel = image.getPixel(col, row)
                    Color.red(pixel) > Color.green(pixel) && Color.blue(pixel) > Color.green(pixel)
                } })
                assertEquals("Guide does not black out its interior", Color.rgb(31, 76, 58),
                    image.getPixel(bounds.centerX(), (bounds.height() * .525f).toInt()))
            } finally { image.recycle() }
            val x = bounds.width() * .5f
            val y = bounds.height() * .525f
            tapScreen(x, y)
            assertEquals("View-only tap reaches the game surface", 1, touches.get())
            compose.onNodeWithText("ADJUST").performClick()
            tapScreen(x, y)
            assertEquals("Adjust mode owns touches inside its box", 1, touches.get())
            compose.onNodeWithContentDescription("QTE right").performClick()
            compose.onNodeWithContentDescription("QTE down").performClick()
            compose.onNodeWithText("MOVE").performClick()
            compose.onNodeWithContentDescription("QTE right").performClick()
            compose.onNodeWithText("VIEW ONLY").performClick()
            tapScreen(x, y)
            assertEquals(2, touches.get())
            compose.onNodeWithText("SAMPLE 10s").performClick()
            compose.onNodeWithText("SAVED 3").assertIsDisplayed()
            compose.onNodeWithText("ADJUST").assertIsNotEnabled()
            assertEquals(bounds.width() * .375f + 1f, sampled!!.x * bounds.width(), .01f)
            assertNull("Sampling a draft does not save calibration", saved)
            sampling.value = QteIconSamplingStatus(false, 3)
            compose.onNodeWithText("SAVE & CLOSE").performClick()
            compose.waitUntil(5_000) { saved != null && guide == null }
            assertEquals(bounds.width() * .375f + 1f, saved!!.x * bounds.width(), .01f)
            assertEquals(bounds.height() * .45f + 1f, saved!!.y * bounds.height(), .01f)
            assertEquals(bounds.width() * .25f + 1f, saved!!.width * bounds.width(), .01f)
            compose.onNodeWithText("QTE BOX").assertIsDisplayed()
            compose.runOnUiThread { guide!!.show() }
            compose.onNodeWithText("CANCEL").performClick()
            compose.runOnIdle { assertNull(guide) }
        } finally {
            compose.runOnUiThread {
                guide?.close()
                if (hud.isAttachedToWindow) manager.removeViewImmediate(hud)
                DroidballOverlayPresentation.reset()
            }
            scope.cancel()
        }
    }

    @Test fun crtCalibrationIncludesSameSavedQteBox(): Unit {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var nextBox: () -> Unit = {}
        var lcd = ""
        compose.setContent {
            MatchCalibrationScreen(CalibrationManager(context), onA = { nextBox = it },
                onLcdUpdate = { title, details -> lcd = "$title $details" })
        }
        // No edit or save here; inspect the existing device configuration.
        repeat(50) {
            compose.runOnIdle { if (!lcd.contains("QTE Type Icons")) nextBox() }
            compose.waitForIdle()
        }
        compose.runOnIdle { assertTrue(lcd, lcd.contains("QTE Type Icons")) }
    }
}
