package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/** Actual charge-camera pixels, with source PTS and transformation beside each fixture. */
class RecordedMismagiusChargedEmptyHpTest {
    private val reference = ActiveHpBarMeasurement(394, 677, 587, 694, .55f, .95f)

    private fun read(name: String): TrackedTerminalHpBarSample? {
        val crop = ImageIO.read(requireNotNull(javaClass.getResource(
            "/battle/hp/2026-11-11-mismagius-charged-empty/$name.png")))
        return ChargedImpactHpBarMeasurer.acquire(672, 1493, reference) { x, y ->
            assertTrue(x in 200 until 472 && y in 507 until 925)
            crop.getRGB(x - 200, y - 507)
        }
    }

    @Test fun `recorded charge impact contains a brief centered empty outline without a preceding red requirement`() {
        // 89.151, 89.187 and 89.279 seconds in the source: this whole sampled
        // opportunity is shorter than one ordinary HP acquisition interval.
        for (name in listOf("empty-first", "empty-middle", "empty-last")) {
            val sample = requireNotNull(read(name))
            assertEquals(HpBarFillColor.EMPTY, sample.fillColor)
            assertEquals(0f, sample.measurement.filledFraction, 0f)
            assertTrue(sample.measurement.centerX in 330f..337f)
            assertTrue(sample.measurement.top in 672..676)
        }
    }

    @Test fun `recorded orange remainder and later camera departure cannot substitute for the empty outline`() {
        assertNull(read("orange-tail")) // 89.121: residual colored damage remains.
        assertNull(read("camera-exit")) // 89.339: the camera has left the centered bar.
    }
}
