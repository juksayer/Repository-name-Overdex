package com.example.overdex.ui.components

import com.example.overdex.presentation.DroidballPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballAcknowledgementMotionTest {
    @Test fun `response duration diminishes and the final response stops requesting frames`() {
        assertEquals(listOf(2320L, 2320L, 420L, 260L, 900L),
            (1..5).map(DroidballAcknowledgementMotion::durationMillis))
        assertEquals(900L, DroidballAcknowledgementMotion.durationMillis(6))
    }

    @Test fun `first two taps retain the same tilt double blink and return`() {
        for (time in 0L..2400L step 10L) {
            assertEquals(pose(time, 1), pose(time, 2))
        }
        assertTrue(pose(300L, 1).rollDegrees > 0f)
        assertEquals(15f, pose(900L, 1).rollDegrees, 0f)
        assertEquals(15f, pose(1720L, 1).rollDegrees, 0f)
        assertTrue(pose(2020L, 1).rollDegrees in 0f..15f)
        assertEquals(2, blinkCount(1))
        assertEquals(2, blinkCount(2))
        assertEquals(0f, pose(999L, 1).blink, 0f)
        assertEquals(1f, pose(1000L, 1).blink, 0f)
        assertEquals(0f, pose(1060L, 1).blink, 0f)
        assertEquals(1f, pose(1160L, 1).blink, 0f)
        assertEquals(0f, pose(1220L, 1).blink, 0f)
    }

    @Test fun `third tap blinks twice upright and fourth tap blinks once upright`() {
        for (tapCount in 3..4) {
            for (time in 0L..500L step 10L) {
                assertEquals(0f, pose(time, tapCount).rollDegrees, 0f)
            }
            assertEquals(0f, pose(99L, tapCount).blink, 0f)
            assertEquals(1f, pose(100L, tapCount).blink, 0f)
            assertEquals(0f, pose(160L, tapCount).blink, 0f)
        }
        assertEquals(2, blinkCount(3))
        assertEquals(1, blinkCount(4))
        assertEquals(1f, pose(260L, 3).blink, 0f)
        assertEquals(0f, pose(320L, 3).blink, 0f)
        assertEquals(0f, pose(260L, 4).blink, 0f)
    }

    @Test fun `fifth tap closes immediately and holds its tilted sleeping pose after motion ends`() {
        assertEquals(1f, pose(0L, 5).blink, 0f)
        assertEquals(0f, pose(0L, 5).rollDegrees, 0f)
        assertTrue(pose(300L, 5).rollDegrees > 0f)
        val sleeping = DroidballPose(rollDegrees = 15f, blink = 1f)
        for (tapCount in listOf(5, 6, Int.MAX_VALUE)) {
            for (time in listOf(900L, 2320L, 60_000L, Long.MAX_VALUE)) {
                assertEquals(sleeping, pose(time, tapCount))
            }
        }
    }

    @Test fun `nonterminal responses finish fully neutral and stay at rest`() {
        for (tapCount in 1..4) {
            val duration = DroidballAcknowledgementMotion.durationMillis(tapCount)
            assertEquals(DroidballPose(), pose(duration, tapCount))
            assertEquals(DroidballPose(), pose(Long.MAX_VALUE, tapCount))
        }
    }

    @Test fun `invalid tap counts stay neutral and negative elapsed time means the first frame`() {
        for (tapCount in listOf(Int.MIN_VALUE, -1, 0)) {
            assertEquals(0L, DroidballAcknowledgementMotion.durationMillis(tapCount))
            assertEquals(DroidballPose(), pose(0L, tapCount))
            assertEquals(DroidballPose(), pose(Long.MAX_VALUE, tapCount))
        }
        for (tapCount in 1..5) {
            assertEquals(pose(0L, tapCount), pose(-100L, tapCount))
        }
    }

    private fun pose(time: Long, tapCount: Int) = DroidballAcknowledgementMotion.pose(time, tapCount)

    private fun blinkCount(tapCount: Int): Int {
        val duration = DroidballAcknowledgementMotion.durationMillis(tapCount)
        val samples = (0L..duration).map { pose(it, tapCount).blink > .5f }
        return samples.zipWithNext().count { (before, after) -> !before && after }
    }
}
