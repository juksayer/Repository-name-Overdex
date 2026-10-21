package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class ResultMusicCorroborationTest {
    private fun music(theme: String = "WIN") = MatchResultMusicMeasured(theme, "sha", .98f, .6f, 0, 4_000_000_000)
    private fun article(id: String, payload: TestimonyPayload, seconds: Long = 100, match: String = "one") = RealityArticle(
        ArticleId(id), seconds * 1000, 200_000, SourceId("TEST"), payload,
        matchId = MatchId(match), monotonicTimeNanos = seconds * 1_000_000_000)

    @Test fun `music and result can arrive in either order with a delayed visual result`() {
        val sound = article("sound", music(), 100)
        val end = article("end", MatchEnded(BattleResult.WIN), 118)
        for (articles in listOf(listOf(sound, end), listOf(end, sound))) {
            val inference = ResultMusicCorroboration()
            assertTrue(inference.accept(articles.first()).isEmpty())
            val derived = inference.accept(articles.last()).single()
            assertTrue(derived.payload.agrees)
            assertEquals(sound, derived.music)
            assertEquals(end, derived.result)
            assertTrue(inference.accept(sound).isEmpty())
            assertTrue(inference.accept(end).isEmpty())
        }
    }

    @Test fun `disagreement is recorded without replacing the existing outcome`() {
        val inference = ResultMusicCorroboration()
        val end = article("end", MatchEnded(BattleResult.LOSS))
        inference.accept(end)
        val derived = inference.accept(article("sound", music())).single()
        assertFalse(derived.payload.agrees)
        assertEquals("LOSS", derived.payload.recordedResult)
        assertEquals("WIN", derived.payload.musicTheme)
        assertEquals(end, derived.result)
    }

    @Test fun `a tune alone never creates a match end or comparison`() {
        val inference = ResultMusicCorroboration()
        assertTrue(inference.accept(article("sound", music())).isEmpty())
    }

    @Test fun `unknown clocks other matches stale clips and ordinary battle music cannot corroborate`() {
        for (sound in listOf(article("sound", music(), seconds = 50),
                article("sound", music(), match = "another"),
                article("sound", music()).copy(monotonicTimeNanos = null),
                article("sound", music("BATTLE")))) {
            val inference = ResultMusicCorroboration()
            inference.accept(article("end", MatchEnded(BattleResult.WIN)))
            assertTrue(inference.accept(sound).isEmpty())
        }
    }
}
