package com.example.overdex.battle.observation

import com.example.overdex.model.observation.VisualFramePublication

internal data class TerminalVisualCaptureDiagnosticSummary(
    val text: String,
    val sourceAt: Long,
    val wallTime: Long,
    val evidenceReferences: List<String>,
)

/**
 * Bitmap-free, per-window accounting. Nothing here is an absence or faint
 * witness. A retained PNG is not necessarily a published CropCaptured article;
 * the capture gate reports actual published crops independently.
 *
 * Only admitted operations awaiting completion/order retirement are retained.
 * In particular this is not a queue of frames to process later. All methods
 * share one lock because expiry, receipt and completion use different coroutines.
 */
internal class TerminalVisualCaptureDiagnostics {
    private data class Pending(var started: Boolean = false, var finished: Boolean = false,
        var retained: Boolean = false)

    private class Window(val window: TerminalVisualCaptureWindow, val refs: List<String>, var wallTime: Long) {
        var received = 0L
        var rejected = 0L
        var stale = 0L
        var outside = 0L
        var admitted = 0L
        var started = 0L
        var completed = 0L
        var inFlight = 0L
        var peakInFlight = 0L
        var retainedPngs = 0L
        var bytes = 0L
        var lastSource: Long? = null
        var maxSourceGap = 0L
        var sourceReorders = 0L
        var lastAdmittedSource: Long? = null
        var invalidAdmissions = 0L
        var lastPreservedSource: Long? = null
        var maxPreserveSourceGap = 0L
        var previousPublication: VisualFramePublication? = null
        var sequenceAdvances = 0L
        var skipped = 0L
        var unknownPublications = 0L
        var oldPublications = 0L
        var streamChanges = 0L
        var elapsed = 0L
        var maxElapsed = 0L
        var copy = 0L
        var maxCopy = 0L
        var preserve = 0L
        var maxPreserve = 0L
        var cpu = 0L
        var cpuUnavailable = 0L
        val pending = linkedMapOf<Long, Pending>()
        val outcomes = linkedMapOf<String, Long>()
    }

    private val windows = linkedMapOf<Long, Window>()
    private val drainedIds = linkedSetOf<Long>()

    /** Idempotent. Refuses overflow instead of silently evicting unfinished accounting. */
    @Synchronized fun opened(
        window: TerminalVisualCaptureWindow,
        evidenceReferences: List<String> = emptyList(),
        wallTime: Long = 0L,
    ): Boolean {
        if (windows.containsKey(window.id)) return windows[window.id]?.window == window
        if (window.id in drainedIds) return false
        if (windows.size >= MAX_WINDOWS || window.until <= window.from) return false
        windows[window.id] = Window(window, evidenceReferences.asSequence().filter { it.isNotBlank() }
            .distinct().take(MAX_REFERENCES).toList(), wallTime)
        return true
    }

    @Synchronized fun received(
        window: TerminalVisualCaptureWindow,
        sourceAt: Long,
        wallTime: Long,
        publication: VisualFramePublication?,
        admitted: Boolean,
        stale: Boolean,
    ) {
        val stats = windows[window.id]?.takeIf { it.window == window } ?: return
        stats.wallTime = maxOf(stats.wallTime, wallTime)
        stats.received++
        if (stale) stats.stale++
        val within = sourceAt >= window.from && sourceAt < window.until
        if (!within) stats.outside++
        if (within) {
            stats.lastSource?.let { previous ->
                if (sourceAt > previous) stats.maxSourceGap = maxOf(stats.maxSourceGap, sourceAt - previous)
                else if (sourceAt < previous) stats.sourceReorders++
            }
            stats.lastSource = maxOf(stats.lastSource ?: sourceAt, sourceAt)
        }
        observePublication(stats, publication)
        if (!admitted) {
            stats.rejected++
            return
        }
        // The gate admits strictly advancing source times within this window.
        // Invalid callbacks cannot manufacture work or exceed the bounded budget.
        // Staleness is sampled after gate admission and can cross its age limit
        // in between. Record the actual admitted work rather than overruling it.
        if (!within || sourceAt <= (stats.lastAdmittedSource ?: Long.MIN_VALUE) ||
            stats.admitted >= MAX_ATTEMPTS) {
            stats.invalidAdmissions++
            stats.rejected++
            return
        }
        stats.admitted++
        stats.lastAdmittedSource = sourceAt
        stats.pending[sourceAt] = Pending()
    }

