package com.example.overdex.battle.observation

import java.util.PriorityQueue

/**
 * Keeps a small, source-ordered window of result evidence ready for live OCR.
 * Older references remain available to a separate background recovery reader.
 * Moving a reference between lanes never changes or removes its source Article.
 * The defaults cover three seconds at the result capture rate of four per
 * second; they bound live work rather than promising an OCR completion time.
 */
internal class OutcomeEvidenceQueue<T>(
    private val maxRecentSamples: Int = 12,
    private val recentWindowNanos: Long = 3_000_000_000L,
) {
    init {
        require(maxRecentSamples > 0) { "maxRecentSamples must be positive" }
        require(recentWindowNanos > 0) { "recentWindowNanos must be positive" }
    }

    internal data class Counts(val recent: Int, val recovery: Int) {
        val total: Int get() = recent + recovery
    }

    private class Entry<T>(val value: T, val capturedAtNanos: Long, val order: Long)

    private val ordering = compareBy<Entry<T>> { it.capturedAtNanos }.thenBy { it.order }
    private val recent = PriorityQueue(ordering)
    private val recovery = PriorityQueue(ordering)
    private var nextOrder = 0L
    private var closed = false

    /** Stores immutable references only; a closed result reader accepts no more work. */
    @Synchronized
    fun offer(value: T, capturedAtNanos: Long, nowNanos: Long): Boolean {
        if (closed) return false
        refresh(nowNanos)
        val entry = Entry(value, capturedAtNanos, nextOrder++)
        if (isRecent(capturedAtNanos, nowNanos)) {
            recent.add(entry)
            while (recent.size > maxRecentSamples) recovery.add(recent.remove())
        } else {
            // Future source times must not obtain live priority either.
            recovery.add(entry)
        }
        return true
    }

    /** Rechecks age when OCR is admitted, then takes the oldest still-live source. */
    @Synchronized
    fun pollRecent(nowNanos: Long): T? {
        refresh(nowNanos)
        return recent.poll()?.value
    }

    @Synchronized
    fun pollRecovery(nowNanos: Long): T? {
        refresh(nowNanos)
        return recovery.poll()?.value
    }

    @Synchronized
    fun counts(nowNanos: Long): Counts {
        refresh(nowNanos)
        return Counts(recent.size, recovery.size)
    }

    @Synchronized
    fun close() {
        closed = true
        recent.clear()
        recovery.clear()
    }

    private fun refresh(nowNanos: Long) {
        // Source order lets the usual monotonic-clock path expire a prefix.
        while (recent.peek()?.let { !isRecent(it.capturedAtNanos, nowNanos) } == true) {
            recovery.add(recent.remove())
        }
        // A backwards test clock or invalid future timestamp cannot leave an
        // entry in the live lane, even when it follows a valid oldest entry.
        val future = recent.filter { it.capturedAtNanos > nowNanos }
        if (future.isNotEmpty()) {
            recent.removeAll(future.toSet())
            recovery.addAll(future)
        }
    }

    private fun isRecent(capturedAtNanos: Long, nowNanos: Long): Boolean {
        val cutoff = if (nowNanos < Long.MIN_VALUE + recentWindowNanos) Long.MIN_VALUE
            else nowNanos - recentWindowNanos
        return capturedAtNanos in cutoff..nowNanos
    }
}
