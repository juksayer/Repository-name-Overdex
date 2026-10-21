package com.example.overdex.data.observation

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OcrWorkSchedulerTest {
    @Test fun `obsolete routine work is skipped at admission and releases its slot`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val release = CompletableDeferred<Unit>()
        var fresh = true
        val running = launch(start = CoroutineStart.UNDISPATCHED) { scheduler.run(OcrPriority.NORMAL) { release.await() } }
        val stale = async(start = CoroutineStart.UNDISPATCHED) {
            scheduler.runFresh(OcrPriority.NORMAL, { fresh }) { error("Stale bitmap reached OCR") }
        }
        fresh = false
        release.complete(Unit); running.join()
        assertNull(stale.await())
        assertEquals("current", scheduler.runFresh(OcrPriority.NORMAL, { true }) { "current" })
    }
    @Test fun `transient announcements pass routine labels while all three lanes make progress`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val running = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { release.await() }
        }
        fun queue(label: String, priority: OcrPriority) = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(priority) { order += label }
        }
        val normal = (1..2).map { queue("label$it", OcrPriority.NORMAL) }
        val transient = (1..3).map { queue("announcement$it", OcrPriority.TRANSIENT) }
        val urgent = (1..6).map { queue("species$it", OcrPriority.URGENT) }
        release.complete(Unit)
        running.join(); (normal + transient + urgent).joinAll()
        assertEquals(listOf("species1", "species2", "announcement1", "species3", "species4",
            "announcement2", "species5", "species6", "label1", "announcement3", "label2"), order)
    }

    @Test fun `species and result pass queued routine reads without starving them`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val running = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { release.await(); order += "running" }
        }
        fun queue(name: String, priority: OcrPriority) = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(priority) { order += name }
        }
        val jobs = listOf(queue("label", OcrPriority.NORMAL), queue("effectiveness", OcrPriority.NORMAL),
            queue("species", OcrPriority.URGENT), queue("result", OcrPriority.URGENT),
            queue("species confirmation", OcrPriority.URGENT))
        assertNull(scheduler.runIfIdle { error("Must not overtake waiting work") })
        release.complete(Unit)
        running.join(); jobs.joinAll()
        assertEquals(listOf("running", "species", "result", "label", "species confirmation", "effectiveness"), order)
        assertEquals("idle", scheduler.runIfIdle { "idle" })
    }

    @Test fun `cancelled waiting match cannot read its recycled crop or block the next match`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val release = CompletableDeferred<Unit>()
        val running = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { release.await() }
        }
        val stoppedMatch = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.URGENT) { error("Stopped match must never enter native OCR") }
        }
        val nextMatch = async(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.URGENT) { "Sneasel" }
        }
        stoppedMatch.cancelAndJoin()
        release.complete(Unit)
        running.join()
        assertEquals("Sneasel", withTimeout(1_000) { nextMatch.await() })
    }

    @Test fun `failed read releases the backend for the following frame`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        assertTrue(runCatching { scheduler.run(OcrPriority.URGENT) { error("OCR failed") } }.isFailure)
        assertEquals("next frame", scheduler.run(OcrPriority.NORMAL) { "next frame" })
    }

    @Test fun `native work keeps its slot until completion when match stops`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val release = CompletableDeferred<Unit>()
        var nextStarted = false
        val running = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { withContext(NonCancellable) { release.await() } }
        }
        val next = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.URGENT) { nextStarted = true }
        }
        running.cancel()
        yield()
        assertFalse(nextStarted)
        release.complete(Unit)
        withTimeout(1_000) { joinAll(running, next) }
        assertTrue(nextStarted)
    }
}
