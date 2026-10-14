package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveHpBarDamageTickDetectorTest {
    @Test
    fun `one settled decrease emits one measured damage tick`() {
        val detector = ActiveHpBarDamageTickDetector()
        assertNull(detector.accept(1f, 0.9f, 0L))
        assertNull(detector.accept(0.82f, 0.9f, 10_000_000L))
        assertNull(detector.accept(0.75f, 0.9f, 50_000_000L))

        val tick = detector.accept(0.75f, 0.9f, 150_000_000L)!!

        assertEquals(1f, tick.beforeFraction, 0.0001f)
        assertEquals(0.75f, tick.afterFraction, 0.0001f)
        assertEquals(0.25f, tick.lostFraction, 0.0001f)
        assertNull(detector.accept(0.75f, 0.9f, 250_000_000L))
    }

    @Test
    fun `small tracking jitter does not become damage`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(0.80f, 0.9f, 0L)
        assertNull(detector.accept(0.797f, 0.9f, 100_000_000L))
        assertNull(detector.accept(0.802f, 0.9f, 200_000_000L))
    }

    @Test
    fun `orange pulse cannot reset a low baseline or manufacture another hit`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(0.24f, 0.9f, 0L)
        assertNull(detector.accept(1f, 0.9f, 100_000_000L))
        assertNull(detector.accept(0.24f, 0.9f, 200_000_000L))
        assertNull(detector.accept(0.24f, 0.9f, 300_000_000L))
        assertNull(detector.accept(0.20f, 0.9f, 400_000_000L))

        val tick = detector.accept(0.20f, 0.9f, 500_000_000L)!!

        assertEquals(0.24f, tick.beforeFraction, 0.0001f)
        assertEquals(0.04f, tick.lostFraction, 0.0001f)
    }

    @Test
    fun `a single low outlier must settle before it becomes damage`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(0.8f, 0.9f, 0L)
        detector.accept(0.2f, 0.9f, 100_000_000L)
        assertNull(detector.accept(0.7f, 0.9f, 200_000_000L))
        val tick = detector.accept(0.7f, 0.9f, 300_000_000L)!!
        assertEquals(0.1f, tick.lostFraction, 0.0001f)
    }

    @Test
    fun `reset prevents a new combatant from inheriting the old hp baseline`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(0.20f, 0.9f, 0L)
        detector.reset()
        assertNull(detector.accept(1f, 0.9f, 100_000_000L))
        assertNull(detector.accept(0.99f, 0.9f, 200_000_000L))
    }

    @Test fun `successive sampled drops do not accumulate into a single enormous hit`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(1f, .9f, 0)
        detector.accept(.96f, .9f, 1_000_000_000L)
        val first = detector.accept(.92f, .9f, 2_000_000_000L)!!
        val second = detector.accept(.88f, .9f, 3_000_000_000L)!!
        assertEquals(.04f, first.lostFraction, .0001f)
        assertEquals(.04f, second.lostFraction, .0001f)
        assertEquals(1_000_000_000L, first.observedAtNanos)
        assertEquals(2_000_000_000L, second.observedAtNanos)
    }

    @Test fun `one pixel edge jitter still permits a settled loss`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(.9524f, .9f, 0)
        detector.accept(.8777f, .9f, 100_000_000L)
        val tick = detector.accept(.8830f, .9f, 400_000_000L)!!
        assertEquals(.8777f, tick.afterFraction, .0001f)
        assertEquals(100_000_000L, tick.observedAtNanos)
    }

    @Test fun `reacquisition after a long gap is a new baseline and not one hit`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(.95f, .9f, 0)
        detector.accept(.92f, .9f, 1_000_000_000L)
        assertNull(detector.accept(.26f, .9f, 25_000_000_000L))
        assertNull(detector.accept(.26f, .9f, 25_200_000_000L))
        detector.accept(.22f, .9f, 25_500_000_000L)
        assertEquals(.04f, detector.accept(.22f, .9f, 25_700_000_000L)!!.lostFraction, .0001f)
    }
}
