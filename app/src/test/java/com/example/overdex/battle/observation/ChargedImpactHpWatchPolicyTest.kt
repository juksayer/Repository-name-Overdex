package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class ChargedImpactHpWatchPolicyTest {
    private val opponent = ActivePokemonSide.OPPONENT
    private val player = ActivePokemonSide.PLAYER
    private val normal = ActiveHpBarMeasurement(387, 625, 580, 642, .83f, .95f)
    private val center = ActiveHpBarMeasurement(239, 763, 432, 782, 0f, .97f)
    private val base = 20_000_000_000L

    private fun seeded(): ChargedImpactHpWatchPolicy = ChargedImpactHpWatchPolicy().also {
        it.request(672, 1419, base - 1_000_000_000L)
        it.seed(opponent, normal, base - 500_000_000L)
        it.seed(player, normal.copy(left = 35, right = 228), base - 500_000_000L)
    }

    @Test fun `green to empty is acquired from preparation without waiting for used OCR`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        assertNull(watch.request(672, 1419, base + 1_000_000_000L))
        val request = requireNotNull(watch.request(672, 1419, base + 6_000_000_000L))
        assertEquals(opponent, request.side)
        assertEquals(.83f, request.reference.filledFraction)
        assertNull(request.anchor)
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY),
            base + 6_000_000_000L))
        assertNull(watch.request(672, 1419, base + 6_033_000_000L))
    }

    @Test fun `late preparation uses historical owned size rather than a future frame`() {
        val watch = seeded()
        watch.seed(opponent, normal.copy(filledFraction = .5f), base + 2_000_000_000L)
        watch.impact(base + 5_000_000_000L)
        watch.prepare(opponent, base)
        val request = requireNotNull(watch.request(672, 1419, base + 6_000_000_000L))
        assertEquals(.83f, request.reference.filledFraction)
        // The already-arrived used cue limits the source window to +8 seconds.
        assertNull(watch.request(672, 1419, base + 8_000_000_001L))
    }

    @Test fun `acquisition is bounded to ten hertz but a real outline is tracked each frame`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val firstAt = base + 6_000_000_000L
        val request = requireNotNull(watch.request(672, 1419, firstAt))
        assertNull(watch.request(672, 1419, firstAt + 33_000_000L))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .15f),
            HpBarFillColor.RED), firstAt))
        val next = requireNotNull(watch.request(672, 1419, firstAt + 66_000_000L))
        assertEquals(center.left, next.anchor?.left)
        assertNull(watch.request(672, 1419, firstAt + 66_000_000L))
        assertNull(watch.request(672, 1419, firstAt + 3_000_000_000L))
    }

    @Test fun `one side's preparation never opens the other side and used alone is insufficient`() {
        val watch = seeded()
        watch.impact(base)
        assertNull(watch.request(672, 1419, base + 2_000_000_000L))
        watch.prepare(player, base + 3_000_000_000L)
        val request = requireNotNull(watch.request(672, 1419, base + 5_000_000_000L))
        assertEquals(player, request.side)
    }

    @Test fun `stale or absent normal geometry cannot authorize an empty camera bar`() {
        val watch = seeded()
        watch.prepare(opponent, base + 6_000_000_000L)
        assertNull(watch.request(672, 1419, base + 9_000_000_000L))
        val noReference = ChargedImpactHpWatchPolicy()
        noReference.prepare(opponent, base)
        assertNull(noReference.request(672, 1419, base + 3_000_000_000L))
    }

    @Test fun `appearance boundary invalidates in-flight acquisition and old cues`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val request = requireNotNull(watch.request(672, 1419, base + 3_000_000_000L))
        watch.reset(opponent, base + 3_010_000_000L)
        assertFalse(watch.current(request))
        assertFalse(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY),
            base + 3_000_000_000L))
        watch.prepare(opponent, base)
        assertNull(watch.request(672, 1419, base + 3_033_000_000L))
    }

    @Test fun `dimension changes expire reference and camera ownership`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        assertNotNull(watch.request(672, 1419, base + 3_000_000_000L))
        assertNull(watch.request(1080, 2280, base + 3_033_000_000L))
        assertNull(watch.request(672, 1419, base + 3_066_000_000L))
    }

    @Test fun `charge samples and first normal return reset fast move baselines`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val at = base + 6_000_000_000L
        val request = requireNotNull(watch.request(672, 1419, at))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY), at))
        assertFalse(watch.fastMoveEvidenceAllowed(opponent, center, at))
        assertTrue(watch.fastMoveEvidenceAllowed(player, normal.copy(left = 35, right = 228), at))
        assertFalse(watch.fastMoveEvidenceAllowed(opponent, normal.copy(filledFraction = .1f), at + 1_000_000_000L))
        assertTrue(watch.fastMoveEvidenceAllowed(opponent, normal.copy(filledFraction = .1f), at + 1_033_000_000L))
    }

    @Test fun `unavailable frame does not consume the window or turn into zero`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        assertNotNull(watch.request(672, 1419, base + 3_000_000_000L))
        assertNotNull(watch.request(672, 1419, base + 3_100_000_000L))
        assertNull(watch.request(672, 1419, base + 12_000_000_001L))
    }
}
