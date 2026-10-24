package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native badge pixels from f843fe0c, including the HUD overlap that froze the old cache. */
class RecordedRetainedBadgeDeviceTest {
    @Test fun retainedBallsRefreshAfterFaintsAndCannotReturnFromQueuedOldFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val hud = AnchorRegion(400.08887f / 672, 144.323334f / 1493, 258.22223f / 672, .3f)
        val calibration = BattleCalibration(
            opponentTeamInfoRegion = AnchorRegion(.59375f, .09260216f, .38339287f, .055234425f),
            opponentSpeciesNameRegion = AnchorRegion(.73363096f, .09930009f, .23363096f, .021433355f),
        )
        fun source(name: String): Bitmap {
            val band = instrumentation.context.assets.open("badge-retention-20261024/$name.png")
                .use(BitmapFactory::decodeStream)!!
            return Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(band, 0f, 80f, null)
                band.recycle()
            }
        }
        val sources = listOf(source("three"), source("two"), source("one"))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var retained: RetainedOpponentBadge
        var width = 0; var height = 0
        scenario.onActivity { activity ->
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(20, 60, 90)) }
            })
            val manager = activity.getSystemService(WindowManager::class.java)
            width = manager.maximumWindowMetrics.bounds.width()
            height = manager.maximumWindowMetrics.bounds.height()
            retained = RetainedOpponentBadge(activity, manager)
        }
        fun shot(label: String): Bitmap {
            instrumentation.waitForIdleSync()
            Thread.sleep(100)
            return instrumentation.uiAutomation.takeScreenshot()!!.also { bitmap ->
                File(instrumentation.targetContext.cacheDir, "retained-balls-$label.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        val scope = RetainedBadgeScope("f843fe0c-recorded-regression", System.nanoTime())
        fun update(source: Bitmap, at: Long, visible: Boolean) {
            val frame = CapturedVisualFrame(source, 1L, at, overlayRegion = hud)
            val badge = TeamBadgeCropLocator.locate(BattleRegionId.OPPONENT_TEAM_INFO, calibration, frame)
            if (visible) assertTrue("Native badge must be independently visible", TeamBadgeCropLocator.nativeBadgePresent(frame, badge))
            retained.update(source, at, visible, visible, badge, width, height, scope, true, hud,
                DroidballOverlayPresentation.opponentBadgeContent.value)
        }
        fun hideNative(source: Bitmap) {
            update(source, System.nanoTime(), false)
            Thread.sleep(120)
            update(source, System.nanoTime(), false)
        }
        fun countShown(image: Bitmap): Int {
            // Native ball caps, above the HUD seam, in original frame coordinates.
            val left = (546f / 672 * width).toInt(); val right = (651f / 672 * width).toInt()
            val top = (120f / 1493 * height).toInt(); val bottom = (144f / 1493 * height).toInt()
            return OpponentBattleResourceCounter.count(right - left, bottom - top,
                { x, y -> image.getPixel(left + x, top + y) }, 3, OpponentBattleResourceCounter::isBrightPokeBallRed)
        }
        DroidballOverlayPresentation.reset()
        try {
            for ((index, bitmap) in sources.withIndex()) {
                val changedAt = System.nanoTime()
                DroidballOverlayPresentation.recordOpponentResources(
                    OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 3 - index, 3), changedAt)
                if (index > 0) DroidballOverlayPresentation.markOpponentFainted(
                    if (index == 1) "Mankey" else "Hitmonlee", changedAt)
                retained.contentChanged(DroidballOverlayPresentation.opponentBadgeContent.value)
                if (index > 0) {
                    shot("cleared-$index").also { assertEquals("Known faint removes the old image immediately", 0, countShown(it)); it.recycle() }
                    Thread.sleep(300) // Same queued-capture exclusion used in production.
                    update(sources[index - 1], changedAt - 1, true)
                    hideNative(sources[index - 1])
                    assertNull("A queued pre-faint frame cannot refill the cache", retained.exclusion(System.nanoTime()))
                }
                update(bitmap, System.nanoTime(), true)
                hideNative(bitmap)
                shot("${3 - index}-balls").also {
                    assertEquals("Refreshed native badge must retain the current count", 3 - index, countShown(it))
                    // The lower rims of the balls sit below the HUD window's
                    // rectangular top but inside its transparent curved opening.
                    val x = (636f / 672 * width).toInt()
                    val y = (150f / 1493 * height).toInt()
                    assertNotEquals("The last ball must not be sliced at the HUD seam",
                        it.getPixel(100, height / 2), it.getPixel(x, y))
                    it.recycle()
                }
                val excluded = requireNotNull(retained.exclusion(System.nanoTime()))
                assertTrue("The retained footprint includes the native curved opening",
                    excluded.y + excluded.height > hud.y)
                assertFalse("Our retained pixels stay excluded from witnesses",
                    CapturedVisualFrame(bitmap, 1L, System.nanoTime(), retainedBadgeRegion = excluded).canObserve(excluded))
            }
            retained.reset()
            shot("reset").also { assertEquals(0, countShown(it)); it.recycle() }
        } finally {
            retained.close()
            instrumentation.waitForIdleSync()
            scenario.close()
            sources.forEach(Bitmap::recycle)
            DroidballOverlayPresentation.reset()
        }
    }
}
