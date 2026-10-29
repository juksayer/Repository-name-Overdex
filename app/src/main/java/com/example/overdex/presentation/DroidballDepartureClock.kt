package com.example.overdex.presentation

/** Advances departure only on displayed frames, preserving motion through startup stalls. */
class DroidballDepartureClock(private val durationMillis: Long) {
    init {
        require(durationMillis >= 0L) { "Departure duration must not be negative" }
    }

    private var previousFrameNanos: Long? = null
    private var elapsedMillis = 0L
    private var remainderNanos = 0L

    fun frameElapsedMillis(frameNanos: Long): Long {
        val previous = previousFrameNanos
        if (previous == null) {
            previousFrameNanos = frameNanos
            return 0L
        }
        if (frameNanos <= previous) return elapsedMillis
        previousFrameNanos = frameNanos
        if (elapsedMillis == durationMillis) return elapsedMillis

        val difference = frameNanos - previous
        // A stalled frame cannot consume the roll. An overflowing positive gap
        // is also a long stall, rather than a negative step.
        val stepNanos = if (difference < 0L) MAX_FRAME_STEP_NANOS
            else difference.coerceAtMost(MAX_FRAME_STEP_NANOS)
        val accumulatedNanos = remainderNanos + stepNanos
        remainderNanos = accumulatedNanos % NANOS_PER_MILLI
        elapsedMillis += (accumulatedNanos / NANOS_PER_MILLI)
            .coerceAtMost(durationMillis - elapsedMillis)
        return elapsedMillis
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_FRAME_STEP_NANOS = 50L * NANOS_PER_MILLI
    }
}
