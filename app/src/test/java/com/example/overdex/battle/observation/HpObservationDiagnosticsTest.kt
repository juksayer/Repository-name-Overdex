package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.model.observation.VisualFramePublication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HpObservationDiagnosticsTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT

    @Test fun `explicit baseline reports a completely starved reader and start is idempotent`() {
        val diagnostic = HpObservationDiagnostics(summaryIntervalNanos = 100L)
        diagnostic.start(100L, publication(10L, 90L))
        diagnostic.start(195L, publication(19L, 190L)) // Must not delay the due window.
        val summary = requireNotNull(diagnostic.takeSummary(200L, publication(20L, 199L)))
        assertEquals(100L, summary.windowStartedAtNanos)
        assertEquals(10L, summary.window.publishedSequenceAdvances)
        assertEquals(1L, summary.window.publicationBaselines)
        assertEquals(0L, summary.window.framesReceived)
        assertEquals(0L, summary.window.enabledReceiveSequenceSkipped)
        assertEquals(0L, summary.window.locatorDuration.sampleCount)
    }

    @Test fun `late frames from an older provider epoch never become a fresh producer`() {
        val diagnostic = HpObservationDiagnostics(summaryIntervalNanos = 100L)
        diagnostic.receive(100L, 1L, stream = 7L)
        // receive() delivers at source + 10; its 100 ns reporting window starts at 110.
        assertNull(diagnostic.takeSummary(209L, null))
        val first = requireNotNull(diagnostic.takeSummary(210L, publication(50L, 209L, stream = 8L)))
        assertEquals(8L, first.latestPublicationStreamId)
        diagnostic.receive(210L, 500L, stream = 7L)
        val summary = diagnostic.finish(300L)
        assertEquals(8L, summary.latestPublicationStreamId)
        assertEquals(50L, summary.latestPublicationSequenceNumber)
        assertEquals(1L, summary.cumulative.publicationRestarts)
        assertEquals(2L, summary.cumulative.publicationBaselines)
        assertEquals(1L, summary.cumulative.publicationRegressions)
        assertEquals(0L, summary.cumulative.publishedSequenceAdvances)
        assertEquals(0L, summary.cumulative.enabledReceiveSequenceSkipped)
    }

    @Test fun `out of order received sequence never creates an extra skipped frame`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.receive(100L, 10L)
        diagnostic.receive(200L, 12L)
        diagnostic.receive(210L, 11L)
        diagnostic.receive(300L, 13L)
        val summary = diagnostic.finish(400L)
        assertEquals(1L, summary.cumulative.enabledReceiveSequenceSkipped)
        assertEquals(3L, summary.cumulative.enabledReceiveSequenceAdvances)
    }

    @Test fun `four stages retain source timing and distinguish receipt locator and custody delay`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.onFrameReceived(1_000L, 1_100L, publication(1L, 1_030L), true)
        diagnostic.onLocationCompleted(1_000L, 1_110L, 1_180L, mapOf(player to "FOUND", opponent to "NO_CANDIDATE"))
        diagnostic.onSubmission(player, 1_000L, 1_200L)
        diagnostic.onTimelineRecorded(player, 1_000L, 1_600L)
        val summary = diagnostic.finish(2_000L)
        val counts = summary.window
        assertEquals(1L, counts.framesReceived)
        assertEquals(30L, counts.sourceToPublication.minimumNanos)
        assertEquals(70L, counts.publicationToReceipt.minimumNanos)
        assertEquals(100L, counts.sourceToReceipt.minimumNanos)
        assertEquals(70L, counts.locatorDuration.minimumNanos)
        assertEquals(1L, counts.player.found)
        assertEquals(1L, counts.opponent.missesByReason["NO_CANDIDATE"])
        assertEquals(0L, counts.opponent.submitted)
        assertEquals(200L, counts.player.sourceToSubmission.minimumNanos)
        assertEquals(400L, counts.player.submissionToTimeline.minimumNanos)
        assertEquals(600L, counts.player.sourceToTimeline.minimumNanos)
        assertEquals(0L, counts.player.recordedWithoutSubmission)
        assertEquals(0, summary.pendingSubmissions)
    }

    @Test fun `only same stream enabled receive pairs count skipped interpretation frames`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.receive(100L, 1L, enabled = true)
        diagnostic.receive(200L, 4L, enabled = true)
        diagnostic.receive(300L, 8L, enabled = false) // Unknown point of enable transition.
        diagnostic.receive(400L, 13L, enabled = false)
        diagnostic.receive(500L, 20L, enabled = true)
        val counts = diagnostic.finish(600L).cumulative
        assertEquals(19L, counts.publishedSequenceAdvances)
        assertEquals(3L, counts.enabledReceiveSequenceAdvances)
        assertEquals(2L, counts.enabledReceiveSequenceSkipped)
        assertEquals(4L, counts.disabledReceiveSequenceSkipped)
        assertEquals(9L, counts.transitionOrUnknownSequenceSkipped)
        assertEquals(3L, counts.enabledFramesReceived)
        assertEquals(2L, counts.disabledFramesReceived)
    }

    @Test fun `unknown publication metadata never invents capture counts or sequence skips`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.onFrameReceived(100L, 110L, null, true)
        diagnostic.onFrameReceived(200L, 210L, null, true)
        val summary = diagnostic.finish(300L)
        assertEquals(2L, summary.window.framesReceived)
        assertEquals(2L, summary.window.receivesWithoutPublicationMetadata)
        assertEquals(0L, summary.window.publishedSequenceAdvances)
        assertEquals(0L, summary.window.enabledReceiveSequenceSkipped)
        assertEquals(0L, summary.window.sourceToPublication.sampleCount)
        assertNull(summary.latestPublicationStreamId)
        assertNull(summary.latestPublicationSequenceNumber)
    }

    @Test fun `producer progress includes frames that never reach reader without inventing a drop cause`() {
        val diagnostic = HpObservationDiagnostics(summaryIntervalNanos = 100L)
        diagnostic.receive(100L, 10L)
        val summary = requireNotNull(diagnostic.takeSummary(300L, publication(40L, 295L)))
        assertEquals(30L, summary.window.publishedSequenceAdvances)
        assertEquals(1L, summary.window.framesReceived)
        assertEquals(0L, summary.window.enabledReceiveSequenceSkipped)
        assertEquals(40L, summary.latestPublicationSequenceNumber)
        // A late delivered frame does not rewind the producer's observed progress.
        diagnostic.receive(310L, 30L)
        val later = diagnostic.finish(400L)
        assertEquals(30L, later.cumulative.publishedSequenceAdvances)
        assertEquals(1L, later.cumulative.publicationRegressions)
        assertEquals(40L, later.latestPublicationSequenceNumber)
    }

    @Test fun `stream restart establishes fresh baseline instead of enormous skipped count`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.receive(100L, 9_999L, stream = 7L)
        diagnostic.receive(200L, 1L, stream = 8L)
        diagnostic.receive(300L, 3L, stream = 8L)
        val counts = diagnostic.finish(400L).cumulative
        assertEquals(2L, counts.publicationBaselines)
        assertEquals(1L, counts.publicationRestarts)
        assertEquals(2L, counts.publishedSequenceAdvances)
        assertEquals(1L, counts.enabledReceiveSequenceSkipped)
    }

    @Test fun `a late custody recording is reconciled across reporting windows`() {
        val diagnostic = HpObservationDiagnostics(summaryIntervalNanos = 100L)
        diagnostic.receive(100L, 1L)
        diagnostic.onSubmission(player, 100L, 150L)
        // The first frame is received at 110, so the first periodic report is due at 210.
        assertNull(diagnostic.takeSummary(209L, null))
        val first = requireNotNull(diagnostic.takeSummary(210L, null))
        assertEquals(1L, first.window.player.submitted)
        assertEquals(0L, first.window.player.recorded)
        assertEquals(1, first.pendingSubmissions)
        assertEquals(60L, first.oldestPendingAgeNanos)
        diagnostic.onTimelineRecorded(player, 100L, 240L)
        val second = requireNotNull(diagnostic.takeSummary(310L, null))
        assertEquals(0L, second.window.player.submitted)
        assertEquals(1L, second.window.player.recorded)
        assertEquals(90L, second.window.player.submissionToTimeline.minimumNanos)
        assertEquals(1L, second.cumulative.player.submitted)
        assertEquals(1L, second.cumulative.player.recorded)
        assertEquals(0, second.pendingSubmissions)
        assertEquals(0L, second.cumulative.player.recordedWithoutSubmission)
    }

    @Test fun `match end flush does not hide an in flight custody recording from final stop`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.start(100L, null)
        diagnostic.onSubmission(player, 150L, 200L)
        val end = requireNotNull(diagnostic.takeSummary(250L, null, HpDiagnosticSummaryReason.MATCH_END))
        assertEquals(1, end.pendingSubmissions)
        diagnostic.onTimelineRecorded(player, 150L, 300L)
        val stop = diagnostic.finish(350L)
        assertEquals(0L, stop.window.player.submitted)
        assertEquals(1L, stop.window.player.recorded)
        assertEquals(1L, stop.cumulative.player.submitted)
        assertEquals(1L, stop.cumulative.player.recorded)
        assertEquals(0, stop.pendingSubmissions)
    }

    @Test fun `bounded pending submissions report eviction and unmatched recordings honestly`() {
        val diagnostic = HpObservationDiagnostics(maxPendingSubmissions = 2)
        diagnostic.onSubmission(player, 100L, 110L)
        diagnostic.onSubmission(opponent, 200L, 210L)
        diagnostic.onSubmission(player, 300L, 310L) // Evicts only the oldest key.
        diagnostic.onTimelineRecorded(player, 100L, 400L)
        diagnostic.onTimelineRecorded(opponent, 200L, 410L)
        val summary = diagnostic.finish(500L)
        assertEquals(1L, summary.window.player.pendingEvicted)
        assertEquals(1L, summary.window.player.recordedWithoutSubmission)
        assertEquals(0L, summary.window.player.submissionToTimeline.sampleCount)
        assertEquals(200L, summary.window.opponent.submissionToTimeline.minimumNanos)
        assertEquals(1, summary.pendingSubmissions)
        assertEquals(190L, summary.oldestPendingAgeNanos)
    }

    @Test fun `duplicate submission does not replace its first custody clock`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.onSubmission(player, 100L, 110L)
        diagnostic.onSubmission(player, 100L, 190L)
        diagnostic.onTimelineRecorded(player, 100L, 200L)
        val summary = diagnostic.finish(300L)
        assertEquals(1L, summary.window.player.duplicateSubmissions)
        assertEquals(90L, summary.window.player.submissionToTimeline.minimumNanos)
        assertEquals(0, summary.pendingSubmissions)
    }

    @Test fun `species reset and disabled interpretation break accepted interval continuity`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.found(100L)
        diagnostic.found(200L)
        diagnostic.resetContinuity(player)
        diagnostic.found(10_000L)
        diagnostic.found(10_300L)
        diagnostic.onFrameReceived(20_000L, 20_001L, null, false)
        diagnostic.found(30_000L)
        val summary = diagnostic.finish(40_000L)
        val intervals = summary.window.player.acceptedSourceIntervals
        assertEquals(2L, intervals.sampleCount)
        assertEquals(100L, intervals.minimumNanos)
        assertEquals(300L, intervals.maximumNanos)
        assertEquals(1L, summary.window.player.continuityResets)
        // Opponent was never found; its missing intervals cannot become fake zero samples.
        assertEquals(0L, summary.window.opponent.acceptedSourceIntervals.sampleCount)
    }

    @Test fun `stale geometry does not rewind accepted source continuity`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.found(100L)
        diagnostic.found(90L)
        diagnostic.found(200L)
        val stats = diagnostic.finish(300L).window.player
        assertEquals(1L, stats.nonIncreasingAcceptedSources)
        assertEquals(1L, stats.acceptedSourceIntervals.sampleCount)
        assertEquals(100L, stats.acceptedSourceIntervals.maximumNanos)
    }

    @Test fun `periodic summaries are sparse final flush is explicit and histograms stay fixed size`() {
        val diagnostic = HpObservationDiagnostics(summaryIntervalNanos = 100_000L)
        assertNull(diagnostic.takeSummary(0L, null))
        for (index in 0L until 10_000L) {
            diagnostic.onFrameReceived(index, index + 5L, null, true)
        }
        assertNull(diagnostic.takeSummary(100_004L, null)) // First receipt was at 5; not due yet.
        val summary = requireNotNull(diagnostic.takeSummary(100_005L, null))
        val timing = summary.window.sourceToReceipt
        assertEquals(10_000L, timing.sampleCount)
        assertEquals(10_000L, timing.bucketCounts.sum())
        assertEquals(HpObservationDiagnostics.HISTOGRAM_UPPER_BOUNDS_NANOS.size, timing.bucketCounts.size)
        assertNotNull(timing.p99UpperBoundNanos)
        val final = diagnostic.finish(100_006L)
        assertEquals("SESSION_STOP", final.reason)
        assertEquals(0L, final.window.framesReceived)
        assertEquals(10_000L, final.cumulative.framesReceived)
    }

    @Test fun `negative stage durations are marked invalid not silently clamped to fast timings`() {
        val diagnostic = HpObservationDiagnostics()
        diagnostic.onFrameReceived(200L, 100L, publication(1L, 150L), true)
        diagnostic.onLocationCompleted(200L, 300L, 250L, mapOf(player to "FOUND"))
        val counts = diagnostic.finish(400L).window
        assertEquals(1L, counts.sourceToReceipt.invalidSampleCount)
        assertEquals(0L, counts.sourceToReceipt.sampleCount)
        assertNull(counts.sourceToReceipt.meanNanos)
        assertEquals(1L, counts.publicationToReceipt.invalidSampleCount)
        assertEquals(1L, counts.locatorDuration.invalidSampleCount)
    }

    @Test fun `unknown locator reasons aggregate into one bounded category`() {
        val diagnostic = HpObservationDiagnostics()
        for (index in 0L until 1_000L) {
            diagnostic.onLocationCompleted(index, index + 1L, index + 2L, mapOf(player to "unexpected-$index"))
        }
        val reasons = diagnostic.finish(2_000L).window.player.missesByReason
        assertEquals(mapOf("OTHER" to 1_000L), reasons)
        assertTrue(reasons.size <= 1)
    }

    private fun publication(sequence: Long, at: Long, stream: Long = 1L) =
        VisualFramePublication(stream, sequence, at)
    private fun HpObservationDiagnostics.receive(at: Long, sequence: Long, enabled: Boolean = true, stream: Long = 1L) =
        onFrameReceived(at, at + 10L, publication(sequence, at + 5L, stream), enabled)
    private fun HpObservationDiagnostics.found(at: Long) =
        onLocationCompleted(at, at + 1L, at + 2L, mapOf(player to "FOUND", opponent to "NO_CANDIDATE"))
    private fun HpObservationDiagnostics.finish(at: Long) =
        requireNotNull(takeSummary(at, null, HpDiagnosticSummaryReason.SESSION_STOP))
}