    @Synchronized fun started(ticket: TerminalVisualCaptureTicket) {
        val stats = windowFor(ticket) ?: return
        val pending = stats.pending[ticket.sourceAt]?.takeIf { !it.started && !it.finished } ?: return
        pending.started = true
        stats.started++
        stats.inFlight++
        stats.peakInFlight = maxOf(stats.peakInFlight, stats.inFlight)
    }

    @Synchronized fun finished(
        ticket: TerminalVisualCaptureTicket,
        outcome: String,
        elapsedNanos: Long,
        copyNanos: Long,
        preserveNanos: Long,
        cpuNanos: Long?,
        bytes: Long,
        wallTime: Long,
        retained: Boolean = outcome == "PRESERVED",
    ) {
        val stats = windowFor(ticket) ?: return
        val pending = stats.pending[ticket.sourceAt]?.takeIf { !it.finished } ?: return
        pending.finished = true
        pending.retained = retained
        stats.completed++
        if (pending.started) stats.inFlight--
        stats.wallTime = maxOf(stats.wallTime, wallTime)
        if (retained) {
            stats.retainedPngs++
            stats.bytes = add(stats.bytes, bytes)
        }
        stats.elapsed = add(stats.elapsed, elapsedNanos)
        stats.maxElapsed = maxOf(stats.maxElapsed, elapsedNanos.coerceAtLeast(0L))
        stats.copy = add(stats.copy, copyNanos)
        stats.maxCopy = maxOf(stats.maxCopy, copyNanos.coerceAtLeast(0L))
        stats.preserve = add(stats.preserve, preserveNanos)
        stats.maxPreserve = maxOf(stats.maxPreserve, preserveNanos.coerceAtLeast(0L))
        if (cpuNanos == null) stats.cpuUnavailable++ else stats.cpu = add(stats.cpu, cpuNanos)
        val bounded = outcome.uppercase().replace(Regex("[^A-Z0-9_]"), "_").take(64).ifEmpty { "UNKNOWN" }
        val key = if (bounded in stats.outcomes || stats.outcomes.size < MAX_OUTCOMES) bounded else "OTHER"
        stats.outcomes[key] = (stats.outcomes[key] ?: 0L) + 1L
        // Retire in admission/source order: a later asynchronous completion must
        // not create a fictitious preservation gap over an earlier pending crop.
        val iterator = stats.pending.iterator()
        while (iterator.hasNext()) {
            val (source, attempt) = iterator.next()
            if (!attempt.finished) break
            if (attempt.retained) {
                stats.lastPreservedSource?.let { previous ->
                    stats.maxPreserveSourceGap = maxOf(stats.maxPreserveSourceGap, source - previous)
                }
                stats.lastPreservedSource = source
            }
            iterator.remove()
        }
    }

    /**
     * One cumulative record per closed window. Pending work delays ordinary
     * drainage; shutdown may force a visibly partial record. [now] only decides
     * expiry and is never substituted for an image's source or wall timestamp.
     */
    @Synchronized fun drain(
        activeWindowIds: Set<Long>,
        now: Long,
        force: Boolean = false,
    ): List<TerminalVisualCaptureDiagnosticSummary> {
        val result = mutableListOf<TerminalVisualCaptureDiagnosticSummary>()
        val iterator = windows.iterator()
        while (iterator.hasNext()) {
            val (id, stats) = iterator.next()
            if (!force && id in activeWindowIds && now < stats.window.until) continue
            val pending = stats.admitted - stats.completed
            if (!force && pending > 0L) continue
            result += TerminalVisualCaptureDiagnosticSummary(summary(stats, pending, force),
                stats.lastSource ?: stats.window.from, stats.wallTime, stats.refs)
            drainedIds += id
            while (drainedIds.size > MAX_RECENT_DRAINED_IDS) drainedIds.remove(drainedIds.first())
            iterator.remove()
        }
        return result
    }

