package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class PersistedForcedReplacementWitnessTest {
    @Test fun `raw OCR and prompt retain original crop clocks and references without emitting a faint`() = runBlocking {
        val reading = text("Switch in a new Pokémon?")
        val fixture = Fixture(ForcedReplacementCropReader { _, _ -> reading })
        try {
            fixture.start()
            val crop = fixture.capture("prompt", fixture.clock.get(), wall = 54_321L)
            await { fixture.articles().any { it.payload is ForcedReplacementPromptWitnessed } }
            fixture.match.awaitAcceptedEvidencePublished()
            val raw = fixture.articles().single { it.sourceId.id == "FORCED_REPLACEMENT_MESSAGE_WITNESS_OCR" }
            val prompt = fixture.articles().single { it.payload is ForcedReplacementPromptWitnessed }
            assertEquals(reading, (raw.payload as RawTestimony).ocr)
            assertEquals(reading.rawText, (raw.payload as RawTestimony).data)
            assertEquals(listOf(crop.id.value), raw.evidenceReferences)
            assertEquals(listOf(crop.id.value, raw.id.value), prompt.evidenceReferences)
            listOf(raw, prompt).forEach {
                assertEquals(crop.perceivedAt, it.perceivedAt)
                assertEquals(crop.monotonicTimeNanos, it.monotonicTimeNanos)
            }
            assertFalse(fixture.articles().any { it.payload is ActivePokemonFaintedWitnessed })
        } finally { fixture.close() }
    }

    @Test fun `completed OCR remains raw evidence when assignment expires during recognition`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val fixture = Fixture(ForcedReplacementCropReader { _, _ ->
            entered.complete(Unit); finish.await(); text("Switch in a new Pokémon?")
        })
        try {
            fixture.start()
            fixture.capture("expired", fixture.clock.get())
            withTimeout(5_000) { entered.await() }
            fixture.clock.addAndGet(3_000_000_000L)
            finish.complete(Unit)
            await { fixture.articles().any { it.payload is RawTestimony && it.sourceId.id.endsWith("_OCR") } }
            fixture.match.awaitAcceptedEvidencePublished()
            assertFalse(fixture.articles().any { it.payload is ForcedReplacementPromptWitnessed })
        } finally { fixture.close() }
    }

    @Test fun `pending crop work is conflated and unrelated crop lanes are never read`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val reads = CopyOnWriteArrayList<String>()
        val fixture = Fixture(ForcedReplacementCropReader { article, isFresh ->
            val label = (article.payload as CropCaptured).artifact.relativePath.substringAfterLast('/')
            reads += label
            if (label == "first.png") { entered.complete(Unit); finish.await() }
            if (isFresh()) text("") else null
        })
        try {
            fixture.start()
            fixture.capture("first", fixture.clock.get())
            withTimeout(5_000) { entered.await() }
            fixture.clock.addAndGet(500_000_000L)
            fixture.capture("middle", fixture.clock.get())
            fixture.clock.addAndGet(500_000_000L)
            fixture.capture("last", fixture.clock.get())
            fixture.capture("announcement", fixture.clock.get(), cropName = BattleCropContracts.announcement.cropName)
            finish.complete(Unit)
            await { reads.size == 2 }
            fixture.match.awaitAcceptedEvidencePublished()
            assertEquals(listOf("first.png", "last.png"), reads.toList())
        } finally { fixture.close() }
    }

    @Test fun `expired future and inactive assignment crops never enter OCR`() = runBlocking {
        val reads = CopyOnWriteArrayList<String>()
        val fixture = Fixture(ForcedReplacementCropReader { article, _ -> reads += article.id.value; text("") },
            needsReading = { false })
        try {
            fixture.start()
            fixture.capture("closed", fixture.clock.get())
            fixture.capture("old", fixture.clock.get() - 3_000_000_000L)
            fixture.capture("future", fixture.clock.get() + 1)
            fixture.match.awaitAcceptedEvidencePublished()
            assertTrue(reads.isEmpty())
        } finally { fixture.close() }
    }

    private class Fixture(reader: ForcedReplacementCropReader, needsReading: (Long) -> Boolean = { true }) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("replacement-prompt-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val clock = AtomicLong(System.nanoTime())
        private val witness = PersistedForcedReplacementWitness(reader, needsReading, clock::get, Dispatchers.Unconfined)
        fun start() = witness.start(match)
        fun articles() = timeline.getArticles()
        suspend fun capture(label: String, at: Long, wall: Long = 12_345L,
            cropName: String = BattleCropContracts.forcedReplacementMessage.cropName): RealityArticle {
            val record = custody.submitTestimony(SourceId("FORCED_REPLACEMENT_MESSAGE_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
                BattleCropProvenance(cropName, 1080, 2400, BattleCropBounds(130, 1740, 950, 1896))),
                wall, null, emptyList(), at)
            match.awaitAcceptedEvidencePublished()
            return articles().single { it.sequenceNumber == record.sequenceNumber }
        }
        fun close() { witness.stop(); match.release() }
    }

    companion object {
        private fun text(value: String) = OcrTextEvidence("RAW", value, value, emptyList(), 1080, null)
        private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
            while (!condition()) delay(1)
        }
    }
}
