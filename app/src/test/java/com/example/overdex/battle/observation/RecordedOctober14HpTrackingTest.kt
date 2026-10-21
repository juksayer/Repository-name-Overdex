package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/** Retained battlefield pixels from fa89d58b, with the original calibrated bands. */
class RecordedOctober14HpTrackingTest {
    @Test fun `both visible bars survive white player art and fast move effects`() {
        val regions = mapOf(
            ActivePokemonSide.PLAYER to BattleCropBounds(35, 367, 241, 1080),
            ActivePokemonSide.OPPONENT to BattleCropBounds(312, 381, 576, 863),
        )
        val failures = mutableListOf<String>()
        for (frame in listOf(86, 90, 92, 104, 114, 133, 150)) {
            val band = ImageIO.read(javaClass.getResource("/battle/hp/oct14/band-$frame.png"))
            val pixels = band.getRGB(0, 0, band.width, band.height, null, 0, band.width)
            val found = BattlefieldHpTracker().measurePixels(672, 1493, frame * 1_000_000_000L, regions, pixels)
            if (found.keys != ActivePokemonSide.entries.toSet()) failures += "frame $frame: $found"
        }
        assertEquals("Both complete HP bars are visible in each source", emptyList<String>(), failures)
    }

    @Test fun `recorded blue border creates one pulse until it returns to white`() {
        fun appearance(frame: Int): ActiveHpBarBorderAppearance {
            val band = ImageIO.read(javaClass.getResource("/battle/hp/oct14/band-$frame.png"))
            val bar = ActiveHpBarMeasurer.measureAll(band.width, band.height, 192, band::getRGB)
                .first { it.centerX > band.width * .6f }
            return requireNotNull(ActiveHpBarBorderAppearanceMeasurer.measure(band.width, band.height, bar, band::getRGB))
        }
        val white = appearance(86)
        val blue = appearance(90)
        assertTrue(white.whiteFraction > .8f)
        assertTrue(blue.blueFraction > .8f)
        assertEquals("Blue must not be relabeled orange", 0f, blue.orangeFraction, .01f)
        val detector = ActiveHpBarBorderPulseDetector()
        // Synthetic sample times exercise edge counting, not this recording's cadence.
        repeat(4) { assertNull(detector.acceptPulse("white-$it", it * 100_000_000L, white)) }
        val first = requireNotNull(detector.acceptPulse("blue", 500_000_000L, blue))
        assertEquals(500_000_000L, first.monotonicTimeNanos)
        assertEquals(0f, first.orangeFractionAtOnset, .01f)
        assertTrue(first.colorDistanceAtOnset > .5f)
        repeat(3) { assertNull(detector.acceptPulse("still-blue-$it", 600_000_000L + it * 100_000_000L, blue)) }
        assertNull(detector.acceptPulse("white-again", 900_000_000L, white))
        val second = requireNotNull(detector.acceptPulse("new-blue", 1_500_000_000L, blue))
        assertEquals(first.monotonicTimeNanos, second.previousPulseNanos)
    }
}
