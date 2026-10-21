package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class MatchArchiveSourceTest {
    @Test fun `summary and export isolate a match from session and previous match articles`() {
        val timeline = InMemoryRealityTimeline()
        val current = MatchId("current")
        listOf(null, MatchId("previous"), current).forEachIndexed { i, match ->
            timeline.append(RealityArticle(ArticleId("article-$i"), 100L + i, 120L + i,
                SourceId("TEST"), RawTestimony("text-$i"), emptyList(), matchId = match))
        }
        val source = MatchArchiveSource(current, timeline)
        val snapshot = source.snapshot()
        assertEquals("current", snapshot.matchId)
        assertEquals(listOf("article-2"), snapshot.articles.map { it.articleId })
        assertEquals(3, timeline.getArticles().size)
        // New session articles arriving after the match cannot poison the next refresh.
        timeline.append(RealityArticle(ArticleId("later"), 130, 130,
            SourceId("SESSION"), RawTestimony("stopped"), emptyList(), matchId = null))
        assertEquals(snapshot, source.snapshot())
        assertEquals(4, timeline.getArticles().size)
    }
}
