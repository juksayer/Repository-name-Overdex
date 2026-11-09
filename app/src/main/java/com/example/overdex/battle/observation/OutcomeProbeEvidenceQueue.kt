package com.example.overdex.battle.observation

import java.util.PriorityQueue

/**
 * Retains a few sparse result checks even when the visual text probe is weak.
 * A newer blank must not replace the only frame containing a brief result while
 * native OCR is occupied. This queue holds references, never decoded bitmaps.
 */
internal class OutcomeProbeEvidenceQueue<T>(
    private val capacity: Int = 16,
    private val maximumAgeNanos: Long = 8_000_000_000L,
) {
    init { require(capacity > 0 && maximumAgeNanos > 0) }

    data class Selection<T>(val value: T, val pendingCount: Int, val overflowTotal: Long, val expiredTotal: Long)
    private data class Entry<T>(val value: T, val capturedAt: Long, val order: Long)
    private val entries = PriorityQueue(compareBy<Entry<T>> { it.capturedAt }.thenBy { it.order })
    private var nextOrder = 0L
    private var overflowTotal = 0L
    private var expiredTotal = 0L

    @Synchronized fun offer(value: T, capturedAtNanos: Long, nowNanos: Long) {
        expire(nowNanos)
        if (capturedAtNanos > nowNanos || isExpired(capturedAtNanos, nowNanos)) {
            expiredTotal++
            return
        }
        entries += Entry(value, capturedAtNanos, nextOrder++)
        while (entries.size > capacity) { entries.remove(); overflowTotal++ }
    }

    @Synchronized fun hasPending(nowNanos: Long): Boolean {
        expire(nowNanos)
        return entries.isNotEmpty()
    }

    @Synchronized fun select(nowNanos: Long): Selection<T>? {
        expire(nowNanos)
        val entry = entries.poll() ?: return null
        return Selection(entry.value, entries.size, overflowTotal, expiredTotal)
    }

    @Synchronized fun clear() { entries.clear() }

    private fun expire(nowNanos: Long) {
        val invalid = entries.filter { it.capturedAt > nowNanos || isExpired(it.capturedAt, nowNanos) }
        entries.removeAll(invalid.toSet())
        expiredTotal += invalid.size
    }

    private fun isExpired(capturedAt: Long, nowNanos: Long): Boolean {
        val cutoff = if (nowNanos < Long.MIN_VALUE + maximumAgeNanos) Long.MIN_VALUE
            else nowNanos - maximumAgeNanos
        return capturedAt < cutoff
    }
}
