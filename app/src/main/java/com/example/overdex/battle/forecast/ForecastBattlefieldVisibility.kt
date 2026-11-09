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

    /**
     * Earliest source time whose visibility changed, or null for a frame that
     * changes no interval. A forecast can keep its replay checkpoint when only
     * the unprocessed tail grows. Confirmation and brief-gap recovery can
     * revise an earlier interval and must be reconciled from that time.
     */
    fun observe(at: Long, bothBarsVisible: Boolean): Long? {
        if (at <= latestAt) return null
        latestAt = at
        val last = spans.lastOrNull()
        if (!bothBarsVisible) {
            if (last != null && last.missingAt == null) {
                val oldEnd = last.end
                last.missingAt = minOf(at, oldEnd)
                return minOf(oldEnd, last.end).takeIf { last.end != oldEnd }
            }
            return null
        }
        // A single failed frame should not repeatedly reset a multi-turn move.
        // Until it recovers, even that short uncertainty freezes the display.
        if (last != null && at - (last.missingAt ?: last.lastSeen) <=
            (if (last.missingAt == null) STALE_FRAME_NANOS else BRIEF_GAP_NANOS)) {
            val oldEnd = last.end
            last.lastSeen = at
            last.missingAt = null
            return minOf(oldEnd, last.end).takeIf { last.end != oldEnd }
        } else spans += Span(at, at)
        // A new unconfirmed span has no visible duration yet.
        return null
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