    private fun windowFor(ticket: TerminalVisualCaptureTicket): Window? =
        windows[ticket.windowId]?.takeIf { it.window.side == ticket.side }

    private fun observePublication(stats: Window, publication: VisualFramePublication?) {
        if (publication == null) {
            stats.unknownPublications++
            stats.previousPublication = null // Unknown receipts must not be counted as dropped known frames.
            return
        }
        val previous = stats.previousPublication
        if (previous == null) stats.previousPublication = publication
        else if (publication.streamId > previous.streamId) {
            stats.streamChanges++
            stats.previousPublication = publication
        } else if (publication.streamId == previous.streamId && publication.sequenceNumber > previous.sequenceNumber) {
            val advance = publication.sequenceNumber - previous.sequenceNumber
            stats.sequenceAdvances = add(stats.sequenceAdvances, advance)
            stats.skipped = add(stats.skipped, advance - 1L)
            stats.previousPublication = publication
        } else if (publication != previous) stats.oldPublications++
    }

    private fun summary(stats: Window, pending: Long, forced: Boolean): String = buildString {
        val window = stats.window
        append("kind=TERMINAL_VISUAL_CAPTURE windowId=${window.id} side=${window.side} trigger=${window.trigger}")
        append(" sourceFrom=${window.from} sourceUntil=${window.until} forced=$forced")
        append(" received=${stats.received} admitted=${stats.admitted} rejected=${stats.rejected}")
        append(" stale=${stats.stale} outsideWindow=${stats.outside} invalidAdmissions=${stats.invalidAdmissions}")
        append(" started=${stats.started} completed=${stats.completed} pending=$pending")
        append(" inFlight=${stats.inFlight} peakInFlight=${stats.peakInFlight}")
        append(" retainedPngs=${stats.retainedPngs} retainedBytes=${stats.bytes}")
        append(" maxSourceGapNanos=${stats.maxSourceGap} sourceReorders=${stats.sourceReorders}")
        append(" maxPreserveSourceGapNanos=${stats.maxPreserveSourceGap}")
        append(" preserveGapComplete=${pending == 0L}")
        append(" sequenceAdvances=${stats.sequenceAdvances} skipped=${stats.skipped}")
        append(" unknownPublications=${stats.unknownPublications} oldPublications=${stats.oldPublications}")
        append(" streamChanges=${stats.streamChanges}")
        append(" elapsedNanos=${stats.elapsed} maxAttemptNanos=${stats.maxElapsed}")
        append(" copyNanos=${stats.copy} maxCopyNanos=${stats.maxCopy}")
        append(" preserveNanos=${stats.preserve} maxPreserveNanos=${stats.maxPreserve}")
        append(" cpuNanos=${stats.cpu} cpuUnavailable=${stats.cpuUnavailable}")
        stats.outcomes.toSortedMap().forEach { (outcome, count) -> append(" outcome_$outcome=$count") }
    }

    private fun add(total: Long, value: Long): Long {
        val nonnegative = value.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - total < nonnegative) Long.MAX_VALUE else total + nonnegative
    }

    internal companion object {
        const val MAX_WINDOWS = 16
        private const val MAX_RECENT_DRAINED_IDS = 64
        private const val MAX_REFERENCES = 16
        private const val MAX_OUTCOMES = 16
        private const val MAX_ATTEMPTS = 60L
    }
}
