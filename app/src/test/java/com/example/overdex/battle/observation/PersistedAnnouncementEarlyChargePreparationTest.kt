package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.CustodyRecord
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** Proves scheduling can bypass a publication stall; it does not measure native OCR or a phone. */
class PersistedAnnouncementEarlyChargePreparationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `get ready prepares capture before raw publication while retaining its typed evidence chain`() = runBlocking {
        assertEarlyPhase("Get Ready!", AcceptedChargeCaptureCueKind.GET_READY, GetReadyWitnessed)
    }

    @Test fun `incoming attack prepares capture before raw publication while retaining its typed evidence chain`() = runBlocking {
        assertEarlyPhase("Attack incoming!", AcceptedChargeCaptureCueKind.ATTACK_INCOMING, AttackIncoming)
    }

    private suspend fun assertEarlyPhase(text: String, kind: AcceptedChargeCaptureCueKind, typedPayload: TestimonyPayload) {
        val reading = evidence(text)
        val fixture = Fixture(ControlledReader { listOf(reading) })
        val store = FileCropArtifactStore(temporaryFolder.root)
        val ready = PersistedAnnouncementPhraseWitness.getReady(store)
        val incoming = PersistedAttackIncomingWitness(store)
        try {
            ready.start(fixture.match)
            incoming.start(fixture.match)
            fixture.start()
            val crop = fixture.capture()
            fixture.reader.admit.complete(Unit)
            fixture.awaitRawBlocked()

            fixture.assertEarlyCue(crop, kind)
            assertTrue(fixture.timeline.getArticles().none { it.payload == typedPayload })
            fixture.custody.releasePublication.complete(Unit)
            await { fixture.timeline.getArticles().any { it.payload == typedPayload } }
            fixture.match.awaitAcceptedEvidencePublished()

            val raw = fixture.raw()
            val accepted = fixture.accepted()
            val typed = fixture.timeline.getArticles().single { it.payload == typedPayload }
            assertEquals(RawTestimony(text, reading), raw.payload)
            assertEquals(RawTestimony(text), accepted.payload)
            assertEquals(listOf(crop.id.value), raw.evidenceReferences)
            assertEquals(listOf(crop.id.value, raw.id.value), accepted.evidenceReferences)
            assertEquals(listOf(accepted.id.value), typed.evidenceReferences)
            assertSourceClocks(crop, raw, accepted, typed)
            assertEquals(crop, fixture.timeline.getArticles().single { it.id == crop.id })
            assertEquals("Publishing canonical evidence must not redeliver the preparation", 1, fixture.cues.size)
        } finally {
            ready.stop()
            incoming.stop()
            fixture.close()
        }
    }

    @Test fun `dedicated QTE vocabulary preparation bypasses publication without rewriting its raw reading`() = runBlocking {
        val reading = evidence("NIICE!")
        val fixture = Fixture(ControlledReader { listOf(reading) }, BattleCropContracts.qteProgressText)
        try {
            fixture.start()
            val crop = fixture.capture()
            fixture.reader.admit.complete(Unit)
            fixture.awaitRawBlocked()
            fixture.assertEarlyCue(crop, AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS)
            fixture.custody.releasePublication.complete(Unit)
            fixture.awaitAccepted()

            val raw = fixture.raw()
            val accepted = fixture.accepted()
            val vocabulary = fixture.timeline.getArticles().single {
                it.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS_VOCABULARY_RESOLUTION"
            }
            assertEquals(RawTestimony("NIICE!", reading), raw.payload)
            assertEquals(RawTestimony("NICE!"), accepted.payload)
            assertEquals(listOf(crop.id.value), raw.evidenceReferences)
            assertEquals(listOf(crop.id.value, raw.id.value), vocabulary.evidenceReferences)
            assertEquals(listOf(crop.id.value, raw.id.value, vocabulary.id.value), accepted.evidenceReferences)
            assertSourceClocks(crop, raw, vocabulary, accepted)
            assertEquals(1, fixture.cues.size)
            assertTrue(fixture.timeline.getArticles().none { it.payload is GetReadyWitnessed || it.payload is AttackIncoming })
        } finally { fixture.close() }
    }

    @Test fun `filtered phase words remain raw evidence and never prepare charge capture`() = runBlocking {
        val reading = evidence("Get Ready! Attack incoming! GREAT!", accepted = "")
        val fixture = Fixture(ControlledReader { listOf(reading) })
        try {
            fixture.start()
            val crop = fixture.capture()
            fixture.reader.admit.complete(Unit)
            fixture.awaitRawBlocked()
            assertTrue(fixture.cues.isEmpty())
            fixture.custody.releasePublication.complete(Unit)
            fixture.awaitAccepted()

            assertEquals(RawTestimony(reading.rawText, reading), fixture.raw().payload)
            assertEquals(RawTestimony(""), fixture.accepted().payload)
            assertSourceClocks(crop, fixture.raw(), fixture.accepted())
            assertTrue(fixture.cues.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun `native callback completing after stop cannot prepare capture or publish late text`() = runBlocking {
        val nativeStarted = CompletableDeferred<Unit>()
        val finishNative = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            withContext(NonCancellable) {
                nativeStarted.complete(Unit)
                finishNative.await()
                listOf(evidence("Get Ready!"))
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.custody.releasePublication.complete(Unit)
            fixture.start()
            fixture.capture()
            reader.admit.complete(Unit)
            withTimeout(5_000) { nativeStarted.await() }
            fixture.witness.stop()
            finishNative.complete(Unit)
            withTimeout(5_000) { reader.finished.await() }
            fixture.match.awaitAcceptedEvidencePublished()
            assertTrue(fixture.cues.isEmpty())
            assertTrue(fixture.custody.getRecords().none { it.sourceId.id.startsWith(fixture.source) })
            assertFalse(fixture.custody.rawReachedPublication.isCompleted)
        } finally {
            finishNative.complete(Unit)
            fixture.close()
        }
    }

    @Test fun `established match end closes preparation before publication and rejects a late native callback`() = runBlocking {
        val nativeStarted = CompletableDeferred<Unit>()
        val finishNative = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            nativeStarted.complete(Unit)
            finishNative.await()
            listOf(evidence("Get Ready!"))
        }
        val fixture = Fixture(reader)
        try {
            fixture.custody.releasePublication.complete(Unit)
            fixture.start()
            val crop = fixture.capture()
            reader.admit.complete(Unit)
            withTimeout(5_000) { nativeStarted.await() }
            // The crop and listener are valid before the terminal boundary.
            fixture.match.acceptedChargeCapturePreparation.offer(fixture.source,
                BattleCropContracts.announcement, crop, "Attack incoming!", fixture.clock.get())
            assertEquals(listOf(AcceptedChargeCaptureCueKind.ATTACK_INCOMING), fixture.cues.map { it.kind })

            // Offer directly from the first terminal subscriber. An asynchronous
            // close elsewhere would leave a gap in which this cue could dispatch.
            val endSubscriber = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                fixture.match.articles.first { it.payload is MatchEnded }.also {
                    fixture.match.acceptedChargeCapturePreparation.offer(fixture.source,
                        BattleCropContracts.announcement, crop, "Get Ready!", fixture.clock.get())
                }
            }
            fixture.custody.submitTestimony(SourceId("YOU_WIN_WITNESS"), RawTestimony("YOU WIN!"),
                crop.perceivedAt + 1_000L, null, emptyList(), fixture.capturedAt + 1_000_000_000L)
            val end = withTimeout(5_000) { endSubscriber.await() }
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            assertEquals("The handoff must already be closed when MatchEnded is emitted", 1, fixture.cues.size)

            // Recognition already in flight may still preserve its source
            // evidence, but cannot reopen HP preparation after the match ended.
            finishNative.complete(Unit)
            fixture.awaitAccepted()
            assertEquals(RawTestimony("Get Ready!"), fixture.accepted().payload)
            assertSourceClocks(crop, fixture.raw(), fixture.accepted())
            assertEquals(1, fixture.cues.size)
        } finally {
            finishNative.complete(Unit)
            fixture.close()
        }
    }

    private class Fixture(
        val reader: ControlledReader,
        private val contract: BattleCropContract = BattleCropContracts.announcement,
    ) {
        val source = if (contract == BattleCropContracts.qteProgressText)
            "QTE_PROGRESS_TEXT_WITNESS" else "ANNOUNCEMENT_WITNESS"
        val custody = PublicationBlockedCustody("${source}_OCR")
        val timeline = InMemoryRealityTimeline()
        val match = Match("early-charge-preparation", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val capturedAt = System.nanoTime()
        val clock = AtomicLong(capturedAt + 2_000_000_000L)
        val cues = CopyOnWriteArrayList<AcceptedChargeCaptureCue>()
        private val recordsAtCue = CopyOnWriteArrayList<List<CustodyRecord>>()
        private val detach = match.acceptedChargeCapturePreparation.attach {
            recordsAtCue += custody.getRecords().filter { record -> record.sourceId.id.startsWith(source) }
            cues += it
        }
        val witness = PersistedAnnouncementWitness(reader, clock::get,
            observerId = ObserverId(source, ObservationSource.SCREEN_CAPTURE),
            cropContract = contract, dispatcher = Dispatchers.Unconfined)

        fun start() = witness.start(match)

        suspend fun capture(): RealityArticle {
            val record = custody.submitTestimony(SourceId("ANNOUNCEMENT_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/charge-phase.png", "a".repeat(64), 1),
                BattleCropProvenance(contract.cropName, 1080, 2400,
                    BattleCropBounds(100, 400, 900, 600))), 65_432L, null, emptyList(), capturedAt)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }

        suspend fun awaitRawBlocked() = withTimeout(5_000) { custody.rawReachedPublication.await() }

        fun assertEarlyCue(crop: RealityArticle, kind: AcceptedChargeCaptureCueKind) {
            assertEquals(listOf(AcceptedChargeCaptureCue(kind, crop.id.value, capturedAt,
                clock.get(), crop.perceivedAt)), cues.toList())
            assertTrue("Preparation must precede latency and raw custody submissions", recordsAtCue.single().isEmpty())
            assertTrue("Raw testimony is preserved while its publication is blocked",
                custody.getRecords().any { it.sourceId.id == "${source}_OCR" })
            assertTrue("Raw and accepted Articles must still be blocked", timeline.getArticles().none {
                it.sourceId.id == "${source}_OCR" || it.sourceId.id == source
            })
            assertFalse(custody.releasePublication.isCompleted)
        }

        fun raw() = timeline.getArticles().single { it.sourceId.id == "${source}_OCR" }
        fun accepted() = timeline.getArticles().single { it.sourceId.id == source }
        suspend fun awaitAccepted() {
            await { timeline.getArticles().any { it.sourceId.id == source } }
            match.awaitAcceptedEvidencePublished()
        }
        fun close() {
            witness.stop()
            detach()
            custody.releasePublication.complete(Unit)
            match.release()
        }
    }

    /** Pause delivery to Match, retaining the original raw record in custody throughout. */
    private class PublicationBlockedCustody(
        private val rawSource: String,
        private val delegate: InMemoryTestimonyCustody = InMemoryTestimonyCustody(),
    ) : TestimonyCustody by delegate {
        val rawReachedPublication = CompletableDeferred<Unit>()
        val releasePublication = CompletableDeferred<Unit>()
        override val testimonyFlow = delegate.testimonyFlow.onEach {
            if (it.sourceId.id == rawSource) {
                rawReachedPublication.complete(Unit)
                releasePublication.await()
            }
        }
    }

    private class ControlledReader(private val read: suspend () -> List<OcrTextEvidence>) : AnnouncementCropReader {
        val admit = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        override suspend fun <T> withReadingTurn(priority: OcrPriority,
            block: suspend (AnnouncementCropReadingTurn) -> T): T = try {
            admit.await()
            block(object : AnnouncementCropReadingTurn {
                override var nativeReadNanos = 0L
                override var readCount = 0
                override suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile) =
                    read().also { readCount += it.size; nativeReadNanos += it.size * 100_000_000L }
            })
        } finally { finished.complete(Unit) }
    }

    companion object {
        private fun evidence(raw: String, accepted: String = raw) =
            OcrTextEvidence("RAW", raw, accepted, emptyList(), 1080, null)
        private fun assertSourceClocks(crop: RealityArticle, vararg descendants: RealityArticle) {
            descendants.forEach {
                assertEquals(crop.monotonicTimeNanos, it.monotonicTimeNanos)
                assertEquals(crop.perceivedAt, it.perceivedAt)
            }
        }
        private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
            while (!condition()) delay(5)
        }
    }
}
