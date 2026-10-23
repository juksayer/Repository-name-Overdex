package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class RetainedBadgeVisibilityTest {
    private val matchScope = RetainedBadgeScope("match-a", 0L)
    private fun RetainedBadgeVisibility.update(at: Long, playerPresent: Boolean, opponentPresent: Boolean,
                                              sourceCovered: Boolean) =
        update(at, playerPresent, opponentPresent, sourceCovered, matchScope, battleSurfaceEstablished = true)

    @Test fun `overworld colors cannot acquire or retain a badge without a battle surface`() {
        val state = RetainedBadgeVisibility()
        repeat(30) { frame ->
            val decision = state.update(frame * 100_000_000L, true, true, false,
                matchScope, battleSurfaceEstablished = false)
            assertFalse(decision.cacheOriginal)
            assertFalse(decision.showRetained)
        }
        assertFalse(state.update(4_000_000_000L, false, false, false).showRetained)
        // Navigating to a real battle recovers in this same session.
        assertTrue(state.update(5_000_000_000L, true, true, false).cacheOriginal)
        state.update(5_100_000_000L, false, false, false)
        assertTrue(state.update(5_300_000_000L, false, false, false).showRetained)
    }

    @Test fun `leaving battle discards a displayed copy immediately`() {
        val state = RetainedBadgeVisibility()
        state.update(0, true, true, false)
        state.update(1, false, false, false)
        assertTrue(state.update(200_000_000L, false, false, false).showRetained)
        val leaving = state.update(300_000_000L, false, false, true, matchScope,
            battleSurfaceEstablished = false)
        assertTrue(leaving.discardOriginal)
        assertFalse(leaving.showRetained)
        assertFalse(state.update(600_000_000L, false, false, false).showRetained)
    }

    @Test fun `next match and restart cannot reuse an old badge even if mode changes are conflated`() {
        for (newScope in listOf(RetainedBadgeScope("match-b", 0L), RetainedBadgeScope("match-a", 10L))) {
            val state = RetainedBadgeVisibility()
            state.update(0, true, true, false)
            state.update(1, false, false, false)
            assertTrue(state.update(200_000_000L, false, false, false).showRetained)
            val fresh = state.update(300_000_000L, false, false, true, newScope,
                battleSurfaceEstablished = true)
            assertTrue(fresh.discardOriginal)
            assertFalse(fresh.showRetained)
            assertTrue(state.update(600_000_000L, true, true, false, newScope,
                battleSurfaceEstablished = true).cacheOriginal)
        }
    }

    @Test fun `a partly overlapping HUD cannot be included in the retained image`() {
        val state = RetainedBadgeVisibility()
        assertFalse(state.update(0, true, true, false, matchScope,
            battleSurfaceEstablished = true, sourceOverlapsHud = true).cacheOriginal)
        state.update(1, false, false, false)
        assertFalse(state.update(200_000_000L, false, false, false).showRetained)
    }

    @Test fun neverInventsABadgeWithoutAnOriginal() {
        val state = RetainedBadgeVisibility()
        state.update(0, false, false, false)
        assertFalse(state.update(1_000_000_000, false, false, false).showRetained)
    }

    @Test fun briefDropoutDoesNotBlinkButSustainedAbsenceRetainsLastBadge() {
        val state = RetainedBadgeVisibility()
        assertTrue(state.update(0, true, true, false).cacheOriginal)
        assertFalse(state.update(10, false, false, false).showRetained)
        assertFalse(state.update(60_000_000, true, true, false).showRetained)
        state.update(100_000_000, false, false, false)
        assertFalse(state.update(150_000_000, false, false, false).showRetained)
        assertTrue(state.update(200_000_000, false, false, false).showRetained)
    }

    @Test fun copiedOpponentBadgeCannotSignalItsOwnReturn() {
        val state = RetainedBadgeVisibility()
        state.update(0, true, true, false)
        state.update(10, false, false, false)
        state.update(200_000_000, false, false, false)
        val ownPixels = state.update(300_000_000, false, true, true)
        assertTrue(ownPixels.showRetained)
        assertFalse(ownPixels.cacheOriginal)
        val playerReturns = state.update(400_000_000, true, true, true)
        assertFalse(playerReturns.showRetained)
        assertFalse(playerReturns.cacheOriginal)
        assertFalse(state.update(500_000_000, true, true, true).cacheOriginal)
        assertTrue(state.update(700_000_000, true, true, false).cacheOriginal)
    }

    @Test fun OneMissingBadgeDoesNotHideAnExistingOriginal() {
        val state = RetainedBadgeVisibility()
        state.update(0, true, true, false)
        state.update(1, true, false, false)
        assertFalse(state.update(1_000_000_000, true, false, false).showRetained)
        assertFalse(state.update(2_000_000_000, false, true, false).showRetained)
    }

    @Test fun NewMatchMustAcquireItsOwnBadge() {
        val state = RetainedBadgeVisibility()
        state.update(0, true, true, false)
        state.update(1, false, false, false)
        assertTrue(state.update(200_000_000, false, false, false).showRetained)
        state.reset()
        assertFalse(state.update(300_000_000, false, false, false).showRetained)
        assertFalse(state.update(800_000_000, false, false, false).showRetained)
    }
}
