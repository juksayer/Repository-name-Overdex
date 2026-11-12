package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

/** One clear terminal outline must not require another frame after the Pokemon leaves. */
class VerifiedTerminalHpDamageTest {
    private val red = ActiveHpBarMeasurement(40, 780, 233, 798, .12f, .95f)
    private val empty = red.copy(filledFraction = 0f, confidence = .97f)
    private val start = 10_000_000_000L

    private fun seeded() = ActiveHpBarDamageTickDetector().apply {
        assertNull(accept(red, start))
        assertNull(accept(red, start + 33_000_000L, HpBarFillColor.RED))
    }

    @Test fun `one verified empty frame completes a fresh settled low HP loss exactly once`() {
        val detector = seeded()
        val at = start + 66_000_000L
        val tick = requireNotNull(detector.accept(empty, at, HpBarFillColor.EMPTY))
        assertEquals(.12f, tick.beforeFraction, 0f)
        assertEquals(0f, tick.afterFraction, 0f)
        assertEquals(.12f, tick.lostFraction, 0f)
        assertEquals(at, tick.observedAtNanos)
        assertTrue(tick.confidence > .7f)
        assertNull(detector.accept(empty, at + 33_000_000L, HpBarFillColor.EMPTY))
    }

    @Test fun `unverified geometric and scalar zero still require ordinary settling`() {
        assertNull(seeded().accept(empty, start + 66_000_000L))
        val scalar = ActiveHpBarDamageTickDetector()
        scalar.accept(.12f, .95f, start, 193)
        assertNull(scalar.accept(0f, .97f, start + 33_000_000L, 193))
        assertNotNull(scalar.accept(0f, .97f, start + 150_000_000L, 193))
    }

    @Test fun `missing baseline fresh entry reset and full HP never qualify for terminal shortcut`() {
        assertNull(ActiveHpBarDamageTickDetector().accept(empty, start, HpBarFillColor.EMPTY))
        val single = ActiveHpBarDamageTickDetector()
        single.accept(red, start)
        assertNull(single.accept(empty, start + 33_000_000L, HpBarFillColor.EMPTY))
        val reset = seeded().apply { reset() }
        assertNull(reset.accept(empty, start + 66_000_000L, HpBarFillColor.EMPTY))
        val high = ActiveHpBarDamageTickDetector()
        high.accept(red.copy(filledFraction = .8f), start)
        assertNull(high.accept(empty, start + 33_000_000L, HpBarFillColor.EMPTY))
    }

    @Test fun `stale moved resized or uncertain outlines retain settling`() {
        for ((name, sample, at) in listOf(
            Triple("stale", empty, start + 534_000_001L),
            Triple("relocated", empty.copy(left = 140, right = 333), start + 66_000_000L),
            Triple("edge jitter", empty.copy(left = 41), start + 66_000_000L),
            Triple("height change", empty.copy(bottom = empty.bottom + 3), start + 66_000_000L),
            Triple("uncertain empty", empty.copy(confidence = .79f), start + 66_000_000L),
        )) {
            assertNull(name, seeded().accept(sample, at, HpBarFillColor.EMPTY))
        }
        val weakBaseline = ActiveHpBarDamageTickDetector()
        weakBaseline.accept(red.copy(confidence = .79f), start)
        assertNull(weakBaseline.accept(empty, start + 33_000_000L, HpBarFillColor.EMPTY))
    }

    @Test fun `source clock cannot rewind or duplicate a terminal hit`() {
        val detector = seeded()
        assertNull(detector.accept(empty, start, HpBarFillColor.EMPTY))
        assertNull(detector.accept(empty, start + 33_000_000L, HpBarFillColor.EMPTY))
        assertNotNull(detector.accept(empty, start + 66_000_000L, HpBarFillColor.EMPTY))
        assertNull(detector.accept(empty, start + 66_000_000L, HpBarFillColor.EMPTY))
    }

    @Test fun `an unsettled earlier loss is not collapsed into one terminal attack`() {
        val detector = seeded()
        assertNull(detector.accept(red.copy(filledFraction = .08f), start + 66_000_000L))
        assertNull(detector.accept(empty, start + 99_000_000L, HpBarFillColor.EMPTY))
    }

    @Test fun `a subpixel remaining sliver can reach a verified empty outline`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(red.copy(filledFraction = .003f), start)
        detector.accept(red.copy(filledFraction = .003f), start + 33_000_000L)
        val tick = requireNotNull(detector.accept(empty, start + 66_000_000L, HpBarFillColor.EMPTY))
        assertEquals(.003f, tick.lostFraction, 0f)
    }

    @Test fun `rebound samples cannot keep an old low baseline fresh`() {
        val detector = seeded()
        for (index in 1..10) assertNull(detector.accept(red.copy(filledFraction = 1f),
            start + 33_000_000L + index * 100_000_000L))
        assertNull(detector.accept(empty, start + 1_066_000_000L, HpBarFillColor.EMPTY))
    }
}
