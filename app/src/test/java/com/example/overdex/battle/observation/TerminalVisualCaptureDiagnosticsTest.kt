package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.model.observation.VisualFramePublication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TerminalVisualCaptureDiagnosticsTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val window = TerminalVisualCaptureWindow(1L, opponent, TerminalVisualTrigger.CHARGED_IMPACT, 100L, 1_000L)
    private val diagnostics = TerminalVisualCaptureDiagnostics()

    private fun receive(at: Long, admitted: Boolean = true, stale: Boolean = false,
        publication: VisualFramePublication? = null, wall: Long = 2_000L) {
        diagnostics.received(window, at, wall, publication, admitted, stale)
    }

    private fun ticket(at: Long) = TerminalVisualCaptureTicket(window.id, window.side, at)

    private fun finish(at: Long, retained: Boolean = true, outcome: String = if (retained) "PRESERVED" else "OBSCURED") {
        diagnostics.finished(ticket(at), outcome, 80L, 10L, 60L, 30L, 128L, 2_100L, retained)
    }

    private fun summary(force: Boolean = false, now: Long = window.until): TerminalVisualCaptureDiagnosticSummary =
        diagnostics.drain(emptySet(), now, force).single()

    private fun fields(summary: TerminalVisualCaptureDiagnosticSummary): Map<String, String> =
        summary.text.split(' ').associate { it.substringBefore('=') to it.substringAfter('=') }

    @Test fun `zero receipt window keeps opening source and measured wall clock`() {
        assertTrue(diagnostics.opened(window, listOf("qte", "qte", ""), wallTime = 1_999L))
        assertTrue(diagnostics.drain(setOf(window.id), 999L).isEmpty())
        val result = summary(now = 90_000L)
        val values = fields(result)
        assertEquals(100L, result.sourceAt)
        assertEquals(1_999L, result.wallTime)
        assertEquals(listOf("qte"), result.evidenceReferences)
        assertEquals("0", values["received"])
        assertEquals("0", values["retainedPngs"])
        assertEquals("0", values["pending"])
        assertFalse(result.text.contains("faint", ignoreCase = true))
        assertFalse(result.text.contains("publishedCrops"))
        assertTrue(diagnostics.drain(emptySet(), 90_001L).isEmpty())
    }

    @Test fun `opening is idempotent and callbacks cannot reopen recently drained window`() {
        diagnostics.opened(window, listOf("original"), 10L)
        diagnostics.opened(window, listOf("replacement"), 20L)
        receive(100L)
        finish(100L)
        val result = summary()
        assertEquals(listOf("original"), result.evidenceReferences)
        assertFalse(diagnostics.opened(window, wallTime = 30L))
        receive(200L)
        finish(200L)
        assertTrue(diagnostics.drain(emptySet(), 10_000L).isEmpty())
    }

    @Test fun `receipt admission completion and retention count distinct measured stages`() {
        diagnostics.opened(window)
        receive(100L, publication = VisualFramePublication(1L, 10L, 100L))
        diagnostics.started(ticket(100L))
        diagnostics.started(ticket(100L))
        finish(100L)
        finish(100L)
        receive(200L, admitted = false, publication = VisualFramePublication(1L, 11L, 200L))
        receive(300L, publication = VisualFramePublication(1L, 15L, 300L))
        diagnostics.started(ticket(300L))
        finish(300L, retained = false)
        val values = fields(summary())
        assertEquals("3", values["received"])
        assertEquals("2", values["admitted"])
        assertEquals("1", values["rejected"])
        assertEquals("2", values["started"])
        assertEquals("2", values["completed"])
        assertEquals("1", values["retainedPngs"])
        assertEquals("128", values["retainedBytes"])
        assertEquals("1", values["outcome_PRESERVED"])
        assertEquals("1", values["outcome_OBSCURED"])
        assertEquals("5", values["sequenceAdvances"])
        assertEquals("3", values["skipped"])
        assertEquals("100", values["maxSourceGapNanos"])
        assertEquals("160", values["elapsedNanos"])
        assertEquals("120", values["preserveNanos"])
        assertEquals("60", values["cpuNanos"])
    }

    @Test fun `closed window cannot drain pending work but force reports partial counters`() {
        diagnostics.opened(window, wallTime = 1_000L)
        receive(200L, wall = 2_000L)
        diagnostics.started(ticket(200L))
        assertTrue(diagnostics.drain(emptySet(), 100_000L).isEmpty())
        val result = summary(force = true, now = 200_000L)
        val values = fields(result)
        assertEquals("true", values["forced"])
        assertEquals("1", values["pending"])
        assertEquals("1", values["inFlight"])
        assertEquals("0", values["completed"])
        assertEquals("false", values["preserveGapComplete"])
        assertEquals(200L, result.sourceAt)
        assertEquals(2_000L, result.wallTime)
        finish(200L)
        assertTrue(diagnostics.drain(emptySet(), 200_001L).isEmpty())
    }

    @Test fun `completed reorder is retired in source order without an inflated preservation gap`() {
        diagnostics.opened(window)
        listOf(100L, 300L, 600L).forEach { receive(it); diagnostics.started(ticket(it)) }
        finish(600L)
        finish(100L)
        assertTrue(diagnostics.drain(emptySet(), 1_001L).isEmpty())
        finish(300L)
        val values = fields(summary())
        assertEquals("3", values["completed"])
        assertEquals("3", values["retainedPngs"])
        assertEquals("300", values["maxPreserveSourceGapNanos"])
        assertEquals("3", values["peakInFlight"])
        assertEquals("0", values["inFlight"])
    }

    @Test fun `failed middle capture is an actual larger preserved source gap`() {
        diagnostics.opened(window)
        listOf(100L, 300L, 600L).forEach { receive(it) }
        finish(100L)
        finish(300L, retained = false)
        finish(600L)
        val values = fields(summary())
        assertEquals("300", values["maxSourceGapNanos"])
        assertEquals("500", values["maxPreserveSourceGapNanos"])
        assertEquals("2", values["retainedPngs"])
        assertEquals("1", values["outcome_OBSCURED"])
    }

    @Test fun `old future and stale sources cannot create work or rebase diagnostic source`() {
        diagnostics.opened(window, wallTime = 1_000L)
        receive(99L, admitted = false)
        receive(1_000L, admitted = false)
        receive(150L, admitted = false, stale = true)
        receive(200L)
        finish(200L)
        receive(199L, admitted = false)
        receive(10_000L, admitted = true)
        val result = summary()
        val values = fields(result)
        assertEquals(200L, result.sourceAt)
        assertEquals("6", values["received"])
        assertEquals("1", values["admitted"])
        assertEquals("5", values["rejected"])
        assertEquals("3", values["outsideWindow"])
        assertEquals("1", values["stale"])
        assertEquals("1", values["sourceReorders"])
        assertEquals("1", values["invalidAdmissions"])
        assertEquals("50", values["maxSourceGapNanos"])
    }

    @Test fun `age crossing after gate admission does not erase actual admitted work`() {
        diagnostics.opened(window)
        receive(100L, admitted = true, stale = true)
        diagnostics.started(ticket(100L))
        finish(100L)
        val values = fields(summary())
        assertEquals("1", values["stale"])
        assertEquals("1", values["admitted"])
        assertEquals("1", values["completed"])
        assertEquals("0", values["rejected"])
    }

    @Test fun `publication gaps never bridge stream changes or unknown receipts`() {
        diagnostics.opened(window)
        val publications = listOf(
            VisualFramePublication(4L, 100L, 0L),
            VisualFramePublication(5L, 1L, 0L),
            VisualFramePublication(4L, 120L, 0L),
            VisualFramePublication(5L, 3L, 0L),
            null,
            VisualFramePublication(5L, 10L, 0L),
            VisualFramePublication(5L, 11L, 0L),
        )
        publications.forEachIndexed { index, publication -> receive(100L + index * 100L, false, publication = publication) }
        val values = fields(summary())
        assertEquals("3", values["sequenceAdvances"])
        assertEquals("1", values["skipped"])
        assertEquals("1", values["unknownPublications"])
        assertEquals("1", values["oldPublications"])
        assertEquals("1", values["streamChanges"])
    }

    @Test fun `negative costs clamp and large accumulated values saturate`() {
        diagnostics.opened(window)
        receive(100L)
        diagnostics.finished(ticket(100L), "PRESERVED", -1L, -1L, -1L, null, -1L, 2_100L)
        receive(200L)
        diagnostics.finished(ticket(200L), "PRESERVED", Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
            Long.MAX_VALUE, Long.MAX_VALUE, 2_200L)
        receive(300L)
        finish(300L)
        val values = fields(summary())
        listOf("elapsedNanos", "copyNanos", "preserveNanos", "cpuNanos", "retainedBytes").forEach {
            assertEquals(it, Long.MAX_VALUE.toString(), values[it])
        }
        assertEquals("1", values["cpuUnavailable"])
    }

    @Test fun `retained bytes survive a later submission failure without claiming publication`() {
        diagnostics.opened(window)
        receive(100L)
        finish(100L, retained = true, outcome = "SUBMISSION_FAILED")
        val result = summary()
        assertEquals("1", fields(result)["retainedPngs"])
        assertEquals("1", fields(result)["outcome_SUBMISSION_FAILED"])
        assertFalse(result.text.contains("published="))
    }

    @Test fun `a ticket from another side or unadmitted source does not finish work`() {
        diagnostics.opened(window)
        receive(100L)
        diagnostics.finished(ticket(100L).copy(side = player), "PRESERVED", 0L, 0L, 0L, null, 0L, 0L)
        finish(101L)
        assertTrue(diagnostics.drain(emptySet(), 1_001L).isEmpty())
        finish(100L)
        assertEquals("1", fields(summary())["completed"])
    }

    @Test fun `capacity refuses new tracking without dropping pending accounting`() {
        (1L..TerminalVisualCaptureDiagnostics.MAX_WINDOWS.toLong()).forEach { id ->
            assertTrue(diagnostics.opened(window.copy(id = id)))
        }
        receive(100L)
        assertFalse(diagnostics.opened(window.copy(id = 100L)))
        val results = diagnostics.drain(emptySet(), 1_001L)
        assertEquals(TerminalVisualCaptureDiagnostics.MAX_WINDOWS - 1, results.size)
        finish(100L)
        assertEquals("1", fields(summary())["completed"])
        assertTrue(diagnostics.opened(window.copy(id = 100L)))
    }

    @Test fun `concurrent completion and expiry produce exactly one complete summary`() {
        diagnostics.opened(window)
        val count = 24
        repeat(count) { receive(100L + it * 10L); diagnostics.started(ticket(100L + it * 10L)) }
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(count) { index -> pool.execute {
                start.await()
                finish(100L + index * 10L)
                done.countDown()
            } }
            assertTrue(diagnostics.drain(emptySet(), 1_001L).isEmpty())
            start.countDown()
            assertTrue(done.await(5L, TimeUnit.SECONDS))
            val values = fields(summary())
            assertEquals(count.toString(), values["completed"])
            assertEquals(count.toString(), values["retainedPngs"])
            assertEquals("10", values["maxPreserveSourceGapNanos"])
            assertEquals("0", values["pending"])
            assertTrue(diagnostics.drain(emptySet(), 1_002L).isEmpty())
        } finally { pool.shutdownNow() }
    }
}
