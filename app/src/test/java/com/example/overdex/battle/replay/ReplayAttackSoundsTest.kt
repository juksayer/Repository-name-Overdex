package com.example.overdex.battle.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayAttackSoundsTest {
    private val fast = ReplayAttackSoundEvent(100L, "Spark", false)
    private val charge = ReplayAttackSoundEvent(200L, "Thunder Punch", true)

    @Test fun forwardPlaybackDoesNotRepeatBoundarySounds() {
        val events = listOf(fast, charge)
        assertEquals(listOf(fast), replayAttackSoundsBetween(events, 0L, 100L))
        assertEquals(listOf(charge), replayAttackSoundsBetween(events, 100L, 200L))
    }

    @Test fun backwardAndStationaryCursorsDoNotPlay() {
        assertTrue(replayAttackSoundsBetween(listOf(fast), 200L, 0L).isEmpty())
        assertTrue(replayAttackSoundsBetween(listOf(fast), 100L, 100L).isEmpty())
    }

    @Test fun frameSkippingPreservesBothAttackKinds() {
        assertEquals(listOf(fast, charge), replayAttackSoundsBetween(listOf(fast, charge), 0L, 300L))
    }
}
