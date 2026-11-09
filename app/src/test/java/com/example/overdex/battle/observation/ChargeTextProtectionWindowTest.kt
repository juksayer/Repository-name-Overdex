package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class ChargeTextProtectionWindowTest {
    @Test fun `late preparation protects only the remaining source timed window`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(GetReadyWitnessed, 10), 15 * SECOND)
        assertTrue(window.isProtected(21 * SECOND))
        assertFalse(window.isProtected(22 * SECOND))
    }

    @Test fun `expired preparation and untrusted raw grades cannot hide the HUD`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(GetReadyWitnessed, 10), 23 * SECOND)
        window.observe(article(RawTestimony("NICE!"), 23), 23 * SECOND)
        assertFalse(window.isProtected(23 * SECOND))
        window.observe(article(RawTestimony("NICE!"), 24, "QTE_PROGRESS_TEXT_WITNESS"), 24 * SECOND)
        assertTrue(window.isProtected(24 * SECOND))
        assertFalse(window.isProtected(32 * SECOND))
    }

    @Test fun `new phase survives delayed earlier faint and source expired cues`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(GetReadyWitnessed, 20), 20 * SECOND)
        window.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Old"), 15), 21 * SECOND)
        assertTrue(window.isProtected(21 * SECOND))
        window.observe(article(AttackIncoming, 4), 22 * SECOND)
        assertEquals(32 * SECOND, window.untilNanos)
    }

    @Test fun `result stops protection and late pre result QTE cannot reopen it`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(GetReadyWitnessed, 10), 10 * SECOND)
        window.observe(article(MatchEnded(BattleResult.WIN), 12), 12 * SECOND)
        window.observe(article(RawTestimony("GREAT!"), 11, "QTE_PROGRESS_TEXT_WITNESS"), 13 * SECOND)
        assertFalse(window.isProtected(13 * SECOND))
    }

    @Test fun `new charge can protect text after a completed earlier match`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(MatchEnded(BattleResult.WIN), 12), 12 * SECOND)
        window.observe(article(MatchStarted, 30), 30 * SECOND)
        window.observe(article(AttackIncoming, 40), 40 * SECOND)
        assertTrue(window.isProtected(50 * SECOND))
        assertFalse(window.isProtected(52 * SECOND))
    }

    @Test fun `used cue keeps short impact tail and future source cannot hide HUD`() {
        val window = ChargeTextProtectionWindow()
        window.observe(article(ChargeMoveUsedAnnounced, 10), 11 * SECOND)
        assertTrue(window.isProtected(12 * SECOND))
        assertFalse(window.isProtected(13 * SECOND))
        window.observe(article(GetReadyWitnessed, 25), 20 * SECOND)
        assertFalse(window.isProtected(20 * SECOND))
    }

    private fun article(payload: TestimonyPayload, seconds: Long, source: String = "test") = RealityArticle(
        ArticleId("$source:$seconds"), seconds * 1000, seconds * 1000, SourceId(source), payload,
        monotonicTimeNanos = seconds * SECOND)

    private companion object { const val SECOND = 1_000_000_000L }
}
