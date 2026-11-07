package com.example.overdex.diagnostics

import com.example.overdex.battle.archive.ArchivedHpObservationPipelineMeasured
import com.example.overdex.battle.custody.HpObservationPipelineMeasured
import com.example.overdex.battle.custody.HpPipelineSideStatistics
import com.example.overdex.battle.custody.HpPipelineStatistics
import com.example.overdex.battle.custody.HpPipelineTimingStatistics
import java.util.Locale

/** Research/log presentation of supplied counters only; no battle-event interpretation. */
object HpObservationDiagnosticText {
    fun summary(value: HpObservationPipelineMeasured): String = summary(
        value.reason, value.windowEndedAtNanos - value.windowStartedAtNanos,
        value.window, value.pendingSubmissions, value.histogramUpperBoundsNanos,
        value.latestPublicationStreamId != null || value.cumulative.publicationBaselines > 0L,
    )

    fun summary(value: ArchivedHpObservationPipelineMeasured): String = summary(
        value.reason, value.windowEndedAtNanos - value.windowStartedAtNanos,
        value.window, value.pendingSubmissions, value.histogramUpperBoundsNanos,
        value.latestPublicationStreamId != null || value.cumulative.publicationBaselines > 0L,
    )

    private fun summary(
        reason: String, duration: Long, counts: HpPipelineStatistics, pending: Int,
        bounds: List<Long>, knownPublicationBaseline: Boolean,
    ): String =
        "HP PIPELINE [$reason; ${milliseconds(duration)} ms; " +
            "received=${counts.framesReceived} enabled=${counts.enabledFramesReceived} disabled=${counts.disabledFramesReceived}; " +
            "observed publication advance=${publicationAdvance(counts, knownPublicationBaseline)}; " +
            "enabled skips=${counts.enabledReceiveSequenceSkipped}/${counts.enabledReceiveSequenceAdvances}; " +
            "found P/O=${counts.player.found}/${counts.opponent.found}; " +
            "submitted P/O=${counts.player.submitted}/${counts.opponent.submitted}; " +
            "recorded P/O=${counts.player.recorded}/${counts.opponent.recorded}; pending=$pending; " +
            "p90 upper bounds: receipt ${bucketBound(counts.sourceToReceipt.p90UpperBoundNanos, bounds)}, " +
            "locator ${bucketBound(counts.locatorDuration.p90UpperBoundNanos, bounds)}, " +
            "Timeline P/O ${bucketBound(counts.player.sourceToTimeline.p90UpperBoundNanos, bounds)}/${bucketBound(counts.opponent.sourceToTimeline.p90UpperBoundNanos, bounds)}]"

    fun details(value: ArchivedHpObservationPipelineMeasured): String = buildString {
        val knownPublicationBaseline = value.latestPublicationStreamId != null || value.cumulative.publicationBaselines > 0L
        appendLine(summary(value))
        appendLine("Window source clock: ${value.windowStartedAtNanos}–${value.windowEndedAtNanos} ns")
        appendLine("Producer stream/sequence: ${value.latestPublicationStreamId ?: "unknown"}/${value.latestPublicationSequenceNumber ?: "unknown"}")
        appendLine("Pending submissions: ${value.pendingSubmissions}; oldest age: ${duration(value.oldestPendingAgeNanos)}")
        appendLine("Counts describe pipeline work, not attacks. Stage counts can cross window boundaries.")
        appendLine("Enabled skips bracket two enabled receive endpoints; disabled and transition intervals are separate.")
        appendLine("Terminal reports are point-in-time snapshots; in-flight reader or Custody work can finish later.")
        appendLine("Timing percentiles are inclusive histogram bucket bounds, not exact percentile estimates.")
        appendLine()
        appendStatistics("WINDOW", value.window, value.histogramUpperBoundsNanos, knownPublicationBaseline)
        appendLine()
        appendStatistics("CUMULATIVE", value.cumulative, value.histogramUpperBoundsNanos, knownPublicationBaseline)
    }.trimEnd()

