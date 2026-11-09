package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull

/** Real reviewed pixels; source frames are immutable and contain no inferred attack clock. */
class ChargedImpactHpPixelDeviceTest {
    @Test fun nativeAndDeliveredPixelScreenshotAcquireTheVisibleEmptyMagcargoBar() {
        requireEmulator()
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("battle_samples/pixel4-20261108-152517-magcargo-empty-hp.jpg").use { it.readBytes() }
        val native = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            for ((width, height) in listOf(1080 to 2280, 672 to 1419)) {
                val frame = if (width == native.width) native else android.graphics.Bitmap.createScaledBitmap(native, width, height, true)
                try {
                    val barWidth = width * 2 / 7
                    val reference = ActiveHpBarMeasurement(width * 57 / 100, height * 44 / 100,
                        width * 57 / 100 + barWidth, height * 44 / 100 + width * 29 / 1000,
                        .83f, .95f)
                    val sample = requireNotNull(ChargedImpactHpBarMeasurer.acquire(frame, reference)) {
                        "No centered empty outline in reviewed Pixel screenshot at $width x $height"
                    }
                    assertEquals(HpBarFillColor.EMPTY, sample.fillColor)
                    assertEquals(0f, sample.measurement.filledFraction, 0f)
                    assertTrue(sample.measurement.top in height * 53 / 100..height * 56 / 100)
                } finally { if (frame !== native) frame.recycle() }
            }
        } finally { native.recycle() }
    }

    /** Supply the private copied capture through adb, not a committed user recording. */
    @Test fun earthPowerBeamObscuringTheRecordedBarRemainsUnknown() {
        requireEmulator()
        val path = InstrumentationRegistry.getArguments().getString("chargedHpFixturePath")
        assumeNotNull("Pass chargedHpFixturePath for the private Magcargo Earth Power frame", path)
        val bytes = File(requireNotNull(path)).readBytes()
        assertEquals("30ed911c02bd718b59403694367aa0a61ccb7f120f1f6ecc1fe3198ef54ebee6",
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val frame = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(672, frame.width)
            assertEquals(1419, frame.height)
            val normal = ActiveHpBarMeasurement(387, 625, 580, 642, .8307f, .95f)
            val watch = ChargedImpactHpWatchPolicy()
            val at = 20_000_000_000L
            watch.request(frame.width, frame.height, at - 1_000_000_000L)
            watch.seed(com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT, normal, at - 500_000_000L)
            watch.prepare(com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT, at)
            // No used announcement has completed OCR at this boundary.
            val request = requireNotNull(watch.request(frame.width, frame.height, at + 6_000_000_000L))
            // The intact outline is visible, but a vertical beam crosses its
            // interior. Human knowledge of the KO must not turn those obscured
            // pixels (including warm orange/yellow shoulders) into measured 0.
            assertNull(ChargedImpactHpBarMeasurer.acquire(frame, request.reference))
            assertNull(TrackedTerminalHpBarMeasurer.measure(frame,
                ActiveHpBarMeasurement(239, 763, 432, 782, .83f, .95f)))
            // Unavailable does not consume the short watch; a later clean frame
            // remains eligible without requiring a second red or empty frame.
            assertNotNull(watch.request(frame.width, frame.height, at + 6_100_000_000L))
        } finally { frame.recycle() }
    }

    private fun requireEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")) {
            "Recorded-pixel checks must run on a disposable emulator, never a user phone"
        }
    }
}
