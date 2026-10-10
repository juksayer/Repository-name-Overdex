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
}
