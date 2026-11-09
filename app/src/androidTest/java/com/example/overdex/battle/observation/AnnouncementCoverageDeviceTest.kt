package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrOverlayPolicy
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.OcrTextEvidence
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native OCR of original game pixels through the same corrected crop as live capture. */
class AnnouncementCoverageDeviceTest {
    @Test fun entryToastFitsAtNativeAndDeliveredCaptureSizes() = verifyBundled(
        "pixel4-20261108-151956-countdown-1.jpg", listOf("Go", "Combusken"))

    @Test fun sludgeUseToastFitsAtNativeAndDeliveredCaptureSizes() = verifyBundled(
        "pixel4-20261108-152242-sludge-impact.jpg", listOf("swampert", "used", "Sludge"))

    @Test fun hydroCannonUseToastFitsAtNativeAndDeliveredCaptureSizes() = verifyBundled(
        "pixel4-20261108-152452-hydro-cannon-impact.jpg", listOf("swampert", "used", "Hydro Cannon"))

    @Test fun mixedRecordedPixelToastKeepsGameTextAndRejectsSmallHudLabels() = runBlocking {
        requireEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Kept privately with the live-match evidence, then supplied to this
        // disposable emulator. The original source is never rewritten.
        val file = File(context.cacheDir, "announcement-pixel-rock-tomb-full.png")
        val bytes = file.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha == "9869c6e314aaf92e81c7c91a0999255860ee191c1b01530506778a014f474669")
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val crop = requireNotNull(BattleCropResolver.resolve("AnnouncementCrop",
            BattleCalibration.DEFAULT_ANNOUNCEMENT_REGION, source))
        try {
            val overlay = crop.provenance.overlayBounds(AnchorRegion(403f / 672f, 165f / 1419f,
                254f / 672f, 200f / 1419f))
            val readings = AnnouncementRecognizer.recognizeEvidence(crop.bitmap,
                OcrSourceContext(source.width, overlay, OcrOverlayPolicy.SMALL_WORDS),
                OcrHeightProfile(OcrField.ANNOUNCEMENT))
            save("mixed-recorded-rock-tomb", readings)
            val accepted = readings.joinToString(" ") { it.acceptedText }
            assertTrue("Game toast was lost: $accepted", listOf("Camerupt", "used", "Rock", "Tomb")
                .all { accepted.contains(it, true) })
            for (label in listOf("Aqua", "Power", "Punch", "Sludge", "Draco", "156")) {
                assertFalse("HUD word escaped: $label in $accepted", accepted.contains(label, true))
            }
            assertTrue(readings.flatMap { it.words }.any { it.decision == "HUD_SIZE_POSITION" })
            assertTrue(readings.any { it.rawText != it.acceptedText })
        } finally { crop.bitmap.recycle(); source.recycle() }
    }

    private fun verifyBundled(filename: String, expectedWords: List<String>) = runBlocking {
        requireEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = context.assets.open("battle_samples/$filename").use(BitmapFactory::decodeStream)!!
        val capture = Bitmap.createScaledBitmap(original, 672, 1419, true)
        try {
            for (source in listOf(original, capture)) {
                val crop = requireNotNull(BattleCropResolver.resolve("AnnouncementCrop",
                    BattleCalibration.DEFAULT_ANNOUNCEMENT_REGION, source))
                try {
                    val readings = AnnouncementRecognizer.recognizeEvidence(crop.bitmap,
                        OcrSourceContext(source.width, overlayPolicy = OcrOverlayPolicy.SMALL_WORDS),
                        OcrHeightProfile(OcrField.ANNOUNCEMENT))
                    save("$filename-${source.width}", readings)
                    val accepted = readings.joinToString(" ") { it.acceptedText }
                    assertTrue("Missing $expectedWords at ${source.width}x${source.height}: $accepted",
                        expectedWords.all { word ->
                            // These source pixels yield both spellings across native/capture
                            // sizes. Preserve the actual OCR; this is no runtime text alias.
                            if (word == "swampert") listOf("swampert", "swampbert").any {
                                accepted.contains(it, true)
                            } else accepted.contains(word, true)
                        })
                } finally { crop.bitmap.recycle() }
            }
        } finally { capture.recycle(); original.recycle() }
    }

    private fun save(name: String, readings: List<OcrTextEvidence>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "announcement-coverage-$name.json").writeText(
            Json { prettyPrint = true }.encodeToString(ListSerializer(OcrTextEvidence.serializer()), readings))
    }

    private fun requireEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")) {
            "Recorded announcement checks must use a disposable emulator, never a user phone"
        }
    }
}
