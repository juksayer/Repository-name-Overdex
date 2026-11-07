package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrWorkScheduler
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
import java.util.concurrent.atomic.AtomicLong

/** Exercises admission, immutable source custody and cancellation without Android/native OCR. */
class PersistedAnnouncementWitnessTest {
    @Test fun `busy shared backend selects the newest crop only after transient admission`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val releaseBackend = CompletableDeferred<Unit>()
        val blocker = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { releaseBackend.await() }
        }
        val reads = CopyOnWriteArrayList<RealityArticle>()
        val reader = object : AnnouncementCropReader {
            override suspend fun <T> withReadingTurn(priority: OcrPriority,
                block: suspend (AnnouncementCropReadingTurn) -> T): T = scheduler.run(priority) {
                block(turn { article -> reads += article; listOf(text("Camerupt used Earth Power!")) })
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            repeat(30) { fixture.capture("queued-$it", fixture.at + it) }
            await { scheduler.waitingCount(OcrPriority.TRANSIENT) == 1 }
            assertTrue(reads.isEmpty())
            val latest = fixture.capture("fresh", fixture.at + 100, wall = 54_321L)
            releaseBackend.complete(Unit)
            blocker.join()
            fixture.awaitAccepted(1)
            assertEquals(listOf(latest.id), reads.map { it.id })
            assertEquals(31, fixture.timeline.getArticles().count { it.payload is CropCaptured })
            val latency = fixture.latencies().single()
            assertTrue(latency.contains("priority=TRANSIENT"))
            assertTrue(latency.contains("replacedPendingTotal=30"))
        } finally {
            releaseBackend.complete(Unit)
            blocker.cancel()
            fixture.close()
        }
    }

    @Test fun `raw and threshold readings share one admitted turn and retain their exact crop provenance`() = runBlocking {
        val raw = text("", treatment = "RAW")
        val threshold = text("Camerupt used Earth Power!", treatment = "THRESHOLD_185_2X")
        lateinit var fixture: Fixture
        val reader = ControlledReader {
            fixture.clock.addAndGet(300_000_000L)
            listOf(raw, threshold)
        }
        fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("old", fixture.at + 9_000_000_000L)
            val request = reader.request()
            fixture.clock.set(fixture.at + 16_000_000_000L)
            val crop = fixture.capture("fresh", fixture.at + 15_500_000_000L, wall = 78_901L)
            request.admit.complete(Unit)
            fixture.awaitAccepted(1)
            assertEquals(1, reader.requests.size)
            assertEquals(OcrPriority.TRANSIENT, request.priority)
            assertEquals(listOf(crop.id), reader.reads.map { it.id })
            val articles = fixture.timeline.getArticles()
            val originals = articles.filter { it.sourceId.id == "ANNOUNCEMENT_WITNESS_OCR" }
            assertEquals(listOf(raw, threshold), originals.map { (it.payload as RawTestimony).ocr })
            assertEquals(listOf("", "Camerupt used Earth Power!"), originals.map { (it.payload as RawTestimony).data })
            val accepted = articles.single { it.sourceId.id == "ANNOUNCEMENT_WITNESS" }
            assertEquals("Camerupt used Earth Power!", (accepted.payload as RawTestimony).data)
            assertEquals(listOf(crop.id.value) + originals.map { it.id.value }, accepted.evidenceReferences)
            originals.forEach { assertEquals(listOf(crop.id.value), it.evidenceReferences) }
            (originals + accepted + articles.single { it.sourceId.id == "ANNOUNCEMENT_WITNESS_LATENCY" }).forEach {
                assertEquals(crop.perceivedAt, it.perceivedAt)
                assertEquals(crop.monotonicTimeNanos, it.monotonicTimeNanos)
            }
            val latency = fixture.latencies().single()
            assertTrue(latency.contains("queueMs=6000"))
            assertTrue(latency.contains("captureToAdmissionMs=500"))
            assertTrue(latency.contains("nativeReadMs=200"))
            assertTrue(latency.contains("passes=2"))
            assertTrue(latency.contains("processingMs=300"))
            assertTrue(latency.contains("totalMs=800"))
        } finally { fixture.close() }
    }

    @Test fun `new crop captured during a native read becomes the next admitted read`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val reader = ControlledReader { article ->
            if (article.label() == "first") { firstStarted.complete(Unit); finishFirst.await() }
            listOf(text(article.label()))
        }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            val first = fixture.capture("first", fixture.at)
            reader.request().admit.complete(Unit)
            firstStarted.await()
            fixture.capture("replaced", fixture.at + 1)
            val last = fixture.capture("last", fixture.at + 2)
            finishFirst.complete(Unit)
            reader.request(index = 1).admit.complete(Unit)
            fixture.awaitAccepted(2)
            assertEquals(listOf(first.id, last.id), reader.reads.map { it.id })
            assertEquals(listOf("first", "last"), fixture.accepted().map { (it.payload as RawTestimony).data })
        } finally { finishFirst.complete(Unit); fixture.close() }
    }

    @Test fun `filtered raw text remains evidence without being promoted to an accepted announcement`() = runBlocking {
        val reading = OcrTextEvidence("RAW", "background words", "", emptyList(), 1080, null)
        val reader = ControlledReader { listOf(reading) }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            val crop = fixture.capture("filtered", fixture.at)
            reader.request().admit.complete(Unit)
            fixture.awaitAccepted(1)
            val raw = fixture.timeline.getArticles().single { it.sourceId.id == "ANNOUNCEMENT_WITNESS_OCR" }
            assertEquals(RawTestimony("background words", reading), raw.payload)
            assertEquals(listOf(crop.id.value), raw.evidenceReferences)
            val accepted = fixture.accepted().single()
            assertEquals(RawTestimony(""), accepted.payload)
            assertEquals(listOf(crop.id.value, raw.id.value), accepted.evidenceReferences)
        } finally { fixture.close() }
    }

    @Test fun `nonincreasing source times are ignored for scheduling but kept in the timeline`() = runBlocking {
        val reader = ControlledReader { listOf(text(it.label())) }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("first", fixture.at)
            val firstTurn = reader.request()
            fixture.capture("equal", fixture.at)
            fixture.capture("older", fixture.at - 1)
            firstTurn.admit.complete(Unit)
            fixture.awaitAccepted(1)
            fixture.capture("new", fixture.at + 1)
            reader.request(index = 1).admit.complete(Unit)
            fixture.awaitAccepted(2)
            assertEquals(listOf("first", "new"), reader.reads.map { it.label() })
            assertEquals(4, fixture.timeline.getArticles().count { it.payload is CropCaptured })
        } finally { fixture.close() }
    }

    @Test fun `a failed native read does not terminate later announcement reads`() = runBlocking {
        val reader = ControlledReader {
            if (it.label() == "bad") error("simulated native read failure")
            listOf(text("Camerupt used Earth Power!"))
        }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("bad", fixture.at)
            val bad = reader.request()
            bad.admit.complete(Unit)
            bad.finished.await()
            await { fixture.latencies().size == 1 }
            fixture.capture("good", fixture.at + 1)
            reader.request(index = 1).admit.complete(Unit)
            fixture.awaitAccepted(1)
            assertEquals(listOf("bad", "good"), reader.reads.map { it.label() })
            assertEquals(2, fixture.latencies().size)
            assertEquals(1, fixture.timeline.getArticles().count { it.sourceId.id == "ANNOUNCEMENT_WITNESS_OCR" })
        } finally { fixture.close() }
    }

    @Test fun `stop during an uncancellable native read refuses late text and latency publication`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            withContext(NonCancellable) {
                started.complete(Unit)
                completed.await()
                listOf(text("Camerupt used Earth Power!"))
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("late", fixture.at)
            val request = reader.request()
            request.admit.complete(Unit)
            started.await()
            fixture.witness.stop()
            completed.complete(Unit)
            request.finished.await()
            fixture.match.awaitAcceptedEvidencePublished()
            assertTrue(fixture.timeline.getArticles().none { it.sourceId.id.startsWith("ANNOUNCEMENT_WITNESS") })
            assertTrue(request.cancelled)
        } finally { completed.complete(Unit); fixture.close() }
    }

    @Test fun `stop while waiting for admission selects no crop`() = runBlocking {
        val reader = ControlledReader { listOf(text("late")) }
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("waiting", fixture.at)
            val request = reader.request()
            fixture.witness.stop()
            request.admit.complete(Unit)
            request.finished.await()
            assertTrue(reader.reads.isEmpty())
            assertTrue(fixture.accepted().isEmpty())
            assertTrue(fixture.latencies().isEmpty())
        } finally { fixture.close() }
    }

    private class Fixture(reader: AnnouncementCropReader) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("announcement-reader-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val at = System.nanoTime()
        val clock = AtomicLong(at + 10_000_000_000L)
        val witness = PersistedAnnouncementWitness(reader, clock::get, dispatcher = Dispatchers.Unconfined)
        fun start() = witness.start(match)
        suspend fun capture(label: String, sourceTime: Long, wall: Long = 12_345L): RealityArticle {
            val record = custody.submitTestimony(SourceId("ANNOUNCEMENT_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
                BattleCropProvenance(BattleCropContracts.announcement.cropName, 1080, 2400,
                    BattleCropBounds(100, 400, 900, 600))), wall, null, emptyList(), sourceTime)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }
        fun accepted() = timeline.getArticles().filter { it.sourceId.id == "ANNOUNCEMENT_WITNESS" }
        fun latencies() = timeline.getArticles().filter { it.sourceId.id == "ANNOUNCEMENT_WITNESS_LATENCY" }
            .map { (it.payload as RawTestimony).data.toString() }
        suspend fun awaitAccepted(count: Int) { await { accepted().size == count }; match.awaitAcceptedEvidencePublished() }
        fun close() { witness.stop(); match.release() }
    }

    private class ControlledReader(private val read: suspend (RealityArticle) -> List<OcrTextEvidence>) : AnnouncementCropReader {
        class Request(val priority: OcrPriority) {
            val admit = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            @Volatile var cancelled = false
        }
        val requests = CopyOnWriteArrayList<Request>()
        val reads = CopyOnWriteArrayList<RealityArticle>()
        override suspend fun <T> withReadingTurn(priority: OcrPriority,
            block: suspend (AnnouncementCropReadingTurn) -> T): T {
            val request = Request(priority)
            requests += request
            return try {
                request.admit.await()
                block(turn { article -> reads += article; read(article) })
            } finally {
                request.cancelled = !currentCoroutineContext().isActive
                request.finished.complete(Unit)
            }
        }
        suspend fun request(index: Int = 0): Request { await { requests.size > index }; return requests[index] }
    }

    companion object {
        private fun RealityArticle.label() = (payload as CropCaptured).artifact.relativePath
            .substringAfterLast('/').removeSuffix(".png")
        private fun text(value: String, treatment: String = "RAW") =
            OcrTextEvidence(treatment, value, value, emptyList(), 1080, null)
        private fun turn(read: suspend (RealityArticle) -> List<OcrTextEvidence>) = object : AnnouncementCropReadingTurn {
            override var nativeReadNanos = 0L
            override var readCount = 0
            override suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile): List<OcrTextEvidence> {
                return try { read(article).also { readCount += it.size; nativeReadNanos += it.size * 100_000_000L } }
                catch (error: Exception) { readCount++; nativeReadNanos += 100_000_000L; throw error }
            }
        }
        private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
            while (!condition()) delay(5)
        }
    }
}
