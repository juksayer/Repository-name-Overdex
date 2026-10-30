package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrWorkScheduler
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Exercises the real crop -> custody -> text -> phrase -> Match end path without native OCR. */
class PersistedOutcomeTextWitnessTest {
    @Test fun `routine recovery backlog cannot hide fresh win and its source provenance survives to Match end`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val backendBusy = CompletableDeferred<Unit>()
        val releaseBackend = CompletableDeferred<Unit>()
        val blocker = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) {
                backendBusy.complete(Unit)
                releaseBackend.await()
            }
        }
        backendBusy.await()
        val reads = CopyOnWriteArrayList<RealityArticle>()
        val reader = object : OutcomeCropReader {
            override suspend fun warmUp() = Unit
            override suspend fun <T> withReadingTurn(priority: OcrPriority,
                block: suspend (OutcomeCropReadingTurn) -> T): T = scheduler.run(priority) {
                block(turn { article ->
                    reads += article
                    if (article.label() == "brief-win") listOf(text("YOU WIN!")) else emptyList()
                })
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 20_000_000_000L)
            fixture.start()
            repeat(30) { fixture.capture("old-$it", fixture.at + it * 1_000_000L) }
            await { scheduler.waitingCount(OcrPriority.NORMAL) == 1 }
            val win = fixture.capture("brief-win", fixture.clock.get() - 200_000_000L, wall = 54_321L)
            await { scheduler.waitingCount(OcrPriority.URGENT) == 1 }
            releaseBackend.complete(Unit)
            blocker.join()
            fixture.awaitEnd()

            // A waiting routine turn may be admitted as the winning read releases
            // the shared backend; it must never be ahead of that live result.
            assertEquals("brief-win", reads.first().label())
            val articles = fixture.timeline.getArticles()
            val raw = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            val phrase = articles.single { it.sourceId.id == "YOU_WIN_WITNESS" }
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(RawTestimony("YOU WIN!", text("YOU WIN!")), raw.payload)
            assertEquals(listOf(win.id.value), raw.evidenceReferences)
            assertEquals(listOf(raw.id.value), phrase.evidenceReferences)
            assertEquals(listOf(phrase.id), end.predecessorIds)
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            listOf(raw, phrase, end).forEach {
                assertEquals(win.perceivedAt, it.perceivedAt)
                assertEquals(win.monotonicTimeNanos, it.monotonicTimeNanos)
            }
            assertEquals(31, articles.count { it.payload is CropCaptured })
            val latency = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" &&
                (it.payload as RawTestimony).data.toString().contains("cropId=${win.id.value} ") }
            assertTrue((latency.payload as RawTestimony).data.toString().contains("lane=RECENT priority=URGENT"))
        } finally {
            releaseBackend.complete(Unit)
            blocker.cancel()
            fixture.close()
        }
    }

    @Test fun `delayed admission expires old frame and selects brief win before a newer blank`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "brief-win") listOf(text("YOU WIN!")) else emptyList()
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("expired-while-waiting", fixture.clock.get() - 1_000_000_000L)
            val waitingTurn = reader.request(OcrPriority.URGENT)
            // Nothing has selected or loaded this frame while the backend is busy.
            assertTrue(reader.reads.isEmpty())
            fixture.clock.addAndGet(4_000_000_000L)
            val win = fixture.capture("brief-win", fixture.clock.get() - 500_000_000L)
            fixture.capture("newer-blank", fixture.clock.get() - 100_000_000L)
            waitingTurn.admit.complete(Unit)
            fixture.awaitEnd()

            assertEquals(listOf(win.id), reader.reads.map { it.id })
            assertEquals(3, fixture.timeline.getArticles().count { it.payload is CropCaptured })
            assertEquals(1, fixture.timeline.getArticles().count { it.payload is MatchEnded })
        } finally { fixture.close() }
    }

    @Test fun `one failed native read does not terminate later result reads`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "bad-frame") error("simulated native read failure")
            listOf(text("YOU WIN!"))
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("bad-frame", fixture.clock.get() - 500_000_000L)
            val badTurn = reader.request(OcrPriority.URGENT)
            fixture.capture("good-frame", fixture.clock.get() - 100_000_000L)
            badTurn.admit.complete(Unit)
            val nextTurn = reader.request(OcrPriority.URGENT, index = 1)
            nextTurn.admit.complete(Unit)
            fixture.awaitEnd()

            assertEquals(listOf("bad-frame", "good-frame"), reader.reads.map { it.label() })
            assertEquals(2, fixture.timeline.getArticles().count {
                it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY"
            })
            assertEquals(1, fixture.timeline.getArticles().count { it.sourceId.id == "YOU_WIN_WITNESS" })
        } finally { fixture.close() }
    }

    @Test fun `stopping during native read refuses its late text and latency publication`() = runBlocking {
        val nativeStarted = CompletableDeferred<Unit>()
        val nativeCompleted = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            // Models a native callback that finishes despite coroutine cancellation.
            withContext(NonCancellable) {
                nativeStarted.complete(Unit)
                nativeCompleted.await()
                listOf(text("YOU WIN!"))
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("late-native-result", fixture.clock.get() - 100_000_000L)
            val waitingTurn = reader.request(OcrPriority.URGENT)
            waitingTurn.admit.complete(Unit)
            withTimeout(5_000) { nativeStarted.await() }
            fixture.witness.stop()
            nativeCompleted.complete(Unit)
            withTimeout(5_000) { waitingTurn.finished.await() }
            fixture.match.awaitAcceptedEvidencePublished()

            assertEquals(1, fixture.timeline.getArticles().count { it.payload is CropCaptured })
            assertTrue(fixture.timeline.getArticles().none {
                it.sourceId.id.startsWith("MATCH_OUTCOME_TEXT_WITNESS") ||
                    it.sourceId.id == "YOU_WIN_WITNESS" || it.payload is MatchEnded
            })
        } finally {
            nativeCompleted.complete(Unit)
            fixture.close()
        }
    }

    @Test fun `decisive result cancels both workers and collector so later crops request no backend turns`() = runBlocking {
        val reader = ControlledReader { listOf(text("YOU WIN!")) }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("recovery-pending", fixture.at)
            val recovery = reader.request(OcrPriority.NORMAL)
            fixture.capture("fresh-win", fixture.clock.get() - 100_000_000L)
            val recent = reader.request(OcrPriority.URGENT)
            recent.admit.complete(Unit)
            fixture.awaitEnd()
            withTimeout(5_000) { recovery.finished.await(); recent.finished.await() }
            assertTrue("Waiting recovery turn must be cancelled", recovery.cancelled)
            assertEquals(0, reader.activeTurns.get())
            val requestsAfterWin = reader.requests.size

            repeat(20) { index ->
                fixture.capture("after-end-$index", if (index % 2 == 0) fixture.at else fixture.clock.get())
            }
            fixture.match.awaitAcceptedEvidencePublished()
            // Give a leaked SharedFlow collector and its workers a chance to react.
            delay(100)
            assertEquals(requestsAfterWin, reader.requests.size)
            assertEquals(listOf("fresh-win"), reader.reads.map { it.label() })
            assertEquals(22, fixture.timeline.getArticles().count { it.payload is CropCaptured })
        } finally { fixture.close() }
    }

    private class Fixture(reader: OutcomeCropReader) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("outcome-reader-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val at = System.nanoTime()
        val clock = AtomicLong(at)
        // Run collector continuations inline so source publication is also an
        // enqueue barrier; admission permits, rather than sleeps, control reads.
        val witness = PersistedOutcomeTextWitness(reader, clock::get, dispatcher = Dispatchers.Unconfined)
        private val phrase = PersistedOutcomePhraseWitness(
            { MatchOutcomeTextResolver.resolve(it) == MatchOutcomePhrase.WIN },
            ObserverId("YOU_WIN_WITNESS", ObservationSource.SCREEN_CAPTURE), "You Win")

        fun start() { phrase.start(match); witness.start(match) }

        suspend fun capture(label: String, sourceTime: Long, wall: Long = 12_345L): RealityArticle {
            // Fake readers use the reference label, and never load a Bitmap or touch ML Kit.
            val record = custody.submitTestimony(SourceId("MATCH_OUTCOME_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
                BattleCropProvenance(BattleCropContracts.matchOutcome.cropName, 1080, 2400,
                    BattleCropBounds(100, 400, 900, 600))), wall, null, emptyList(), sourceTime)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }

        suspend fun awaitEnd() {
            await { timeline.getArticles().any { it.payload is MatchEnded } }
            match.awaitAcceptedEvidencePublished()
        }

        fun close() { witness.stop(); phrase.stop(); match.release() }
    }

    private class ControlledReader(private val read: suspend (RealityArticle) -> List<OcrTextEvidence>) : OutcomeCropReader {
        class Request(val priority: OcrPriority) {
            val admit = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            @Volatile var cancelled = false
        }
        val requests = CopyOnWriteArrayList<Request>()
        val reads = CopyOnWriteArrayList<RealityArticle>()
        val activeTurns = AtomicInteger()
        override suspend fun warmUp() = Unit
        override suspend fun <T> withReadingTurn(priority: OcrPriority,
            block: suspend (OutcomeCropReadingTurn) -> T): T {
            val request = Request(priority)
            activeTurns.incrementAndGet()
            requests += request
            return try {
                request.admit.await()
                block(turn { article -> reads += article; read(article) })
            } finally {
                request.cancelled = !currentCoroutineContext().isActive
                activeTurns.decrementAndGet()
                request.finished.complete(Unit)
            }
        }

        suspend fun request(priority: OcrPriority, index: Int = 0): Request {
            await { requests.count { it.priority == priority } > index }
            return requests.filter { it.priority == priority }[index]
        }
    }

    companion object {
        private fun RealityArticle.label() = (payload as CropCaptured).artifact.relativePath
            .substringAfterLast('/').removeSuffix(".png")

        private fun text(value: String) = OcrTextEvidence("ORIGINAL", value, value, emptyList(), 1080, null)

        private fun turn(read: suspend (RealityArticle) -> List<OcrTextEvidence>) = object : OutcomeCropReadingTurn {
            override var nativeReadNanos: Long = 0
            override var readCount: Int = 0
            override suspend fun recognize(article: RealityArticle): List<OcrTextEvidence> {
                readCount++
                nativeReadNanos += 100_000_000L
                return read(article)
            }
        }

        private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
            while (!condition()) delay(5)
        }
    }
}
