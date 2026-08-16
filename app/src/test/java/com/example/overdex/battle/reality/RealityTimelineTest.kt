package com.example.overdex.battle.reality

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RealityTimelineTest {

    private val sourceA = SourceId("SOURCE_A")
    private val sourceB = SourceId("SOURCE_B")
    private val interpreterX = SourceId("INTERPRETER_X")

    @Test
    fun `originating article preserves distinct perceived and recorded times`() {
        val timeline = InMemoryRealityTimeline()
        val id = ArticleId(UUID.randomUUID().toString())
        val perceivedTime = 1000L
        val recordedTime = 1005L
        
        val article = RealityArticle(
            id = id,
            perceivedAt = perceivedTime,
            recordedAt = recordedTime,
            sourceId = sourceA,
            payload = RawTestimony("Data")
        )

        timeline.append(article)
        
        val retrieved = timeline.getArticles().first()
        assertEquals(perceivedTime, retrieved.perceivedAt)
        assertEquals(recordedTime, retrieved.recordedAt)
        assertEquals(sourceA, retrieved.sourceId)
    }

    @Test
    fun `timeline preserves many-to-one informational ancestry`() {
        val timeline = InMemoryRealityTimeline()
        
        // Article A
        val articleA = RealityArticle(
            id = ArticleId("A"),
            perceivedAt = 100L,
            recordedAt = 101L,
            sourceId = sourceA,
            payload = RawTestimony("Visual 1")
        )
        
        // Article B
        val articleB = RealityArticle(
            id = ArticleId("B"),
            perceivedAt = 110L,
            recordedAt = 111L,
            sourceId = sourceB,
            payload = RawTestimony("Visual 2")
        )
        
        // Article C derived from A and B
        val articleC = RealityArticle(
            id = ArticleId("C"),
            perceivedAt = 110L, // perceivedAt of latest predecessor
            recordedAt = 120L,
            sourceId = interpreterX,
            payload = RawTestimony("Conclusion"),
            predecessorIds = listOf(articleA.id, articleB.id)
        )

        timeline.append(articleA)
        timeline.append(articleB)
        timeline.append(articleC)

        val articles = timeline.getArticles()
        assertEquals(3, articles.size)
        
        val retrievedC = articles[2]
        assertEquals(listOf(articleA.id, articleB.id), retrievedC.predecessorIds)
        assertEquals(interpreterX, retrievedC.sourceId)
    }

    @Test
    fun `derived articles do not modify their predecessors`() {
        val timeline = InMemoryRealityTimeline()
        
        val articleA = RealityArticle(
            id = ArticleId("A"),
            perceivedAt = 100L,
            recordedAt = 101L,
            sourceId = sourceA,
            payload = RawTestimony("Original")
        )
        
        timeline.append(articleA)
        
        // Reasoner creates a "replacement" or derivation C
        val articleC = RealityArticle(
            id = ArticleId("C"),
            perceivedAt = 100L,
            recordedAt = 110L,
            sourceId = interpreterX,
            payload = RawTestimony("Improved Interpretation"),
            predecessorIds = listOf(articleA.id)
        )
        
        timeline.append(articleC)
        
        val articles = timeline.getArticles()
        assertEquals(2, articles.size)
        
        // Verify Article A remains unchanged in the timeline
        val retrievedA = articles[0]
        assertEquals("Original", (retrievedA.payload as RawTestimony).data)
        
        // Verify Article C exists independently
        val retrievedC = articles[1]
        assertEquals("Improved Interpretation", (retrievedC.payload as RawTestimony).data)
        assertEquals(listOf(articleA.id), retrievedC.predecessorIds)
    }

    @Test
    fun `timeline protects internal store from external modification`() {
        val timeline = InMemoryRealityTimeline()
        val articleA = RealityArticle(
            id = ArticleId("A"),
            perceivedAt = 100L,
            recordedAt = 101L,
            sourceId = sourceA,
            payload = RawTestimony("A")
        )
        timeline.append(articleA)
        
        val list = timeline.getArticles()
        try {
            (list as MutableList).add(
                RealityArticle(ArticleId("X"), 0, 0, sourceB, RawTestimony("X"))
            )
        } catch (e: Exception) {
            // Unmodifiable or CopyOnWrite results in no affect
        }

        assertEquals(1, timeline.getArticles().size)
        assertEquals(ArticleId("A"), timeline.getArticles().first().id)
    }
    
    @Test
    fun `article id is independent of any external sequence`() {
        val id = ArticleId("IndependentID")
        val article = RealityArticle(
            id = id,
            perceivedAt = 500L,
            recordedAt = 600L,
            sourceId = sourceA,
            payload = RawTestimony("Data")
        )
        
        assertEquals("IndependentID", article.id.value)
    }
}
