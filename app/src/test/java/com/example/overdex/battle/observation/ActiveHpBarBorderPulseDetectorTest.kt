package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ApertureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveHpBarBorderPulseDetectorTest {
    private val neutral = ActiveHpBarBorderAppearance(
        red = 0.82f,
        green = 0.82f,
        blue = 0.82f,
        brightness = 0.82f,
        orangeFraction = 0.01f,
        whiteFraction = 0.82f
    )
    private val colored = ActiveHpBarBorderAppearance(
        red = 0.95f,
        green = 0.48f,
        blue = 0.12f,
        brightness = 0.95f,
        orangeFraction = 0.72f,
        whiteFraction = 0.08f
    )

    @Test
    fun `one prolonged border flash creates only one pulse edge`() {
        val detector = ActiveHpBarBorderPulseDetector()
        warm(detector)

        assertNull(detector.accept("pulse-1", 500_000_000L, colored))
        assertNull(detector.accept("still-pulse-1", 600_000_000L, colored))
        assertNull(detector.accept("return-1", 700_000_000L, neutral))
        val cadence = detector.accept("pulse-2", 2_000_000_000L, colored)

        requireNotNull(cadence)
        assertEquals(1_500_000_000L, cadence.intervalNanos)
        assertEquals(listOf("pulse-1", "pulse-2"), cadence.evidenceArticleIds)
        assertTrue(cadence.colorDistanceAtOnset >= 0.10f)
    }

    @Test
    fun `pulse API preserves the first edge and timestamps every later edge`() {
        val detector = ActiveHpBarBorderPulseDetector()
        warm(detector)

        val first = detector.acceptPulse("pulse-1", 500_000_000L, colored)
        requireNotNull(first)
        assertEquals("pulse-1", first.articleId)
        assertEquals(500_000_000L, first.monotonicTimeNanos)
        assertNull(first.previousPulseNanos)
        assertNull(first.previousPulseArticleId)

        assertNull(detector.acceptPulse("still-pulse-1", 600_000_000L, colored))
        assertNull(detector.acceptPulse("return-1", 700_000_000L, neutral))

        val second = detector.acceptPulse("pulse-2", 2_000_000_000L, colored)
        requireNotNull(second)
        assertEquals(1_500_000_000L, second.monotonicTimeNanos - second.previousPulseNanos!!)
        assertEquals("pulse-1", second.previousPulseArticleId)
    }

    @Test
    fun `one captured orange frame between white frames remains observable`() {
        val detector = ActiveHpBarBorderPulseDetector()
        warm(detector)

        val pulse = detector.acceptPulse("orange-frame", 500_000_000L, colored)
        requireNotNull(pulse)
        assertEquals(0.72f, pulse.orangeFractionAtOnset)
        assertEquals(0.82f, pulse.whiteFractionBeforePulse, 0.01f)
        assertNull(detector.acceptPulse("white-again", 550_000_000L, neutral))
    }

    @Test
    fun `transition API reports aperture state without inferring a hit`() {
        val detector = ActiveHpBarBorderPulseDetector()

        val initial = detector.acceptTransition("base-1", 0L, neutral)
        requireNotNull(initial)
        assertEquals(ApertureStatus.ABSENT, initial.status)
        assertNull(initial.pulse)
        assertNull(detector.acceptTransition("base-2", 100_000_000L, neutral))
        assertNull(detector.acceptTransition("base-3", 200_000_000L, neutral))
        assertNull(detector.acceptTransition("base-4", 300_000_000L, neutral))

        val present = detector.acceptTransition("orange", 500_000_000L, colored)
        requireNotNull(present)
        assertEquals(ApertureStatus.PRESENT, present.status)
        requireNotNull(present.pulse)

        val absent = detector.acceptTransition("white", 600_000_000L, neutral)
        requireNotNull(absent)
        assertEquals(ApertureStatus.ABSENT, absent.status)
        assertNull(absent.pulse)
    }

    @Test
    fun `green yellow and red fill changes do not impersonate an orange border`() {
        val detector = ActiveHpBarBorderPulseDetector()
        warm(detector)
        listOf(
            ActiveHpBarBorderAppearance(0.15f, 0.90f, 0.68f, 0.90f, 0.01f, 0.75f),
            ActiveHpBarBorderAppearance(0.95f, 0.85f, 0.15f, 0.95f, 0.01f, 0.74f),
            ActiveHpBarBorderAppearance(0.90f, 0.15f, 0.12f, 0.90f, 0.01f, 0.73f)
        ).forEachIndexed { index, appearance ->
            assertNull(detector.acceptPulse("fill-$index", 500_000_000L + index * 50_000_000L, appearance))
        }
    }

    @Test
    fun `ordinary baseline drift is not a pulse`() {
        val detector = ActiveHpBarBorderPulseDetector()
        warm(detector)
        repeat(8) { index ->
            assertNull(
                detector.accept(
                    "drift-$index",
                    500_000_000L + index * 100_000_000L,
                    ActiveHpBarBorderAppearance(0.80f, 0.81f, 0.82f, 0.82f, 0.01f, 0.80f)
                )
            )
        }
    }

    private fun warm(detector: ActiveHpBarBorderPulseDetector) {
        assertNull(detector.accept("base-1", 0L, neutral))
        assertNull(detector.accept("base-2", 100_000_000L, neutral))
        assertNull(detector.accept("base-3", 200_000_000L, neutral))
        assertNull(detector.accept("base-4", 300_000_000L, neutral))
    }
}
