package com.example.overdex.diagnostics

import com.example.overdex.battle.archive.ArchivedHpObservationPipelineMeasured
import com.example.overdex.battle.custody.HpObservationPipelineMeasured
import com.example.overdex.battle.custody.HpPipelineSideStatistics
import com.example.overdex.battle.custody.HpPipelineStatistics
import com.example.overdex.battle.custody.HpPipelineTimingStatistics
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HpObservationDiagnosticTextTest {
    @Test fun `missing metadata and absent timing samples remain unknown in concise logs`() {
        val measurement = measurement(HpPipelineStatistics(framesReceived = 3,
            receivesWithoutPublicationMetadata = 3))
        val article = RealityArticle(ArticleId("pipeline"), 100L, 200L,
            SourceId("HP_OBSERVATION_PIPELINE"), measurement)
        val original = article.copy()
        val text = requireNotNull(BattleRecordDiagnostics.format(article))
        assertTrue(text.contains("signal=HpObservationPipelineMeasured"))
        assertTrue(text.contains("observed publication advance=unknown"))
        assertTrue(text.contains("receipt unknown"))
        assertFalse(text.contains("receipt <=0"))
        assertEquals(original, article)
        assertEquals(HpObservationDiagnosticText.summary(measurement),
            HpObservationDiagnosticText.summary(archived(measurement)))
    }

    @Test fun `later window inherits known publication baseline from cumulative statistics`() {
        val measurement = measurement(HpPipelineStatistics(publishedSequenceAdvances = 106L,
            publicationBaselines = 0L)).copy(cumulative = HpPipelineStatistics(
                publishedSequenceAdvances = 124L, publicationBaselines = 1L))
        val archived = archived(measurement)
        assertTrue(HpObservationDiagnosticText.summary(measurement).contains("observed publication advance=106"))
        assertTrue(HpObservationDiagnosticText.summary(archived).contains("observed publication advance=106"))
        val details = HpObservationDiagnosticText.details(archived)
        assertTrue(details.contains("WINDOW\nObserved publication advance: 106; baselines: 0"))
        assertTrue(details.contains("CUMULATIVE\nObserved publication advance: 124; baselines: 1"))
    }

    @Test fun `known stream preserves zero publication advance without a new window baseline`() {
        val measurement = measurement(HpPipelineStatistics()).copy(
            latestPublicationStreamId = 7L, latestPublicationSequenceNumber = 11L)
        assertTrue(HpObservationDiagnosticText.summary(measurement).contains("observed publication advance=0"))
        val details = HpObservationDiagnosticText.details(archived(measurement))
        assertTrue(details.contains("WINDOW\nObserved publication advance: 0; baselines: 0"))
        assertTrue(details.contains("Producer stream/sequence: 7/11"))
    }

    @Test fun `research details separate window cumulative disabled intervals and factual misses`() {
        val timing = HpPipelineTimingStatistics(sampleCount = 2, minimumNanos = 1_500_000L,
            maximumNanos = 6_000_000_000L, meanNanos = 3_000_750_000.0,
            p50UpperBoundNanos = 4_000_000L, p90UpperBoundNanos = Long.MAX_VALUE,
            p99UpperBoundNanos = Long.MAX_VALUE, bucketCounts = listOf(1L, 0L, 1L))
        val side = HpPipelineSideStatistics(locationAttempts = 3L, found = 2L,
            missesByReason = mapOf("RECOVERY_SCAN_THROTTLED" to 1L), submitted = 2L,
            recorded = 1L, sourceToTimeline = timing)
        val counts = HpPipelineStatistics(publishedSequenceAdvances = 9L,
            publicationBaselines = 1L, framesReceived = 5L, enabledFramesReceived = 3L,
            disabledFramesReceived = 2L, enabledReceiveSequenceAdvances = 4L,
            enabledReceiveSequenceSkipped = 1L, disabledReceiveSequenceSkipped = 2L,
            transitionOrUnknownSequenceSkipped = 3L, player = side)
        val measurement = measurement(counts).copy(cumulative = counts.copy(framesReceived = 15L))
        val text = HpObservationDiagnosticText.details(archived(measurement))
        assertTrue(text.contains("WINDOW\n"))
        assertTrue(text.contains("CUMULATIVE\n"))
        assertTrue(text.contains("Received: 5; enabled: 3; disabled: 2"))
        assertTrue(text.contains("Received: 15; enabled: 3; disabled: 2"))
        assertTrue(text.contains("disabled skips: 2; transition/unknown skips: 3"))
        assertTrue(text.contains("RECOVERY_SCAN_THROTTLED=1"))
        assertTrue(text.contains("p50 <=4.000 ms"))
        assertTrue(text.contains("p90 >5000.000 ms (overflow)"))
        assertTrue(text.contains("Histogram: <=4.000 ms: 1, >5000.000 ms (overflow): 1"))
        assertTrue(text.contains("not exact percentile estimates"))
        assertTrue(text.contains("point-in-time snapshots"))
        assertTrue(text.contains("not attacks"))
    }

    @Test fun `pending custody age and unknown producer are visible without fake zero latency`() {
        val measurement = measurement(HpPipelineStatistics()).copy(pendingSubmissions = 2,
            oldestPendingAgeNanos = 15_500_000L)
        val text = HpObservationDiagnosticText.details(archived(measurement))
        assertTrue(text.contains("Producer stream/sequence: unknown/unknown"))
        assertTrue(text.contains("Pending submissions: 2; oldest age: 15.500 ms"))
        assertTrue(text.contains("samples=0; invalid=0; min=unknown; mean=unknown; max=unknown"))
        assertTrue(text.contains("Recorded without submission=0"))
    }

    private fun measurement(counts: HpPipelineStatistics) = HpObservationPipelineMeasured(
        windowStartedAtNanos = 1_000_000_000L, windowEndedAtNanos = 6_000_000_000L,
        reason = "PERIODIC", histogramUpperBoundsNanos = listOf(4_000_000L, 5_000_000_000L, Long.MAX_VALUE),
        window = counts, cumulative = counts, pendingSubmissions = 0,
        oldestPendingAgeNanos = null, latestPublicationStreamId = null,
        latestPublicationSequenceNumber = null,
    )
    private fun archived(value: HpObservationPipelineMeasured) = ArchivedHpObservationPipelineMeasured(
        value.windowStartedAtNanos, value.windowEndedAtNanos, value.reason,
        value.histogramUpperBoundsNanos, value.window, value.cumulative,
        value.pendingSubmissions, value.oldestPendingAgeNanos,
        value.latestPublicationStreamId, value.latestPublicationSequenceNumber,
    )
}
