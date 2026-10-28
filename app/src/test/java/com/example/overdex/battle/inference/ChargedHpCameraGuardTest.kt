package com.example.overdex.battle.inference

import org.junit.Assert.*
import org.junit.Test

class ChargedHpCameraGuardTest {
    @Test fun `recorded Hydro Cannon relocation guards the later impact without a damage-size threshold`() {
        val guard = ChargedHpCameraGuard()
        // Source-frame coordinates from the October 28 Halloween Cup recording.
        hp(guard, "baseline-1", 108.033, 444)
        hp(guard, "baseline-2", 108.551, 483)
        hp(guard, "baseline-3", 108.620, 485)
        hp(guard, "baseline-4", 108.933, 476)
        hp(guard, "baseline-5", 109.355, 443, right = 231)
        guard.observeCharge(nanos(109.942))
        hp(guard, "camera-before-damage", 110.277, 656)
        hp(guard, "camera-damage", 111.432, 661, left = 40, right = 227, height = 16)
        assertFalse(guard.isChargeImpact(nanos(109.355)))
        assertTrue(guard.isChargeImpact(nanos(111.432)))
        assertFalse(guard.isAwaitingCharge(nanos(111.432)))
        // No health fractions, move names, or energy estimates are inputs.
        assertEquals(listOf(nanos(110.277) until nanos(115.277)), guard.windows())
    }

    @Test fun `late OCR can corroborate held geometry by original source time`() {
        val guard = baseline()
        hp(guard, "shift", 4.0, 650)
        hp(guard, "impact", 5.2, 650)
        guard.advanceTo(nanos(7.6))
        assertTrue(guard.isAwaitingCharge(nanos(5.2)))
        assertFalse(guard.isChargeImpact(nanos(5.2)))
        guard.observeCharge(nanos(3.7))
        assertTrue(guard.isChargeImpact(nanos(5.2)))
        assertFalse(guard.isAwaitingCharge(nanos(5.2)))
    }

    @Test fun `missing announcement releases suspicious frames after the bounded hold`() {
        val guard = baseline()
        hp(guard, "shift", 4.0, 650)
        assertTrue(guard.isAwaitingCharge(nanos(4.0)))
        guard.advanceTo(nanos(9.001))
        assertFalse(guard.isAwaitingCharge(nanos(4.0)))
        assertFalse(guard.isChargeImpact(nanos(4.0)))
        assertTrue(guard.windows().isEmpty())
    }

