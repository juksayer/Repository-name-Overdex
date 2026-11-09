package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-reviewed Pixel source pixels at native and delivered-capture dimensions.
 * These independently selected stills test visible geometry, not attack timing
 * or how recently a production observer acquired the supplied anchor.
 *
 * The rectangles below bound the actual outline in each native source image.
 * A small same-size locator crop is logged as a diagnostic, but cannot acquire
 * empty bars and does not replace the terminal sampler's separate assertions.
 */
class TrackedTerminalHpBarPixelDeviceTest {
    @Test fun paleRedPlayerHpIsMeasuredWithoutTreatingTheOrangeRemainderAsHp() = verify(Spec(
        "pixel4-20261108-152702-low-player-hp.jpg",
        ActiveHpBarMeasurement(65, 1252, 377, 1283, .20f, .95f),
        HpBarFillColor.RED,
        positiveFraction = .12f.. .22f,
    ))

    @Test fun combuskenStillHasAnActuallyVisibleEmptyOutline() = verify(Spec(
        "pixel4-20261108-152312-combusken-fainted.jpg",
        ActiveHpBarMeasurement(534, 1037, 846, 1067, .05f, .95f),
        HpBarFillColor.EMPTY,
    ))

    @Test fun magcargoHasAnActuallyVisibleEmptyOutlineDuringWaterImpact() = verify(Spec(
        "pixel4-20261108-152517-magcargo-empty-hp.jpg",
        ActiveHpBarMeasurement(384, 1226, 696, 1257, .05f, .95f),
        HpBarFillColor.EMPTY,
    ))

    @Test fun flareonsMissingBarAfterTheFinalHitRemainsUnavailable() = verify(Spec(
        "pixel4-20261108-153343-flareon-fainting.jpg",
        // Last visible Flareon outline in the supplied 153325 source image.
        ActiveHpBarMeasurement(534, 1021, 848, 1051, .02f, .95f),
        expectedColor = null,
    ))

    private fun verify(spec: Spec) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")) {
            "Recorded-pixel terminal HP checks must run on a disposable emulator, never a user phone"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val path = "battle_samples/${spec.filename}"
        val bytes = context.assets.open(path).use { it.readBytes() }
        val native = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val readings = JSONArray()
        val failures = mutableListOf<String>()
        try {
            check(native.width == 1080 && native.height == 2280) { "Source geometry changed for $path" }
            for ((width, height) in listOf(1080 to 2280, 672 to 1419)) {
                val frame = if (width == native.width) native else Bitmap.createScaledBitmap(native, width, height, true)
                try {
                    val anchor = scale(spec.nativeAnchor, frame.width.toFloat() / native.width,
                        frame.height.toFloat() / native.height)
                    val measured = TrackedTerminalHpBarMeasurer.measure(frame, anchor)
                    val reading = JSONObject()
                        .put("sourceWidth", width).put("sourceHeight", height)
                        .put("anchor", geometry(anchor))
                        .put("expectedColor", spec.expectedColor?.name ?: "UNAVAILABLE")
                        .put("actualColor", measured?.fillColor?.name ?: "UNAVAILABLE")
                        .put("measurement", measured?.measurement?.let(::geometry) ?: JSONObject.NULL)
                        .put("normalLocatorCandidates", JSONArray(normalCandidates(frame, anchor).map(::geometry)))
                    readings.put(reading)
                    val failure = when {
                        spec.expectedColor == null && measured != null -> "Missing bar became ${measured.fillColor}"
                        spec.expectedColor != null && measured == null -> "Visible ${spec.expectedColor} bar was missed"
                        measured?.fillColor != spec.expectedColor -> "Expected ${spec.expectedColor}, found ${measured?.fillColor}"
                        measured?.fillColor == HpBarFillColor.EMPTY && measured.measurement.filledFraction != 0f ->
                            "Visible empty outline had nonzero fill"
                        measured != null && spec.positiveFraction != null &&
                            measured.measurement.filledFraction !in spec.positiveFraction ->
                            "Literal red fill ${measured.measurement.filledFraction} outside reviewed range ${spec.positiveFraction}"
                        else -> null
                    }
                    if (failure != null) failures += "$width x $height: $failure; $reading"
                } finally { if (frame !== native) frame.recycle() }
            }
        } finally {
            native.recycle()
            val report = JSONObject().put("source", path).put("sha256", sha256(bytes))
                .put("clockPolicy", "Independent appearance examples; no original video frame times")
                .put("readings", readings).put("failures", JSONArray(failures))
            File(context.cacheDir, "terminal-hp-${spec.filename.removeSuffix(".jpg")}.json")
                .writeText(report.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("terminal_hp_recorded_pixels", report.toString()) })
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Small actual-pixel locator diagnostic with results restored to source coordinates. */
    private fun normalCandidates(frame: Bitmap, anchor: ActiveHpBarMeasurement): List<ActiveHpBarMeasurement> {
        val left = (anchor.left - 12).coerceAtLeast(0)
        val top = (anchor.top - 16).coerceAtLeast(0)
        val right = (anchor.right + 12).coerceAtMost(frame.width)
        val bottom = (anchor.bottom + 17).coerceAtMost(frame.height)
        val width = right - left
        val height = bottom - top
        val pixels = IntArray(width * height)
        frame.getPixels(pixels, 0, width, left, top, width, height)
        return ActiveHpBarMeasurer.measureAll(width, height, anchor.width, pixels).map { bar ->
            bar.copy(left = bar.left + left, right = bar.right + left,
                top = bar.top + top, bottom = bar.bottom + top)
        }
    }

    private fun scale(bar: ActiveHpBarMeasurement, x: Float, y: Float) = bar.copy(
        left = (bar.left * x).roundToInt(), right = (bar.right * x).roundToInt(),
        top = (bar.top * y).roundToInt(), bottom = (bar.bottom * y).roundToInt(),
    )

    private fun geometry(bar: ActiveHpBarMeasurement) = JSONObject()
        .put("left", bar.left).put("top", bar.top).put("right", bar.right).put("bottom", bar.bottom)
        .put("filledFraction", bar.filledFraction).put("confidence", bar.confidence)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Spec(
        val filename: String,
        val nativeAnchor: ActiveHpBarMeasurement,
        val expectedColor: HpBarFillColor?,
        val positiveFraction: ClosedFloatingPointRange<Float>? = null,
    )
}
