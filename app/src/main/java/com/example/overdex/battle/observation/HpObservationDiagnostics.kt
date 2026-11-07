package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.HpObservationPipelineMeasured
import com.example.overdex.battle.custody.HpPipelineSideStatistics
import com.example.overdex.battle.custody.HpPipelineStatistics
import com.example.overdex.battle.custody.HpPipelineTimingStatistics
import com.example.overdex.model.observation.VisualFramePublication
import kotlin.math.ceil

enum class HpDiagnosticSummaryReason { PERIODIC, MATCH_END, SESSION_STOP }

/**
 * Counters and fixed-size histograms for the HP pipeline. No image retention,
 * per-frame Articles, attack inference, or asynchronous diagnostic work. All
 * stages share one lock because Custody may publish on another thread.
 */
class HpObservationDiagnostics(
    private val summaryIntervalNanos: Long = 5_000_000_000L,
    private val maxPendingSubmissions: Int = 512,
) {
    init {
        require(summaryIntervalNanos > 0)
        require(maxPendingSubmissions in 1..512)
    }

    private var windowStartedAt: Long? = null
    private var window = Statistics()
    private val cumulative = Statistics()
    private var latestPublication: VisualFramePublication? = null
    private var previousReceivedPublication: VisualFramePublication? = null
    private var previousReceivedEnabled: Boolean? = null
    private var previousSource: Long? = null
    private var previousReceipt: Long? = null
    private val previousAccepted = mutableMapOf<ActivePokemonSide, Long>()
    private data class Key(val side: ActivePokemonSide, val sourceNanos: Long)
    private val pending = LinkedHashMap<Key, Long>()

    /** Begin the producer baseline even if no frame is ever delivered to the reader. */
    @Synchronized
    fun start(nowNanos: Long, latestPublication: VisualFramePublication?) {
        if (windowStartedAt != null) return
        startAt(nowNanos)
        latestPublication?.let(::observePublished)
    }

    @Synchronized
    fun onFrameReceived(
        sourceNanos: Long,
        receivedAtNanos: Long,
        publication: VisualFramePublication?,
        enabled: Boolean,
    ) {
        startAt(receivedAtNanos)
        both { framesReceived++; if (enabled) enabledFramesReceived++ else disabledFramesReceived++ }
        previousSource?.let { previous -> both { receivedSourceIntervals.add(sourceNanos - previous) } }
        previousReceipt?.let { previous -> both { receiptIntervals.add(receivedAtNanos - previous) } }
        previousSource = sourceNanos
        previousReceipt = receivedAtNanos
        both { sourceToReceipt.add(receivedAtNanos - sourceNanos) }
        if (publication == null) {
            both { receivesWithoutPublicationMetadata++ }
            previousReceivedPublication = null
            previousReceivedEnabled = null
        } else {
            observePublished(publication)
            both {
                sourceToPublication.add(publication.publishedAtMonotonicTimeNanos - sourceNanos)
                publicationToReceipt.add(receivedAtNanos - publication.publishedAtMonotonicTimeNanos)
            }
            previousReceivedPublication?.let { previous ->
                if (publication.streamId == latestPublication?.streamId &&
                    previous.streamId == publication.streamId && publication.sequenceNumber > previous.sequenceNumber) {
                    val advance = publication.sequenceNumber - previous.sequenceNumber
                    val skipped = advance - 1L
                    both {
                        when {
                            previousReceivedEnabled == true && enabled -> {
                                enabledReceiveSequenceAdvances += advance
                                enabledReceiveSequenceSkipped += skipped
                            }
                            previousReceivedEnabled == false && !enabled -> disabledReceiveSequenceSkipped += skipped
                            else -> transitionOrUnknownSequenceSkipped += skipped
                        }
                    }
                }
            }
            val previous = previousReceivedPublication
            if (previous == null || publication.streamId > previous.streamId ||
                (publication.streamId == previous.streamId && publication.sequenceNumber > previous.sequenceNumber)) {
                previousReceivedPublication = publication
                previousReceivedEnabled = enabled
            } else if (previousReceivedEnabled != enabled) {
                // An old/duplicate frame cannot rewind the receive high-water mark;
                // an interpretation transition nevertheless makes its next gap unknown.
                previousReceivedEnabled = null
            }
        }
        // Disabled interpretation is not a missing-bar interval or a failed locator.
        if (!enabled) previousAccepted.clear()
    }

    /** Outcomes are factual locator statuses; unexpected strings share one bounded OTHER bucket. */
    @Synchronized
    fun onLocationCompleted(
        sourceNanos: Long,
        startedAtNanos: Long,
        completedAtNanos: Long,
        outcomes: Map<ActivePokemonSide, String>,
    ) {
        startAt(startedAtNanos)
        both { locationPasses++; locatorDuration.add(completedAtNanos - startedAtNanos) }
        ActivePokemonSide.entries.forEach { side ->
            val reason = outcomes[side]?.takeIf { it in OUTCOMES } ?: "OTHER"
            sides(side) {
                locationAttempts++
                if (reason == "FOUND") found++ else misses[reason] = (misses[reason] ?: 0L) + 1L
            }
            if (reason == "FOUND") {
                val previous = previousAccepted[side]
                if (previous == null || sourceNanos > previous) {
                    previous?.let { sides(side) { acceptedIntervals.add(sourceNanos - it) } }
                    previousAccepted[side] = sourceNanos
                } else {
                    sides(side) { nonIncreasingAcceptedSources++ }
                }
            }
        }
    }

    /** Called before entering Custody, so even synchronous recording finds its matching submission. */
    @Synchronized
    fun onSubmission(side: ActivePokemonSide, sourceNanos: Long, submittedAtNanos: Long) {
        startAt(submittedAtNanos)
        sides(side) { submitted++; sourceToSubmission.add(submittedAtNanos - sourceNanos) }
        val key = Key(side, sourceNanos)
        if (pending.containsKey(key)) {
            sides(side) { duplicateSubmissions++ }
            return // Preserve the original submission time for the first Article.
        }
        if (pending.size >= maxPendingSubmissions) {
            val oldest = pending.entries.iterator()
            val evicted = oldest.next().key
            oldest.remove()
            sides(evicted.side) { pendingEvicted++ }
        }
        pending[key] = submittedAtNanos
    }

    /** Called only after the geometry Article has actually entered the Match Timeline. */
    @Synchronized
    fun onTimelineRecorded(side: ActivePokemonSide, sourceNanos: Long, recordedAtNanos: Long) {
        startAt(recordedAtNanos)
        val submission = pending.remove(Key(side, sourceNanos))
        sides(side) {
            recorded++
            sourceToTimeline.add(recordedAtNanos - sourceNanos)
            if (submission == null) recordedWithoutSubmission++
            else submissionToTimeline.add(recordedAtNanos - submission)
        }
    }

    /** Species/empty-HP boundaries must not turn separate appearances into one cadence interval. */
    @Synchronized
    fun resetContinuity(side: ActivePokemonSide) {
        previousAccepted.remove(side)
        sides(side) { continuityResets++ }
    }

    /**
     * PERIODIC returns null until due; terminal reasons always flush. Reading
     * producer progress does not label undelivered frames as disabled or lost.
     */
    @Synchronized
    fun takeSummary(
        nowNanos: Long,
        latestPublication: VisualFramePublication?,
        reason: HpDiagnosticSummaryReason = HpDiagnosticSummaryReason.PERIODIC,
    ): HpObservationPipelineMeasured? {
        val started = windowStartedAt
        if (reason == HpDiagnosticSummaryReason.PERIODIC &&
            (started == null || nowNanos - started < summaryIntervalNanos)) return null
        startAt(nowNanos)
        latestPublication?.let(::observePublished)
        val summary = HpObservationPipelineMeasured(
            windowStartedAtNanos = requireNotNull(windowStartedAt),
            windowEndedAtNanos = nowNanos,
            reason = reason.name,
            histogramUpperBoundsNanos = HISTOGRAM_UPPER_BOUNDS_NANOS.toList(),
            window = window.snapshot(),
            cumulative = cumulative.snapshot(),
            pendingSubmissions = pending.size,
            oldestPendingAgeNanos = pending.values.minOrNull()?.let { (nowNanos - it).coerceAtLeast(0L) },
            latestPublicationStreamId = this.latestPublication?.streamId,
            latestPublicationSequenceNumber = this.latestPublication?.sequenceNumber,
        )
        window = Statistics()
        windowStartedAt = nowNanos
        return summary
    }

    private fun startAt(at: Long) { if (windowStartedAt == null) windowStartedAt = at }

    private fun observePublished(publication: VisualFramePublication) {
        val previous = latestPublication
        when {
            previous == null -> both { publicationBaselines++ }
            publication.streamId < previous.streamId -> {
                // Provider epochs are monotonically assigned. A buffered old epoch
                // must not become a fresh producer or restart the denominator.
                both { publicationRegressions++ }
                return
            }
            publication.streamId > previous.streamId -> both { publicationRestarts++; publicationBaselines++ }
            publication.sequenceNumber > previous.sequenceNumber ->
                both { publishedSequenceAdvances += publication.sequenceNumber - previous.sequenceNumber }
            publication.sequenceNumber < previous.sequenceNumber -> {
                // Late received frames must not rewind the producer high-water mark.
                both { publicationRegressions++ }
                return
            }
        }
        latestPublication = publication
    }

    private inline fun both(block: Statistics.() -> Unit) { window.block(); cumulative.block() }
    private inline fun sides(side: ActivePokemonSide, block: SideStatistics.() -> Unit) {
        window.sides.getValue(side).block(); cumulative.sides.getValue(side).block()
    }

    private class Distribution {
        private val buckets = LongArray(HISTOGRAM_UPPER_BOUNDS_NANOS.size)
        private var count = 0L
        private var invalid = 0L
        private var minimum: Long? = null
        private var maximum: Long? = null
        private var mean = 0.0
        fun add(value: Long) {
            if (value < 0L) { invalid++; return }
            count++
            minimum = minimum?.coerceAtMost(value) ?: value
            maximum = maximum?.coerceAtLeast(value) ?: value
            mean += (value.toDouble() - mean) / count
            buckets[HISTOGRAM_UPPER_BOUNDS_NANOS.indexOfFirst { value <= it }]++
        }
        fun snapshot() = HpPipelineTimingStatistics(
            sampleCount = count, invalidSampleCount = invalid,
            minimumNanos = minimum, maximumNanos = maximum,
            meanNanos = mean.takeIf { count > 0L },
            p50UpperBoundNanos = percentile(.50), p90UpperBoundNanos = percentile(.90),
            p99UpperBoundNanos = percentile(.99), bucketCounts = buckets.toList(),
        )
        private fun percentile(fraction: Double): Long? {
            if (count == 0L) return null
            val target = ceil(count * fraction).toLong().coerceAtLeast(1L)
            var accumulated = 0L
            buckets.forEachIndexed { index, value ->
                accumulated += value
                if (accumulated >= target) return HISTOGRAM_UPPER_BOUNDS_NANOS[index]
            }
            return null
        }
    }

    private class SideStatistics {
        var locationAttempts = 0L; var found = 0L
        val misses = linkedMapOf<String, Long>()
        var submitted = 0L; var recorded = 0L; var recordedWithoutSubmission = 0L
        var pendingEvicted = 0L; var duplicateSubmissions = 0L
        var continuityResets = 0L; var nonIncreasingAcceptedSources = 0L
        val acceptedIntervals = Distribution(); val sourceToSubmission = Distribution()
        val submissionToTimeline = Distribution(); val sourceToTimeline = Distribution()
        fun snapshot() = HpPipelineSideStatistics(
            locationAttempts, found, misses.toMap(), submitted, recorded,
            recordedWithoutSubmission, pendingEvicted, duplicateSubmissions,
            continuityResets, nonIncreasingAcceptedSources,
            acceptedIntervals.snapshot(), sourceToSubmission.snapshot(),
            submissionToTimeline.snapshot(), sourceToTimeline.snapshot(),
        )
    }

    private class Statistics {
        var publishedSequenceAdvances = 0L; var publicationBaselines = 0L
        var publicationRestarts = 0L; var publicationRegressions = 0L
        var framesReceived = 0L; var enabledFramesReceived = 0L; var disabledFramesReceived = 0L
        var receivesWithoutPublicationMetadata = 0L; var enabledReceiveSequenceAdvances = 0L
        var enabledReceiveSequenceSkipped = 0L; var disabledReceiveSequenceSkipped = 0L
        var transitionOrUnknownSequenceSkipped = 0L; var locationPasses = 0L
        val receivedSourceIntervals = Distribution(); val receiptIntervals = Distribution()
        val sourceToPublication = Distribution(); val publicationToReceipt = Distribution()
        val sourceToReceipt = Distribution(); val locatorDuration = Distribution()
        val sides = ActivePokemonSide.entries.associateWith { SideStatistics() }
        fun snapshot() = HpPipelineStatistics(
            publishedSequenceAdvances, publicationBaselines, publicationRestarts, publicationRegressions,
            framesReceived, enabledFramesReceived, disabledFramesReceived, receivesWithoutPublicationMetadata,
            enabledReceiveSequenceAdvances, enabledReceiveSequenceSkipped, disabledReceiveSequenceSkipped,
            transitionOrUnknownSequenceSkipped, locationPasses,
            receivedSourceIntervals.snapshot(), receiptIntervals.snapshot(),
            sourceToPublication.snapshot(), publicationToReceipt.snapshot(),
            sourceToReceipt.snapshot(), locatorDuration.snapshot(),
            sides.getValue(ActivePokemonSide.PLAYER).snapshot(), sides.getValue(ActivePokemonSide.OPPONENT).snapshot(),
        )
    }

    companion object {
        /** Last bin includes every nonnegative duration, including long stalls. */
        val HISTOGRAM_UPPER_BOUNDS_NANOS: List<Long> = listOf(
            1_000_000L, 4_000_000L, 8_000_000L, 16_000_000L, 33_000_000L,
            50_000_000L, 75_000_000L, 100_000_000L, 150_000_000L, 250_000_000L,
            500_000_000L, 1_000_000_000L, 2_000_000_000L, 5_000_000_000L, Long.MAX_VALUE,
        )
        private val OUTCOMES = setOf(
            "FOUND", "INVALID_CALIBRATION", "OUT_OF_ORDER_FRAME", "NO_CANDIDATE",
            "OBSCURED_CANDIDATES", "UNASSIGNED_CANDIDATES", "RECOVERY_SCAN_THROTTLED",
        )
    }
}