    private fun StringBuilder.appendStatistics(
        title: String, counts: HpPipelineStatistics, bounds: List<Long>, knownPublicationBaseline: Boolean,
    ) {
        appendLine(title)
        appendLine("Observed publication advance: ${publicationAdvance(counts, knownPublicationBaseline)}; baselines: ${counts.publicationBaselines}; restarts: ${counts.publicationRestarts}; stale progress: ${counts.publicationRegressions}")
        appendLine("Received: ${counts.framesReceived}; enabled: ${counts.enabledFramesReceived}; disabled: ${counts.disabledFramesReceived}; unknown publication metadata: ${counts.receivesWithoutPublicationMetadata}")
        appendLine("Enabled receive sequence advances: ${counts.enabledReceiveSequenceAdvances}; skipped: ${counts.enabledReceiveSequenceSkipped}; disabled skips: ${counts.disabledReceiveSequenceSkipped}; transition/unknown skips: ${counts.transitionOrUnknownSequenceSkipped}")
        appendLine("Locator passes: ${counts.locationPasses}")
        appendTiming("Received source intervals", counts.receivedSourceIntervals, bounds)
        appendTiming("Receipt intervals", counts.receiptIntervals, bounds)
        appendTiming("Source → publication", counts.sourceToPublication, bounds)
        appendTiming("Publication → receipt", counts.publicationToReceipt, bounds)
        appendTiming("Source → receipt", counts.sourceToReceipt, bounds)
        appendTiming("Locator duration", counts.locatorDuration, bounds)
        appendSide("PLAYER", counts.player, bounds)
        appendSide("OPPONENT", counts.opponent, bounds)
    }

    private fun StringBuilder.appendSide(side: String, counts: HpPipelineSideStatistics, bounds: List<Long>) {
        appendLine("$side: locator attempts=${counts.locationAttempts}; found=${counts.found}; submitted=${counts.submitted}; recorded=${counts.recorded}")
        appendLine("  Miss reasons: ${counts.missesByReason.entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "none reported" }}")
        appendLine("  Recorded without submission=${counts.recordedWithoutSubmission}; pending evicted=${counts.pendingEvicted}; duplicate submissions=${counts.duplicateSubmissions}; continuity resets=${counts.continuityResets}; non-increasing accepted sources=${counts.nonIncreasingAcceptedSources}")
        appendTiming("  Accepted source intervals", counts.acceptedSourceIntervals, bounds)
        appendTiming("  Source → submission", counts.sourceToSubmission, bounds)
        appendTiming("  Submission → Timeline", counts.submissionToTimeline, bounds)
        appendTiming("  Source → Timeline", counts.sourceToTimeline, bounds)
    }

    private fun StringBuilder.appendTiming(label: String, timing: HpPipelineTimingStatistics, bounds: List<Long>) {
        appendLine("$label: samples=${timing.sampleCount}; invalid=${timing.invalidSampleCount}; " +
            "min=${duration(timing.minimumNanos)}; mean=${timing.meanNanos?.let { "${decimal(it / 1_000_000.0)} ms" } ?: "unknown"}; max=${duration(timing.maximumNanos)}; " +
            "p50 ${bucketBound(timing.p50UpperBoundNanos, bounds)}; p90 ${bucketBound(timing.p90UpperBoundNanos, bounds)}; p99 ${bucketBound(timing.p99UpperBoundNanos, bounds)}")
        val buckets = timing.bucketCounts.mapIndexedNotNull { index, count ->
            if (count == 0L) null else "${bucketBound(bounds.getOrNull(index), bounds)}: $count"
        }
        if (buckets.isNotEmpty()) appendLine("  Histogram: ${buckets.joinToString()}")
    }

    private fun bucketBound(bound: Long?, bounds: List<Long>): String = when (bound) {
        null -> "unknown"
        Long.MAX_VALUE -> bounds.lastOrNull { it != Long.MAX_VALUE }
            ?.let { ">${milliseconds(it)} ms (overflow)" } ?: "overflow"
        else -> "<=${milliseconds(bound)} ms"
    }
    private fun publicationAdvance(counts: HpPipelineStatistics, knownPublicationBaseline: Boolean): String =
        // A reporting window inherits its producer baseline; only new baselines
        // are counted inside that window. Zero new baselines does not erase it.
        if (!knownPublicationBaseline && counts.publicationBaselines == 0L) "unknown"
        else counts.publishedSequenceAdvances.toString()
    private fun duration(nanos: Long?): String = nanos?.let { "${milliseconds(it)} ms" } ?: "unknown"
    private fun milliseconds(nanos: Long): String = decimal(nanos / 1_000_000.0)
    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
}
