package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchInterpretationTest {

    @Test
    fun `species testimony is interpreted and recorded in battle memory`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        val speciesName = "Bulbasaur"
        val timestamp = 1000L

        // Submit Species Testimony
        custody.submitTestimony(
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony(speciesName),
            timestamp = timestamp,
            confidence = 1.0f
        )

        // Allow background collection and interpretation
        delay(150)

        // 1. Verify Reality Timeline preservation
        val articles = timeline.getArticles()
        assertEquals(1, articles.size)
        assertEquals(speciesName, (articles.first().payload as RawTestimony).data)

        // 2. Verify Battle Memory interpretation
        val events = match.battleMemory.timeline.events
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals(BattleEventType.POKEMON_IDENTIFIED, event.type)
        assertEquals(BattleActor.ENEMY, event.actor)
        assertEquals(speciesName, event.message)
        assertEquals(timestamp, event.timestamp)

        match.release()
    }

    @Test
    fun `unsupported testimony is preserved in reality but not recorded in battle memory`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        // Submit Unsupported Testimony
        custody.submitTestimony(
            sourceId = SourceId("UNKNOWN_SOURCE"),
            payload = RawTestimony("Some data"),
            timestamp = 2000L,
            confidence = 1.0f
        )

        delay(150)

        // 1. Verify Reality Timeline preservation
        val articles = timeline.getArticles()
        assertEquals(1, articles.size)

        // 2. Verify Battle Memory (should be empty)
        val events = match.battleMemory.timeline.events
        assertEquals(0, events.size)

        match.release()
    }

    @Test
    fun `you win witness testimony with you wvin variant creates derived reality article`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        custody.submitTestimony(
            sourceId = SourceId("YOU_WIN_WITNESS"),
            payload = RawTestimony("YOU WVIN!"),
            timestamp = 1500L,
            confidence = null
        )

        delay(150)

        val articles = timeline.getArticles()
        assertEquals(2, articles.size)

        // First article: original witness article
        val witnessArticle = articles[0]
        assertEquals(SourceId("YOU_WIN_WITNESS"), witnessArticle.sourceId)
        assertEquals("YOU WVIN!", (witnessArticle.payload as RawTestimony).data)
        assertEquals(null, witnessArticle.confidence)
        assertEquals(emptyList<com.example.overdex.battle.reality.ArticleId>(), witnessArticle.predecessorIds)

        // Second article: derived interpreter article
        val derivedArticle = articles[1]
        assertEquals(SourceId("BATTLE_INTERPRETER"), derivedArticle.sourceId)
        assertEquals(listOf(witnessArticle.id), derivedArticle.predecessorIds)
        assertEquals("WIN", (derivedArticle.payload as RawTestimony).data)

        match.release()
    }
}
