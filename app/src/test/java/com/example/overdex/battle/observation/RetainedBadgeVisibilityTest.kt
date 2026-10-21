package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class RetainedBadgeVisibilityTest {
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
