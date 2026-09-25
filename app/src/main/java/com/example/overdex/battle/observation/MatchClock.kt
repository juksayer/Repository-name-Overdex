package com.example.overdex.battle.observation

import com.example.overdex.battle.time.ClockReading
import com.example.overdex.battle.time.ExternalClock
import com.example.overdex.battle.time.SystemExternalClock

/**
 * The independent monotonic metronome used to scrutinize battle evidence.
 *
 * It is available before, during, and after GO. Captured frames receive a
 * reading from the same monotonic authority, but no frame advances this clock.
 */
class MatchClock(
    private val externalClock: ExternalClock = SystemExternalClock
) {
    fun read(): ClockReading = externalClock.read()

    fun elapsedNanosBetween(earlier: ClockReading, later: ClockReading): Long =
        (later.monotonicTimeNanos - earlier.monotonicTimeNanos).coerceAtLeast(0L)
}
