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
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
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
    @Test fun `readable banner survives a later blank across eight seconds of shared backend contention`() = runBlocking {
        val reader = ControlledReader(read = { article ->
            listOf(text(if (article.label() == "banner") "Camerupt used Rock Tomb!" else ""))
        }, inspect = { article -> if (article.label() == "banner") visual(1) else null })
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 1_000_000_000L)
            fixture.start()
            val banner = fixture.capture("banner", fixture.at + 200_000_000L, wall = 50_123L)
            val waiting = reader.request()
            val blank = fixture.capture("sky", fixture.at + 300_000_000L)
            fixture.clock.set(fixture.at + 9_000_000_000L)
            waiting.admit.complete(Unit)
            fixture.awaitAccepted(1)
            assertEquals(listOf(banner.id), reader.reads.map { it.id })
            val accepted = fixture.accepted().single()
            assertEquals("Camerupt used Rock Tomb!", (accepted.payload as RawTestimony).data)
            assertEquals(banner.monotonicTimeNanos, accepted.monotonicTimeNanos)
            assertEquals(banner.perceivedAt, accepted.perceivedAt)
            assertTrue(banner.id.value in accepted.evidenceReferences.orEmpty())
            assertFalse(blank.id.value in accepted.evidenceReferences.orEmpty())
            assertTrue(fixture.latencies().single().contains("selection=TEXT_KEYFRAME"))
            assertTrue(fixture.latencies().single().contains("queueMs=8000"))
            reader.request(index = 1).admit.complete(Unit)
            fixture.awaitAccepted(2)
            assertEquals(listOf(banner.id, blank.id), reader.reads.map { it.id })
            assertEquals(2, fixture.timeline.getArticles().count { it.payload is CropCaptured })
        } finally { fixture.close() }
    }

    @Test fun `distinct queued phases each release and reacquire a native turn with their own source clocks`() = runBlocking {
        val reader = ControlledReader(read = { listOf(text(it.label())) }, inspect = {
            when (it.label()) { "ready" -> visual(1); "used" -> visual(2); else -> null }
        })
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 1_000_000_000L)
            fixture.start()
            val ready = fixture.capture("ready", fixture.at + 200_000_000L)
            val first = reader.request()
            val used = fixture.capture("used", fixture.at + 1_100_000_000L)
            fixture.capture("sky", fixture.at + 1_300_000_000L)
            fixture.clock.set(fixture.at + 8_000_000_000L)
            first.admit.complete(Unit)
            fixture.awaitAccepted(1)
            val second = reader.request(index = 1)
            assertTrue(first.finished.isCompleted)
            assertEquals(OcrPriority.TRANSIENT, second.priority)
            second.admit.complete(Unit)
            fixture.awaitAccepted(2)
            assertEquals(listOf(ready.id, used.id), reader.reads.map { it.id })
            assertEquals(listOf(ready.monotonicTimeNanos, used.monotonicTimeNanos),
                fixture.accepted().map { it.monotonicTimeNanos })
        } finally { fixture.close() }
    }

    @Test fun `dedicated QTE crop retains a short nice frame before a blank without inventing an announcement`() = runBlocking {
        val reader = ControlledReader(read = { listOf(text(if (it.label() == "nice") "NICE!" else "")) },
            inspect = { if (it.label() == "nice") visual(3) else null })
        val fixture = Fixture(reader, BattleCropContracts.qteProgressText)
        try {
            fixture.start()
            val nice = fixture.capture("nice", fixture.at + 200_000_000L)
            val request = reader.request()
            fixture.capture("sky", fixture.at + 300_000_000L)
            request.admit.complete(Unit)
            fixture.awaitAccepted(1)
            val accepted = fixture.accepted().single()
            assertEquals("QTE_PROGRESS_TEXT_WITNESS", accepted.sourceId.id)
            assertEquals("NICE!", (accepted.payload as RawTestimony).data)
            assertEquals(nice.monotonicTimeNanos, accepted.monotonicTimeNanos)
            assertTrue(fixture.timeline.getArticles().none { it.sourceId.id == "ANNOUNCEMENT_WITNESS" })
        } finally { fixture.close() }
    }

    @Test fun `visual probe failure still admits the latest crop to ordinary OCR`() = runBlocking {
        val reader = ControlledReader(read = { listOf(text("Get Ready!")) }, inspect = { error("probe failure") })
        val fixture = Fixture(reader)
        try {
            fixture.start()
            val crop = fixture.capture("fallback", fixture.at)
            reader.request().admit.complete(Unit)
            fixture.awaitAccepted(1)
            assertEquals(listOf(crop.id), reader.reads.map { it.id })
            assertTrue(fixture.latencies().single().contains("selection=LATEST_FALLBACK"))
        } finally { fixture.close() }
    }

    @Test fun `clearer same episode frame captured during blank native read survives even after the banner disappears`() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val reader = ControlledReader(read = {
            if (it.label() == "fading") {
                reading.complete(Unit)
                finish.await()
                listOf(text(""))
            } else listOf(text("Camerupt used Rock Tomb!"))
        }, inspect = {
            when (it.label()) {
                "fading" -> visual(1)
                "clear" -> visual(1).copy(score = .99f)
                else -> null
            }
        })
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("fading", fixture.at)
            reader.request().admit.complete(Unit)
            reading.await()
            val clear = fixture.capture("clear", fixture.at + 100_000_000L, wall = 65_432L)
            fixture.capture("gone", fixture.at + 200_000_000L)
            finish.complete(Unit)
            fixture.awaitAccepted(1)
            reader.request(index = 1).admit.complete(Unit)
            fixture.awaitAccepted(2)
            val recovered = fixture.accepted().last()
            assertEquals("Camerupt used Rock Tomb!", (recovered.payload as RawTestimony).data)
            assertEquals(clear.monotonicTimeNanos, recovered.monotonicTimeNanos)
            assertEquals(clear.perceivedAt, recovered.perceivedAt)
            assertEquals(clear.id, reader.reads.last().id)
            assertTrue(clear.id.value in recovered.evidenceReferences.orEmpty())
        } finally { finish.complete(Unit); fixture.close() }
    }

    @Test fun `stop during a visual probe refuses its late offer and native request`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val reader = ControlledReader(read = { listOf(text("Get Ready!")) }, inspect = {
            withContext(NonCancellable) { started.complete(Unit); finish.await(); visual(1) }
        })
        val fixture = Fixture(reader)
        try {
            fixture.start()
            fixture.capture("late-probe", fixture.at)
            started.await()
            fixture.witness.stop()
            finish.complete(Unit)
            delay(25)
            assertTrue(reader.requests.isEmpty())
            assertTrue(fixture.accepted().isEmpty())
        } finally { finish.complete(Unit); fixture.close() }
    }

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

    private class Fixture(reader: AnnouncementCropReader,
        private val cropContract: BattleCropContract = BattleCropContracts.announcement) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("announcement-reader-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val at = System.nanoTime()
        val clock = AtomicLong(at + 10_000_000_000L)
        private val source = if (cropContract == BattleCropContracts.qteProgressText)
            "QTE_PROGRESS_TEXT_WITNESS" else "ANNOUNCEMENT_WITNESS"
        val witness = PersistedAnnouncementWitness(reader, clock::get,
            observerId = ObserverId(source, ObservationSource.SCREEN_CAPTURE),
            cropContract = cropContract, dispatcher = Dispatchers.Unconfined)
        fun start() = witness.start(match)
        suspend fun capture(label: String, sourceTime: Long, wall: Long = 12_345L): RealityArticle {
            val record = custody.submitTestimony(SourceId("ANNOUNCEMENT_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
                BattleCropProvenance(cropContract.cropName, 1080, 2400,
                    BattleCropBounds(100, 400, 900, 600))), wall, null, emptyList(), sourceTime)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }
        fun accepted() = timeline.getArticles().filter { it.sourceId.id == source }
        fun latencies() = timeline.getArticles().filter { it.sourceId.id == "${source}_LATENCY" }
            .map { (it.payload as RawTestimony).data.toString() }
        suspend fun awaitAccepted(count: Int) { await { accepted().size == count }; match.awaitAcceptedEvidencePublished() }
        fun close() { witness.stop(); match.release() }
    }

    private class ControlledReader(
        private val inspect: suspend (RealityArticle) -> TransientTextProbe.Reading? = { null },
        private val read: suspend (RealityArticle) -> List<OcrTextEvidence>,
    ) : AnnouncementCropReader {
        override suspend fun inspect(article: RealityArticle) = inspect.invoke(article)
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
        private fun visual(signature: Long) = TransientTextProbe.Reading(.9f, true, signature, null)
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
