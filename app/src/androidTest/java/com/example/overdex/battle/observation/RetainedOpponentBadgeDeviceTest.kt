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
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetainedOpponentBadgeDeviceTest {
    @Test fun retainsOriginalPositionRejectsOwnEvidenceAndReleasesWhenPlayerBadgeReturns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = instrumentation.context.assets.open("battle/badge_retention/frame.png").use(BitmapFactory::decodeStream)
        val opponent = AnchorRegion(.59375f, .057602145f, .38339287f, .050234426f)
        val player = AnchorRegion(.017857144f, .057602145f, .38934523f, .050234426f)
        assertTrue(TeamBadgePresenceDetector.present(source.width, source.height, opponent, source::getPixel))
        assertTrue(TeamBadgePresenceDetector.present(source.width, source.height, player, source::getPixel))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var retained: RetainedOpponentBadge
        var width = 0; var height = 0
        scenario.onActivity { activity ->
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(20, 60, 90)) }
            })
            val manager = activity.getSystemService(WindowManager::class.java)
            val bounds = manager.maximumWindowMetrics.bounds
            width = bounds.width(); height = bounds.height()
            retained = RetainedOpponentBadge(activity, manager)
        }
        fun shot(name: String): Bitmap {
            instrumentation.waitForIdleSync()
            Thread.sleep(100)
            return instrumentation.uiAutomation.takeScreenshot()!!.also { image ->
                File(instrumentation.targetContext.cacheDir, "badge-retention-$name.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        try {
            val before = shot("native")
            val t = System.nanoTime()
            val scope = RetainedBadgeScope("badge-retention-test", t)
            fun update(bitmap: Bitmap, at: Long, playerPresent: Boolean, opponentPresent: Boolean) =
                retained.update(bitmap, at, playerPresent, opponentPresent, opponent, width, height,
                    scope, battleSurfaceEstablished = true, hudRegion = null)
            update(source, t, true, true)
            assertNull(retained.exclusion(t))
            update(source, t + 1, false, false)
            update(source, t + 150_000_000, false, false)
            val shown = shot("retained")
            assertEquals(opponent, retained.exclusion(System.nanoTime()))
            val x = ((opponent.x + opponent.width * .5f) * width).toInt()
            val y = ((opponent.y + opponent.height * .25f) * height).toInt()
            assertNotEquals("Retained image occupies original calibrated badge", before.getPixel(x, y), shown.getPixel(x, y))
            // Compare within a frame: Android's activity-entry fade changes the
            // whole background between the initial and retained screenshots.
            assertEquals("Nothing is appended below the original badge", shown.getPixel(100, height / 2),
                shown.getPixel(x, y + (opponent.height * height).toInt()))

            val frame = CapturedVisualFrame(shown, 1, System.nanoTime(), retainedBadgeRegion = opponent)
            val calibration = BattleCalibration().copy(opponentTeamInfoRegion = opponent, playerTeamInfoRegion = player,
                opponentSpeciesNameRegion = opponent, opponentPokeBallsRegion = opponent, opponentShieldsRegion = opponent)
            for (crop in listOf(BattleCropContracts.opponentActiveSpeciesText,
                BattleCropContracts.opponentPokeBalls, BattleCropContracts.opponentShields)) {
                assertNull("Retained pixels cannot become new ${crop.cropName} testimony", crop.resolve(calibration, frame))
            }
            assertFalse(frame.canObserve(opponent))
            assertTrue(frame.canObserve(player))
            assertTrue(frame.canObserve(AnchorRegion(.2f, .5f, .6f, .05f)))

            // Our copy is visible in this capture. It must not cause hide/cache cycling.
            update(shown, System.nanoTime(), false, true)
            assertNotNull(retained.exclusion(System.nanoTime()))
            update(source, System.nanoTime(), true, true)
            assertNotNull("Queued captures remain excluded after hiding", retained.exclusion(System.nanoTime()))
            val cleared = shot("returned")
            assertEquals("Player badge return removes our copy", cleared.getPixel(100, height / 2), cleared.getPixel(x, y))
            Thread.sleep(300)
            assertNull(retained.exclusion(System.nanoTime()))
            val clean = frame.copy(retainedBadgeRegion = null)
            val original = BattleCropContracts.opponentActiveSpeciesText.resolve(calibration, clean)
            assertNotNull("Native reading resumes after clearance", original)
            original?.bitmap?.recycle()
            retained.reset()
            before.recycle(); shown.recycle(); cleared.recycle()
        } finally {
            retained.close()
            instrumentation.waitForIdleSync()
            scenario.close(); source.recycle()
        }
    }
}
