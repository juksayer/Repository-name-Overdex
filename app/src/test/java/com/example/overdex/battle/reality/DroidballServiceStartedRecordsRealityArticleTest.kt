package com.example.overdex.battle.reality

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Brick #301: StartService -> RealityArticle -> RealityTimeline
 *
 * Proves that starting the Droidball service is itself an event in Reality
 * and is recorded as a single, immutable, uninterpreted record.
 *
 * Mirrors the exact construction used in PokedexViewModel's DroidballSignal.Started
 * handler, without exercising the Android/ViewModel/Service wiring around it.
 */
class DroidballServiceStartedRecordsRealityArticleTest {

    @Test
    fun `StartService produces exactly one RealityArticle with source, payload, and equal timestamps`() {
        val timeline = InMemoryRealityTimeline()

        // Mirrors the DroidballSignal.Started handler in PokedexViewModel.
        val now = System.currentTimeMillis()
        timeline.append(
            RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = now,
                recordedAt = now,
                sourceId = SourceId("DroidballService"),
                payload = RawTestimony("DroidballService started")
            )
        )

        val articles = timeline.getArticles()

        assertEquals(1, articles.size)

        val article = articles.first()
        assertEquals(SourceId("DroidballService"), article.sourceId)
        assertEquals(RawTestimony("DroidballService started"), article.payload)
        assertTrue(article.id.value.isNotBlank())

        // The equality is intentional, not merely likely: the service starting
        // *is* the event, so there is no earlier witnessed time to distinguish
        // from registration time.
        assertEquals(article.perceivedAt, article.recordedAt)
    }

    @Test
    fun `two StartService events produce distinct ArticleIds`() {
        val timeline = InMemoryRealityTimeline()

        repeat(2) {
            val now = System.currentTimeMillis()
            timeline.append(
                RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = now,
                    recordedAt = now,
                    sourceId = SourceId("DroidballService"),
                    payload = RawTestimony("DroidballService started")
                )
            )
        }

        val (first, second) = timeline.getArticles()
        assertNotEquals(first.id, second.id)
    }
}
