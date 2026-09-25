package com.example.overdex.battle.time

/**
 * The common temporal authority for evidence recorded during every Match.
 *
 * Wall-clock time makes records legible outside the device. Monotonic time is
 * used to correlate evidence without being affected by wall-clock adjustments.
 */
interface ExternalClock {
    fun read(): ClockReading
}

data class ClockReading(
    val wallTimeMillis: Long,
    val monotonicTimeNanos: Long
)

object SystemExternalClock : ExternalClock {
    override fun read() = ClockReading(
        wallTimeMillis = System.currentTimeMillis(),
        monotonicTimeNanos = System.nanoTime()
    )
}
