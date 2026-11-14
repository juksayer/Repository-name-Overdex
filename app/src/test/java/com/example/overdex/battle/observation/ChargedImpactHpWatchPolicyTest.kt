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

    @Test fun `orange geometry keeps tracking without consuming the window or renewing its deadline`() {
        val watch=seeded()
        watch.prepare(opponent,base)
        val at=base+3_000_000_000L
        val request=requireNotNull(watch.request(672,1419,at))
        assertTrue(watch.acceptGeometry(request,center,at))
        val next=requireNotNull(watch.request(672,1419,at+33_000_000))
        assertEquals(center,next.anchor)
        assertTrue(watch.canCapture(at+33_000_000))
        assertFalse(watch.acceptGeometry(next,center,at-1))
        watch.appearanceBoundary(opponent,at+40_000_000)
        assertFalse(watch.acceptGeometry(next,center,at+50_000_000))
        assertNull(watch.request(672,1419,at+60_000_000))
    }

    @Test fun `sparse crop window requires a prepared recipient and never advances the geometry cadence`() {
        val watch = seeded()
        assertFalse(watch.canCapture(base))
        watch.prepare(opponent, base)
        assertFalse(watch.canCapture(base + 1_999_999_999L))
        val at = base + 3_000_000_000L
        assertTrue(watch.canCapture(at))
        assertTrue(watch.canCapture(at))
        val request = requireNotNull(watch.request(672, 1419, at))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY), at))
        assertFalse(watch.canCapture(at + 33_000_000L))
        watch.resetAll(at + 50_000_000L)
        assertFalse(watch.canCapture(at + 100_000_000L))
        val expired = seeded().apply { prepare(opponent, base) }
        assertFalse(expired.canCapture(base + 12_000_000_001L))
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

    @Test fun `camera is excluded and normal return seeds without reporting charged damage`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val at = base + 6_000_000_000L
        val request = requireNotNull(watch.request(672, 1419, at))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY), at))
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(normal.copy(filledFraction = 1f), at - 100_000_000L)
        fun deliver(bar: ActiveHpBarMeasurement, time: Long): ActiveHpBarDamageTickMeasurement? {
            val disposition = watch.fastMoveDisposition(opponent, bar, time)
            return detector.accept(LiveActiveHpBarSample(bar, null, null, 0, time,
                fastMoveEvidenceAllowed = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.EVIDENCE,
                baselineOnly = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.BASELINE_ONLY))
        }
        assertNull(deliver(center.copy(filledFraction = .8f), at))
        assertNull(deliver(center.copy(filledFraction = .2f), at + 50_000_000L))
        assertNull(deliver(normal.copy(filledFraction = .4f), at + 1_000_000_000L))
        assertNull(deliver(normal.copy(filledFraction = .4f), at + 1_050_000_000L))
        val ticks = (1..8).mapNotNull { deliver(normal.copy(filledFraction = .3f),
            at + 1_050_000_000L + it * 50_000_000L) }
        assertEquals(1, ticks.size)
        assertEquals(.4f, ticks.single().beforeFraction, .001f)
        assertEquals(.3f, ticks.single().afterFraction, .001f)
    }

    @Test fun `appearance boundary seeds a lower normal bar without a false hit`() {
        val watch = seeded()
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(normal.copy(filledFraction = 1f), base)
        watch.appearanceBoundary(opponent, base + 1)
        val disposition = watch.fastMoveDisposition(opponent, normal, base + 2)
        assertEquals(ChargedImpactHpWatchPolicy.FastMoveDisposition.BASELINE_ONLY, disposition)
        assertNull(detector.accept(LiveActiveHpBarSample(normal.copy(filledFraction = .4f), null, null,
            0, base + 2, fastMoveEvidenceAllowed = false, baselineOnly = true)))
        assertNull(detector.accept(normal.copy(filledFraction = .4f), base + 50_000_000L))
    }

    @Test fun `unavailable frame does not consume the window or turn into zero`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        assertNotNull(watch.request(672, 1419, base + 3_000_000_000L))
        assertNotNull(watch.request(672, 1419, base + 3_100_000_000L))
        assertNull(watch.request(672, 1419, base + 12_000_000_001L))
    }

    @Test fun `used cue keeps a later empty frame observable after early green acquisition expires`() {
        val watch = seeded()
        watch.prepare(opponent, base, "get-ready")
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        assertFalse(watch.canCapture(acquired + 3_000_000_000L))
        assertNull(watch.request(672, 1419, acquired + 3_000_000_000L))

        watch.impact(base + 8_000_000_000L, "used")
        val emptyAt = base + 8_100_000_000L
        assertTrue(watch.canCapture(emptyAt))
        val later = requireNotNull(watch.request(672, 1419, emptyAt))
        assertEquals(opponent, later.side)
        assertEquals(listOf("get-ready", "used"), later.cueArticleIds)
        assertNull("The old outline must be reacquired after the tracking gap", later.anchor)
        assertTrue(watch.accept(later, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY), emptyAt))
        assertFalse(watch.canCapture(emptyAt + 33_000_000L))
    }

    @Test fun `the first used cue bounds the window and later duplicates cannot slide it`() {
        val watch = seeded()
        watch.prepare(opponent, base, "ready")
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        watch.impact(base + 8_000_000_000L, "used-first")
        watch.impact(base + 8_000_000_000L, "used-first")
        watch.impact(base + 9_000_000_000L, "used-repeat")
        watch.impact(base + 10_000_000_000L, "used-repeat-later")

        val deadline = base + 11_000_000_000L
        assertTrue(watch.canCapture(deadline))
        val finalRequest = requireNotNull(watch.request(672, 1419, deadline))
        assertEquals(1, finalRequest.cueArticleIds.count { it == "used-first" })
        assertFalse(watch.canCapture(deadline + 1))
        assertNull(watch.request(672, 1419, deadline + 1))
    }

    @Test fun `an earlier source impact tightens the same deadline when delivery is reversed`() {
        for (reverse in listOf(false, true)) {
            val watch = seeded()
            watch.prepare(opponent, base)
            val acquired = base + 2_100_000_000L
            val request = requireNotNull(watch.request(672, 1419, acquired))
            assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
                HpBarFillColor.GREEN), acquired))
            val cues = listOf(base + 8_000_000_000L to "earlier-source", base + 9_000_000_000L to "later-source")
            (if (reverse) cues.reversed() else cues).forEach { (at, id) -> watch.impact(at, id) }

            val deadline = base + 11_000_000_000L
            assertTrue("reverse=$reverse", watch.canCapture(deadline))
            assertNotNull(watch.request(672, 1419, deadline))
            assertFalse("reverse=$reverse", watch.canCapture(deadline + 1))
            assertNull(watch.request(672, 1419, deadline + 1))
        }
    }

    @Test fun `a last in-phase used cue has at most fifteen seconds from preparation`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        watch.impact(base + 12_000_000_000L, "last-in-phase")
        watch.impact(base + 14_000_000_000L, "duplicate")
        val deadline = base + 15_000_000_000L
        assertTrue(watch.canCapture(deadline))
        assertNotNull(watch.request(672, 1419, deadline))
        assertFalse(watch.canCapture(deadline + 1))
        assertNull(watch.request(672, 1419, deadline + 1))
    }

    @Test fun `an acquired outline without a used cue retains its three second bound`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        assertTrue(watch.canCapture(acquired + 2_999_999_999L))
        assertFalse(watch.canCapture(acquired + 3_000_000_000L))
        assertNull(watch.request(672, 1419, acquired + 3_000_000_000L))
    }

    @Test fun `a used cue delivered after its source deadline cannot reopen current or backdated frames`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        assertNull(watch.request(672, 1419, base + 12_000_000_000L))
        // The announcement arrives now, but still describes its original +8 s source frame.
        watch.impact(base + 8_000_000_000L, "delayed-used")
        assertFalse(watch.canCapture(base + 12_100_000_000L))
        assertNull(watch.request(672, 1419, base + 12_100_000_000L))
        assertNull(watch.request(672, 1419, base + 8_100_000_000L))
    }

    @Test fun `out of phase used cues cannot revive an expired early acquisition`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val acquired = base + 2_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, acquired))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center.copy(filledFraction = .8f),
            HpBarFillColor.GREEN), acquired))
        watch.impact(base - 1, "older-phase")
        watch.impact(base + 12_000_000_001L, "after-phase")
        assertFalse(watch.canCapture(base + 13_000_000_000L))
        assertNull(watch.request(672, 1419, base + 13_000_000_000L))
    }

    @Test fun `a used cue cannot reopen a consumed empty observation`() {
        val watch = seeded()
        watch.prepare(opponent, base)
        val emptyAt = base + 3_000_000_000L
        val request = requireNotNull(watch.request(672, 1419, emptyAt))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(center, HpBarFillColor.EMPTY), emptyAt))
        watch.impact(base + 8_000_000_000L, "used")
        assertFalse(watch.canCapture(base + 8_100_000_000L))
        assertNull(watch.request(672, 1419, base + 8_100_000_000L))
    }
}
