package com.example.overdex.battle.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayPlaybackClockTest {
    @Test fun delayedFramesDoNotSlowDownOneTimesPlayback() {
        val clock = ReplayPlaybackClock(10_000_000_000L, 100_000_000_000L, 1f)
        assertEquals(10_000_000_000L, clock.positionAt(1_000_000_000L))
        clock.positionAt(1_016_000_000L)
        clock.positionAt(1_500_000_000L)
        assertEquals(12_000_000_000L, clock.positionAt(3_000_000_000L))
    }

    @Test fun supportsSlowMotionAndFiveTimesPlayback() {
        for ((speed, expected) in listOf(0.1f to 200_000_000L, 2f to 4_000_000_000L, 5f to 10_000_000_000L)) {
            val clock = ReplayPlaybackClock(0L, 100_000_000_000L, speed)
            clock.positionAt(500L)
            assertEquals(expected.toDouble(), clock.positionAt(2_000_000_500L).toDouble(), 5.0)
        }
    }

    @Test fun pauseSeekAndSpeedChangesStartFromCurrentCursorWithoutCatchup() {
        val clock = ReplayPlaybackClock(0L, 100_000_000_000L, 1f)
        clock.positionAt(1_000L)
        val paused = clock.positionAt(1_000_001_000L)
        val resumed = ReplayPlaybackClock(paused, 100_000_000_000L, 5f)
        assertEquals(paused, resumed.positionAt(60_000_000_000L))
        assertEquals(6_000_000_000L, resumed.positionAt(61_000_000_000L))
        val sought = ReplayPlaybackClock(20_000_000_000L, 100_000_000_000L, 1f)
        assertEquals(20_000_000_000L, sought.positionAt(70_000_000_000L))
        assertEquals(21_000_000_000L, sought.positionAt(71_000_000_000L))
    }

    @Test fun stopsExactlyAtEndEvenWhenFrameArrivesLate() {
        val clock = ReplayPlaybackClock(98_000_000_000L, 100_000_000_000L, 5f)
        clock.positionAt(1_000L)
        assertEquals(100_000_000_000L, clock.positionAt(1_000_001_000L))
    }
}
