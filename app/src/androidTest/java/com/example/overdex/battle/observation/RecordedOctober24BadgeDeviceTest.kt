package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.*
import org.junit.Test

/** Recorded badge pixels at their original source coordinates; no saved-profile writes. */
class RecordedOctober24BadgeDeviceTest {
    @Test fun hudCardsDoNotPreventRecoveryToTheNativeResourceRow() {
        val calibration = BattleCalibration(
            opponentSpeciesNameRegion = AnchorRegion(.7268519f, .09916666f, .24425925f, .020416666f),
            opponentPokeBallsRegion = AnchorRegion(.8101852f, .11666667f, .1712963f, .025f),
        )
        for ((name, remaining) in listOf("one" to 1, "two" to 2, "three" to 3)) {
            val band = InstrumentationRegistry.getInstrumentation().context.assets
                .open("badge-location-20261024/$name-badge-band.png").use(BitmapFactory::decodeStream)!!
            val bitmap = Bitmap.createBitmap(672, 1419, Bitmap.Config.ARGB_8888)
            try {
                val pixels = IntArray(band.width * band.height)
                band.getPixels(pixels, 0, band.width, 0, 0, band.width, band.height)
                bitmap.setPixels(pixels, 0, band.width, 0, 90, band.width, band.height)
                // Hand-labelled HUD footprint for the recorded band, not source telemetry.
                val hud = AnchorRegion(400f / 672, 145f / 1419, 272f / 672, 300f / 1419)
                val frame = CapturedVisualFrame(bitmap, 1L, 1L, displayWidth = 1080,
                    displayHeight = 2280, overlayRegion = hud)
                val original = calibration.copy()
                val resolved = requireNotNull(BattleCropContracts.opponentPokeBalls.resolve(calibration, frame))
                try {
                    assertTrue("$name must leave the HUD row: ${resolved.provenance}", resolved.provenance.bounds.top < 145)
                    assertEquals(name, remaining, OpponentBattleResourceCounter.pokeBallCount(
                        resolved.bitmap.width, resolved.bitmap.height, resolved.provenance.overlayBounds(hud),
                        resolved.bitmap::getPixel))
                    assertEquals(original, calibration)
                } finally { resolved.bitmap.recycle() }
            } finally { band.recycle(); bitmap.recycle() }
        }
    }
}
