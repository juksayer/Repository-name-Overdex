package com.example.overdex.model

import com.example.overdex.battle.reality.ArticleId
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleTimelineTest {

    @Test
    fun `record preserves the sourceArticleId of a BattleEvent`() {
        // 1. Construct a BattleEvent with a unique ArticleId
        val articleId = ArticleId("TIMELINE-CUSTODY-001")
        val event = BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu",
            sourceArticleId = articleId
        )

        // 2. Record it using the existing BattleTimeline
        val timeline = BattleTimeline()
        timeline.record(event)

        // 3. Retrieve the recorded event from timeline.events
        val recordedEvent = timeline.events[0]

        // 4. Assert that its sourceArticleId is exactly the original ArticleId
        assertEquals(
            "The sourceArticleId must be preserved in the timeline",
            articleId,
            recordedEvent.sourceArticleId
        )
    }
}
