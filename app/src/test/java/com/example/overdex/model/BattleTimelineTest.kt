package com.example.overdex.model

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleTimelineTest {

    @Test
    fun `measurement articles do not invalidate the shell event count`() {
        val timeline = BattleTimeline()
        var invalidations = 0
        val scope = Any()
        val observer = SnapshotStateObserver { callback -> callback() }
        observer.start()
        try {
            observer.observeReads(scope, { invalidations++ }) { timeline.eventCount }
            repeat(100) { index ->
                timeline.record(RealityArticle(ArticleId("measurement-$index"), 100L, 101L,
                    SourceId("MEASUREMENT"), RawTestimony("sample")))
            }
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            timeline.record(BattleEvent(type = BattleEventType.BATTLE_STARTED))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun `raw article bursts preserve the complete ledger without changing semantic history`() {
        val timeline = BattleTimeline()
        val first = BattleEvent(type = BattleEventType.BATTLE_STARTED, timestamp = 100L)
        timeline.record(first)
        val beforeBurst = timeline.events
        val articles = (1..1_000).map { index ->
            RealityArticle(ArticleId("raw-$index"), index.toLong(), index.toLong() + 10L,
                SourceId("MEASUREMENT"), RawTestimony("sample-$index"))
        }
        articles.forEach(timeline::record)

        assertEquals(1, timeline.eventCount)
        assertEquals(listOf(first), timeline.events)
        assertEquals(beforeBurst, timeline.events)
        assertEquals(listOf(first) + articles, timeline.records)
        val last = BattleEvent(type = BattleEventType.BATTLE_ENDED, timestamp = 2_000L)
        timeline.record(last)
        assertEquals(2, timeline.eventCount)
        assertEquals(listOf(first, last), timeline.events)
        assertEquals(listOf(first), beforeBurst)

        timeline.clear()
        assertEquals(0, timeline.eventCount)
        assertEquals(emptyList<BattleEvent>(), timeline.events)
        assertEquals(emptyList<TimelineRecord>(), timeline.records)
    }

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
