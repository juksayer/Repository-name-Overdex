package com.example.overdex.battle.forecast

/**
 * Disposable forecast clock fed by the existing two-bar tracker. A missing bar
 * pauses extrapolation; it does not testify that a QTE, faint, or switch occurred.
 * No pixels or predicted attacks are retained here.
 */
internal class ForecastBattlefieldVisibility {
    private data class Span(val start: Long, var lastSeen: Long, var missingAt: Long? = null) {
        // One isolated apparent pair during an effect is not enough to restart
        // the clock. Confirm the field across two separately captured frames.
        val end get() = if (lastSeen == start) start else missingAt ?: (lastSeen + STALE_FRAME_NANOS)
    }
    private val spans = mutableListOf<Span>()
    private var latestAt = Long.MIN_VALUE

    fun observe(at: Long, bothBarsVisible: Boolean) {
        if (at <= latestAt) return
        latestAt = at
        val last = spans.lastOrNull()
        if (!bothBarsVisible) {
            if (last != null && last.missingAt == null) last.missingAt = minOf(at, last.end)
            return
        }
        // A single failed frame should not repeatedly reset a multi-turn move.
        // Until it recovers, even that short uncertainty freezes the display.
        if (last != null && at - (last.missingAt ?: last.lastSeen) <=
            (if (last.missingAt == null) STALE_FRAME_NANOS else BRIEF_GAP_NANOS)) {
            last.lastSeen = at
            last.missingAt = null
        } else spans += Span(at, at)
    }

    fun contains(at: Long): Boolean = spans.any { at >= it.start && at < it.end }

    fun elapsed(from: Long, until: Long): Long = spans.sumOf {
        (minOf(until, it.end) - maxOf(from, it.start)).coerceAtLeast(0L)
    }

    fun completedCycles(from: Long, until: Long, cycleNanos: Long): Int = spans.sumOf {
        ((minOf(until, it.end) - maxOf(from, it.start)).coerceAtLeast(0L) / cycleNanos).toInt()
    }

    fun clear() { spans.clear(); latestAt = Long.MIN_VALUE }

    private companion object {
        const val BRIEF_GAP_NANOS = 250_000_000L
        const val STALE_FRAME_NANOS = 500_000_000L
    }
}
