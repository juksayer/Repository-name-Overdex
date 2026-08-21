package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRealityHandoffTest {

    private val sourceId = SourceId("ATTACK_INCOMING_COLLECTOR")

    @Test
    fun `accepted testimony is automatically appended to reality timeline`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        val testimonyPayload = RawTestimony("ATTACK_INCOMING")
        val timestamp = 123456789L

        // Submit to custody
        custody.submitTestimony(
            sourceId = sourceId,
            payload = testimonyPayload,
            timestamp = timestamp,
            confidence = 1.0f
        )

        // Give the background collection a moment
        delay(100)

        // Verify it reached the Reality Timeline
        val articles = timeline.getArticles()
        assertEquals(1, articles.size)
        
        val article = articles.first()
        assertEquals(testimonyPayload, article.payload)
        assertEquals(timestamp, article.perceivedAt)
        assertEquals(sourceId, article.sourceId)
        assertTrue("recordedAt should be current system time", article.recordedAt > 0)
        
        match.release()
    }

    @Test
    fun `multiple testimonies produce independent reality articles in order`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "M1",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        custody.submitTestimony(sourceId, RawTestimony("1"), 100L, 1.0f)
        custody.submitTestimony(sourceId, RawTestimony("2"), 110L, 1.0f)
        custody.submitTestimony(sourceId, RawTestimony("3"), 120L, 1.0f)

        delay(200)

        val articles = timeline.getArticles()
        assertEquals(3, articles.size)
        assertEquals("1", (articles[0].payload as RawTestimony).data)
        assertEquals("2", (articles[1].payload as RawTestimony).data)
        assertEquals("3", (articles[2].payload as RawTestimony).data)
        
        match.release()
    }
}
