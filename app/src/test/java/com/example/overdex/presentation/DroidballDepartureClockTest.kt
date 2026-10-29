package com.example.overdex.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballDepartureClockTest {
    @Test fun `first delivered frame starts at zero regardless of its timestamp`() {
        val clock = DroidballDepartureClock(1600L)

        assertEquals(0L, clock.frameElapsedMillis(8_000_000_000L))
        assertEquals(16L, clock.frameElapsedMillis(8_016_000_000L))
    }

    @Test fun `startup and later stalls advance at most fifty milliseconds without catching up`() {
        val clock = DroidballDepartureClock(1600L)

        assertEquals(0L, clock.frameElapsedMillis(0L))
        assertEquals(50L, clock.frameElapsedMillis(2_000_000_000L))
        assertEquals(66L, clock.frameElapsedMillis(2_016_000_000L))
        assertEquals(116L, clock.frameElapsedMillis(9_000_000_000L))
    }

    @Test fun `duplicate and older frames neither advance nor move the baseline backward`() {
        val clock = DroidballDepartureClock(1600L)

        assertEquals(0L, clock.frameElapsedMillis(100_000_000L))
        assertEquals(20L, clock.frameElapsedMillis(120_000_000L))
        assertEquals(20L, clock.frameElapsedMillis(120_000_000L))
        assertEquals(20L, clock.frameElapsedMillis(90_000_000L))
        assertEquals(30L, clock.frameElapsedMillis(130_000_000L))
    }

    @Test fun `fractional milliseconds are retained across frames`() {
        val clock = DroidballDepartureClock(1600L)

        assertEquals(0L, clock.frameElapsedMillis(0L))
        assertEquals(16L, clock.frameElapsedMillis(16_666_667L))
        assertEquals(33L, clock.frameElapsedMillis(33_333_334L))
        assertEquals(50L, clock.frameElapsedMillis(50_000_001L))
    }

    @Test fun `departure reaches its exact duration and stays there`() {
        val clock = DroidballDepartureClock(75L)

        assertEquals(0L, clock.frameElapsedMillis(0L))
        assertEquals(50L, clock.frameElapsedMillis(50_000_000L))
        assertEquals(75L, clock.frameElapsedMillis(100_000_000L))
        assertEquals(75L, clock.frameElapsedMillis(150_000_000L))
        assertEquals(75L, clock.frameElapsedMillis(90_000_000L))
    }

    @Test fun `zero duration remains complete from the first frame`() {
        val clock = DroidballDepartureClock(0L)

        assertEquals(0L, clock.frameElapsedMillis(5_000_000L))
        assertEquals(0L, clock.frameElapsedMillis(500_000_000L))
    }
}