    @Test fun `observed return ends the guard before the first normal frame`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "shift", 4.0, 650)
        hp(guard, "shift-hit", 5.0, 650)
        hp(guard, "return", 5.4, 455)
        assertTrue(guard.isChargeImpact(nanos(5.0)))
        assertFalse(guard.isChargeImpact(nanos(5.4)))
        assertEquals(listOf(nanos(4.0) until nanos(5.4)), guard.windows())
    }

    @Test fun `a charge alone never extends the existing fallback tail`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "normal-hit", 5.2, 480)
        assertFalse(guard.isChargeImpact(nanos(5.2)))
        assertFalse(guard.isAwaitingCharge(nanos(5.2)))
        assertTrue(guard.windows().isEmpty())
    }

    @Test fun `normal HP bobbing cannot become a charged camera interval`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        for ((index, y) in listOf(430, 490, 445, 480, 465).withIndex())
            hp(guard, "bob-$index", 4.0 + index * .2, y)
        assertTrue(guard.windows().isEmpty())
        assertFalse(guard.isAwaitingCharge(nanos(4.0)))
    }

    @Test fun `three distinct clocks and recent baseline are required`() {
        val duplicate = ChargedHpCameraGuard()
        repeat(4) { hp(duplicate, "copy-$it", 1.0, 450) }
        duplicate.observeCharge(nanos(2.0))
        hp(duplicate, "shift", 3.0, 650)
        assertTrue(duplicate.windows().isEmpty())
        val stale = baseline()
        stale.observeCharge(nanos(9.0))
        hp(stale, "shift", 10.0, 650)
        assertTrue(stale.windows().isEmpty())
    }

    @Test fun `unrelated horizontal spans resolutions sides and malformed bars cannot corroborate`() {
        for (variant in 0..4) {
            val guard = baseline()
            guard.observeCharge(nanos(3.7))
            when (variant) {
                0 -> hp(guard, "other-span", 4.0, 650, left = 300, right = 492)
                1 -> guard.observeHp("other-resolution", nanos(4.0), "PLAYER", 38, 650, 230, 668, 1080, 2400)
                2 -> guard.observeHp("other-side", nanos(4.0), "OPPONENT", 38, 650, 230, 668, 672, 1493)
                3 -> guard.observeHp("legacy", nanos(4.0), "PLAYER", 38, 650, 230, 668, null, null)
                4 -> guard.observeHp("thick-shape", nanos(4.0), "PLAYER", 38, 650, 80, 668, 672, 1493)
            }
            assertTrue("variant $variant", guard.windows().isEmpty())
        }
    }

    @Test fun `charge association is bounded and permits modest pre-announcement camera movement`() {
        for ((chargeAt, expected) in listOf(0.0 to true, -.01 to false, 5.5 to true, 5.501 to false)) {
            val guard = baseline()
            hp(guard, "shift", 4.0, 650)
            guard.observeCharge(nanos(chargeAt))
            assertEquals("charge $chargeAt", expected, guard.isChargeImpact(nanos(4.0)))
        }
    }

    @Test fun `arrival order does not change camera windows`() {
        val forward = ChargedHpCameraGuard()
        val reversed = ChargedHpCameraGuard()
        val samples = listOf(1.0 to 450, 2.0 to 480, 3.0 to 440, 4.0 to 650, 5.0 to 655, 5.5 to 460)
        samples.forEachIndexed { index, (at, y) -> hp(forward, "$index", at, y) }
        samples.withIndex().reversed().forEach { (index, sample) -> hp(reversed, "$index", sample.first, sample.second) }
        forward.observeCharge(nanos(3.7))
        reversed.observeCharge(nanos(3.7))
        assertEquals(forward.windows(), reversed.windows())
        assertEquals(listOf(nanos(4.0) until nanos(5.5)), reversed.windows())
    }

    @Test fun `a sustained new layout expires and repeated charges cannot keep extending it`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "shift", 4.0, 650)
        guard.observeCharge(nanos(8.5))
        hp(guard, "still-shifted", 9.5, 650)
        hp(guard, "new-baseline-2", 10.0, 655)
        hp(guard, "new-baseline-3", 10.5, 650)
        hp(guard, "new-layout-hit", 11.0, 650)
        assertTrue(guard.isChargeImpact(nanos(4.0)))
        assertFalse(guard.isChargeImpact(nanos(9.5)))
        assertFalse(guard.isChargeImpact(nanos(11.0)))
    }

    @Test fun `confirmed historical intervals survive bounded history pruning but reset clears them`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "shift", 4.0, 650)
        hp(guard, "returned", 5.0, 450)
        guard.advanceTo(nanos(60.0))
        assertEquals(listOf(nanos(4.0) until nanos(5.0)), guard.windows())
        guard.reset()
        assertTrue(guard.windows().isEmpty())
        assertFalse(guard.isAwaitingCharge(nanos(4.0)))
    }

    @Test fun `incremental long stream and late frame rebuilds preserve the same windows through pruning`() {
        val incremental = ChargedHpCameraGuard()
        val reordered = ChargedHpCameraGuard()
        fun feed(guard: ChargedHpCameraGuard, index: Int) {
            val at = index * .05
            val phase = index % 600
            if (phase == 0) guard.observeBoundary(nanos(at))
            if (phase == 200) guard.observeCharge(nanos(at))
            val top = if (phase in 204..239) 650 else 450 + index % 30
            hp(guard, "stream-$index", at, top, right = 230 + index % 2)
            // Both production consumers query on each source frame.
            guard.isAwaitingCharge(nanos(at))
            guard.isChargeImpact(nanos(at))
        }
        repeat(1_500) { feed(incremental, it) }
        for (block in 0 until 150) for (offset in 9 downTo 0) feed(reordered, block * 10 + offset)
        assertEquals(incremental.windows(), reordered.windows())
        assertEquals(3, incremental.windows().size)
        for (start in listOf(10.2, 40.2, 70.2)) {
            assertTrue(incremental.isChargeImpact(nanos(start)))
            assertFalse(incremental.isChargeImpact(nanos(start + 2.0)))
        }
    }

    @Test fun `a faint boundary closes the camera and clears both sides baselines`() {
        val guard = baseline()
        repeat(3) { index ->
            guard.observeHp("opponent-$index", nanos(1.0 + index), "OPPONENT", 380, 450, 572, 468, 672, 1493)
        }
        guard.observeCharge(nanos(3.7))
        hp(guard, "charge-camera", 4.0, 650)
        guard.observeBoundary(nanos(4.4))
        hp(guard, "replacement", 4.8, 650)
        guard.observeHp("opponent-new-view", nanos(4.8), "OPPONENT", 380, 650, 572, 668, 672, 1493)
        assertTrue(guard.isChargeImpact(nanos(4.2)))
        assertFalse(guard.isChargeImpact(nanos(4.4)))
        assertFalse(guard.isAwaitingCharge(nanos(4.8)))
        assertFalse(guard.isChargeImpact(nanos(4.8)))
        assertEquals(listOf(nanos(4.0) until nanos(4.4)), guard.windows())
    }

    @Test fun `a late faint before relocation prevents replacement height becoming charge evidence`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "replacement", 4.8, 650)
        hp(guard, "replacement-hit", 5.3, 650)
        assertTrue(guard.isChargeImpact(nanos(5.3)))
        guard.observeBoundary(nanos(4.4))
        assertTrue(guard.windows().isEmpty())
        assertFalse(guard.isAwaitingCharge(nanos(5.3)))
        assertFalse(guard.isChargeImpact(nanos(5.3)))
    }

    @Test fun `late boundaries truncate measured windows at original source time`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "charge-camera", 4.0, 650)
        hp(guard, "later-shift", 5.3, 650)
        guard.observeBoundary(nanos(4.4))
        assertTrue(guard.isChargeImpact(nanos(4.2)))
        assertFalse(guard.isChargeImpact(nanos(4.8)))
        assertEquals(listOf(nanos(4.0) until nanos(4.4)), guard.windows())
    }

    @Test fun `charge before a boundary cannot corroborate a later appearance camera`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        guard.observeBoundary(nanos(4.0))
        hp(guard, "new-baseline-1", 4.2, 450)
        hp(guard, "new-baseline-2", 4.4, 455)
        hp(guard, "new-baseline-3", 4.6, 450)
        hp(guard, "new-camera", 4.8, 650)
        assertTrue(guard.isAwaitingCharge(nanos(4.8)))
        assertFalse(guard.isChargeImpact(nanos(4.8)))
        guard.observeCharge(nanos(4.7))
        assertTrue(guard.isChargeImpact(nanos(4.8)))
    }

    @Test fun `repeated boundary timestamps are idempotent while repeated species OCR needs no boundary`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "camera", 4.0, 650)
        // A caller receiving the same species again sends no appearance boundary.
        hp(guard, "same-appearance", 4.2, 650)
        assertTrue(guard.isChargeImpact(nanos(4.2)))
        repeat(3) { guard.observeBoundary(nanos(4.4)) }
        hp(guard, "replacement", 4.8, 650)
        assertEquals(listOf(nanos(4.0) until nanos(4.4)), guard.windows())
        assertFalse(guard.isChargeImpact(nanos(4.8)))
    }

    @Test fun `an exact faint frame retains its charged impact and duplicate boundary flags merge`() {
        val guard = baseline()
        guard.observeCharge(nanos(3.7))
        hp(guard, "charge-camera", 4.0, 650)
        hp(guard, "final-impact", 4.4, 650)
        guard.observeBoundary(nanos(4.4))
        assertFalse(guard.isChargeImpact(nanos(4.4)))
        guard.observeBoundary(nanos(4.4), includeImpactAtBoundary = true)
        guard.observeBoundary(nanos(4.4))
        assertTrue(guard.isChargeImpact(nanos(4.4)))
        assertFalse(guard.isChargeImpact(nanos(4.4) + 1))
        hp(guard, "replacement", 4.8, 650)
        assertFalse(guard.isChargeImpact(nanos(4.8)))
        assertFalse(guard.isAwaitingCharge(nanos(4.8)))
    }

    private fun baseline() = ChargedHpCameraGuard().also { guard ->
        hp(guard, "baseline-1", 1.0, 450)
        hp(guard, "baseline-2", 2.0, 480)
        hp(guard, "baseline-3", 3.0, 440)
    }

    private fun hp(
        guard: ChargedHpCameraGuard, id: String, at: Double, top: Int,
        left: Int = 38, right: Int = 230, height: Int = 18,
    ) = guard.observeHp(id, nanos(at), "PLAYER", left, top, right, top + height, 672, 1493)

    private fun nanos(seconds: Double) = (seconds * 1_000_000_000).toLong()
}
