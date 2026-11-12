package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class ChargedHpReferenceOwnershipTest {
    private val opponent = ActivePokemonSide.OPPONENT
    private val player = ActivePokemonSide.PLAYER
    private val base = 20_000_000_000L
    private val bar = ActiveHpBarMeasurement(387, 625, 580, 642, .76f, .95f)
    private val centered = bar.copy(left = 239, right = 432, top = 763, bottom = 780)

    private fun watch() = ChargedImpactHpWatchPolicy().apply { request(672, 1419, base - 2_000_000_000L) }

    @Test fun `delayed identity preserves later geometry but prunes earlier and equal geometry`() {
        for (offset in listOf(-1L, 0L, 1L)) {
            for (identityFirst in listOf(false, true)) {
                val watch = watch()
                if (identityFirst) watch.appearanceBoundary(opponent, base)
                watch.seed(opponent, bar, base + offset)
                if (!identityFirst) watch.appearanceBoundary(opponent, base)
                assertEquals("offset=$offset identityFirst=$identityFirst",
                    if (offset > 0) ChargedImpactHpWatchPolicy.PreparationResult.PREPARED
                    else ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
                    watch.prepare(opponent, base + 1_000_000_000L))
            }
        }
    }

    @Test fun `delayed identity keeps acquired geometry and original impact deadline`() {
        val watch = watch()
        watch.seed(opponent, bar, base)
        watch.prepare(opponent, base + 1_000_000_000L, "ready")
        watch.impact(base + 4_000_000_000L, "used")
        val at = base + 4_100_000_000L
        val request = requireNotNull(watch.request(672, 1419, at))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(centered, HpBarFillColor.GREEN), at))
        val before = watch.preparationDescription()

        watch.appearanceBoundary(opponent, base - 1)

        assertTrue(watch.current(request))
        assertEquals(before, watch.preparationDescription())
        val next = requireNotNull(watch.request(672, 1419, at + 33_000_000L))
        assertEquals(request.token, next.token)
        assertEquals(centered, next.anchor)
        assertEquals(listOf("ready", "used"), next.cueArticleIds)
        assertTrue(watch.canCapture(base + 7_000_000_000L))
        assertFalse(watch.canCapture(base + 7_000_000_001L))
    }

    @Test fun `an identity correction cannot reopen an already consumed empty frame`() {
        val watch = watch()
        watch.seed(opponent, bar, base)
        watch.prepare(opponent, base + 1_000_000_000L)
        val at = base + 4_000_000_000L
        val request = requireNotNull(watch.request(672, 1419, at))
        assertTrue(watch.accept(request,
            TrackedTerminalHpBarSample(centered.copy(filledFraction = 0f), HpBarFillColor.EMPTY), at))
        watch.appearanceBoundary(opponent, base - 1)
        assertFalse(watch.canCapture(at + 1))
        assertNull(watch.request(672, 1419, at + 1))
    }

    @Test fun `charge prepared after a replacement still cannot use its predecessor's geometry`() {
        val watch = watch()
        watch.seed(opponent, bar, base)
        watch.prepare(opponent, base + 2_000_000_000L)
        val request = requireNotNull(watch.request(672, 1419, base + 4_000_000_000L))
        watch.appearanceBoundary(opponent, base + 1_000_000_000L)
        assertFalse(watch.current(request))
        assertFalse(watch.accept(request, TrackedTerminalHpBarSample(centered, HpBarFillColor.GREEN),
            base + 4_000_000_000L))
        assertFalse(watch.canCapture(base + 4_000_000_001L))
    }

    @Test fun `stale geometry does not become fresh when identity arrives late`() {
        val watch = watch()
        watch.seed(opponent, bar, base)
        watch.appearanceBoundary(opponent, base - 1)
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.STALE_REFERENCE,
            watch.prepare(opponent, base + ChargedImpactHpWatchPolicy.MAX_REFERENCE_AGE_NANOS + 1))
    }

    @Test fun `older repeated identities cannot rewind a newer boundary or erase another side`() {
        val watch = watch()
        watch.seed(player, bar.copy(left = 35, right = 228), base)
        watch.appearanceBoundary(opponent, base + 2)
        watch.appearanceBoundary(opponent, base + 1)
        watch.appearanceBoundary(opponent, base + 2)
        watch.seed(opponent, bar, base + 1)
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            watch.prepare(opponent, base + 1_000_000_000L))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
            watch.prepare(player, base + 2_000_000_000L))
    }

    @Test fun `hard reset clears even newer geometry and old identity cannot resurrect it`() {
        val watch = watch()
        watch.seed(opponent, bar, base + 1)
        watch.reset(opponent, base)
        watch.appearanceBoundary(opponent, base - 1)
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            watch.prepare(opponent, base + 1_000_000_000L))
    }

    @Test fun `identity during an ordinary scan preserves later charged geometry without publishing old terminal ownership`() {
        val watch = watch()
        val terminal = TerminalHpWatchPolicy()
        val epoch = watch.snapshotReferenceEpoch(opponent)
        val generation = terminal.snapshotGeneration(opponent)
        // The normal scan has begun on frame base+1 when older identity completes.
        terminal.reset(opponent, base)
        watch.appearanceBoundary(opponent, base)
        assertFalse(terminal.isCurrentGeneration(opponent, generation))
        assertTrue(watch.seedIfCurrent(opponent, bar, 672, 1419, base + 1, epoch))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
            watch.prepare(opponent, base + 1_000_000_000L))
    }

    @Test fun `an ordinary scan cannot cross a later appearance even with the same epoch`() {
        val watch = watch()
        val epoch = watch.snapshotReferenceEpoch(opponent)
        watch.appearanceBoundary(opponent, base + 1)
        assertFalse(watch.seedIfCurrent(opponent, bar, 672, 1419, base, epoch))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            watch.prepare(opponent, base + 1_000_000_000L))
    }

    @Test fun `a hard reset invalidates in-flight scans even when their source time is later`() {
        for (allSides in listOf(false, true)) {
            val watch = watch()
            val epoch = watch.snapshotReferenceEpoch(opponent)
            if (allSides) watch.resetAll(base - 1) else watch.reset(opponent, base - 1)
            watch.appearanceBoundary(opponent, base - 2)
            assertFalse(watch.seedIfCurrent(opponent, bar, 672, 1419, base, epoch))
            assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
                watch.prepare(opponent, base + 1_000_000_000L))
        }
    }

    @Test fun `resize and return to the old size still invalidates an old in-flight scan`() {
        val watch = watch()
        val oldEpoch = watch.snapshotReferenceEpoch(opponent)
        watch.request(1080, 2400, base)
        val resizedEpoch = watch.snapshotReferenceEpoch(opponent)
        assertFalse(watch.seedIfCurrent(opponent, bar, 672, 1419, base + 1, resizedEpoch))
        assertFalse("Rejected old-size geometry cannot roll the domain backward",
            watch.seedIfCurrent(opponent, bar, 672, 1419, base + 2, resizedEpoch))
        watch.request(672, 1419, base + 3)
        assertFalse(watch.seedIfCurrent(opponent, bar, 672, 1419, base + 4, oldEpoch))
        assertTrue(watch.seedIfCurrent(opponent, bar, 672, 1419, base + 4,
            watch.snapshotReferenceEpoch(opponent)))
    }

    @Test fun `a hard reset on the other side preserves a pending normal reference`() {
        val watch = watch()
        val epoch = watch.snapshotReferenceEpoch(opponent)
        watch.reset(player, base)
        assertTrue(watch.seedIfCurrent(opponent, bar, 672, 1419, base + 1, epoch))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
            watch.prepare(opponent, base + 1_000_000_000L))
    }

    @Test fun `ordinary camera-centered pixels cannot replace the side-owned reference`() {
        val watch = watch()
        val epoch = watch.snapshotReferenceEpoch(opponent)
        watch.appearanceBoundary(opponent, base - 1)
        assertFalse(watch.seedIfCurrent(opponent, centered, 672, 1419, base, epoch))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            watch.prepare(opponent, base + 1_000_000_000L))
    }
}
