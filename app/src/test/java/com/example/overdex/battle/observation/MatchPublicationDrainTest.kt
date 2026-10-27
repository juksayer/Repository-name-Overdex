package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MatchPublicationDrainTest {
    @Test fun `immediate drain preserves accepted burst stop reason and offline testimony`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("drain-burst", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        try {
            val records = (0 until 128).map { index ->
                custody.submitTestimony(SourceId("CAPTURE_FIXTURE"), RawTestimony("sentinel-$index"),
                    1_000L + index, 0.8f, listOf("preserved-$index"), 9_000L + index)
            }
            val stopped = custody.submitTestimony(SourceId("DROIDBALL_SYSTEM_CONTROL"),
                RawTestimony("SCREEN_OFF_AND_LOCKED"), 2_000L, 1f, emptyList(), 20_000L)
            val offline = custody.submitAvailability(SourceId("CAPTURE_FIXTURE"), false, 2_001L)

            // No delay gives the collectors an artificial head start before stopping.
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            val articles = timeline.getArticles()
            assertEquals(130, articles.size)
            (records + stopped).forEach { accepted ->
                val article = articles.single { it.sequenceNumber == accepted.sequenceNumber }
                assertEquals(accepted.payload, article.payload)
                assertEquals(accepted.sourceId, article.sourceId)
                assertEquals(accepted.timestamp, article.perceivedAt)
                assertEquals(accepted.monotonicTimeNanos, article.monotonicTimeNanos)
                assertEquals(accepted.confidence, article.confidence)
                assertEquals(accepted.evidenceReferences, article.evidenceReferences)
            }
            val availability = articles.single { it.sequenceNumber == offline.sequenceNumber }
            assertEquals(WitnessOperating(false), availability.payload)
            assertEquals(offline.sourceId, availability.sourceId)
            assertEquals(offline.timestamp, availability.perceivedAt)
            assertEquals(offline.monotonicTimeNanos, availability.monotonicTimeNanos)
            assertTrue(articles.none { it.payload is MatchEnded || it.payload is MatchStarted })
            assertTrue(articles.none { it.payload is ObservationSessionStoppedByUser })
            assertEquals(records + stopped + offline, custody.getRecords())
        } finally { match.release() }
    }

    @Test fun `drain waits until interpretation finishes after raw article publication`() = runBlocking {
        val lookupEntered = CompletableDeferred<Unit>()
        val finishLookup = CompletableDeferred<Unit>()
        val knowledge = object : FakePokemonKnowledge() {
            override suspend fun getPokemonByName(name: String): Pokemon? {
                lookupEntered.complete(Unit)
                finishLookup.await()
                return super.getPokemonByName(name)
            }
        }
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("drain-interpretation", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = knowledge)
        try {
            custody.submitTestimony(SourceId("SPECIES_WITNESS"), RawTestimony("Pikachu"),
                1_000L, null, emptyList(), 10_000L)
            withTimeout(5_000) {
                lookupEntered.await()
                assertEquals(1, timeline.getArticles().size)
                val drained = async(start = CoroutineStart.UNDISPATCHED) {
                    match.awaitAcceptedEvidencePublished()
                }
                assertFalse("Raw publication alone must not complete the barrier", drained.isCompleted)
                finishLookup.complete(Unit)
                drained.await()
            }
            assertEquals(BattleEventType.POKEMON_IDENTIFIED, match.battleMemory.battleHistory.single().type)
            assertEquals("Pikachu", match.battleMemory.battleHistory.single().message)
        } finally {
            finishLookup.complete(Unit)
            match.release()
        }
    }

    @Test fun `drain includes inline derived article with unchanged source time and lineage`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("drain-derived", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        try {
            custody.submitTestimony(SourceId("ATTACK_INCOMING_WITNESS"), RawTestimony("Attack incoming!"),
                1_234L, null, emptyList(), 12_345L)
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            val raw = timeline.getArticles().single { it.payload is RawTestimony }
            val derived = timeline.getArticles().single { it.payload is AttackIncoming }
            assertEquals(listOf(raw.id), derived.predecessorIds)
            assertEquals(raw.perceivedAt, derived.perceivedAt)
            assertEquals(raw.monotonicTimeNanos, derived.monotonicTimeNanos)
            assertTrue(timeline.getArticles().none { it.payload is MatchEnded })
        } finally { match.release() }
    }

    @Test fun `empty and one lane drains return and leave match available for subsequent evidence`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("drain-remains-open", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        try {
            withTimeout(5_000) {
                match.awaitAcceptedEvidencePublished()
                custody.submitAvailability(SourceId("FIXTURE"), true, 1_000L)
                match.awaitAcceptedEvidencePublished()
                assertEquals(listOf(WitnessOperating(true)), timeline.getArticles().map { it.payload })
                custody.submitTestimony(SourceId("FIXTURE"), RawTestimony("later reading"), 1_001L)
                match.awaitAcceptedEvidencePublished()
                assertEquals(listOf(WitnessOperating(true), RawTestimony("later reading")),
                    timeline.getArticles().map { it.payload })
                match.awaitAcceptedEvidencePublished()
                assertEquals(2, timeline.getArticles().size)
            }
        } finally { match.release() }
    }
}
