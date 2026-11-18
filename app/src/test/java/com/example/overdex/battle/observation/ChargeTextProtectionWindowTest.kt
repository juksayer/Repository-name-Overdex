package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class ChargeTextProtectionWindowTest {
    @Test fun `a grade on its own line can hide HUD text but HUD labels alone cannot`() {
        val window = ChargeTextProtectionWindow()
        val source = "QTE_PROGRESS_TEXT_WITNESS"
        listOf("Liquidation", "Great League", "NICE WATER GUN").forEach {
            window.observe(article(RawTestimony(it), 36, source), 38 * SECOND)
        }
        assertFalse(window.isProtected(38 * SECOND))
        window.observe(article(RawTestimony("GREAT\nLqutdation"), 36, source), 40 * SECOND)
        assertTrue(window.isProtected(40 * SECOND))
        window.battlefieldReturned(41 * SECOND, 41 * SECOND)
        assertFalse(window.isProtected(41 * SECOND))
        window.observe(article(RawTestimony("GREAT!"), 39, source), 42 * SECOND)
        assertFalse(window.isProtected(42 * SECOND))
    }

    @Test fun `only fresh same-frame motion of both whole bars hides the move list`() {
        fun bar(player: Boolean, x: Int, y: Int = 710) = CompleteHpBarObserved(
            if (player) ActivePokemonSide.PLAYER else ActivePokemonSide.OPPONENT,
            x, y, x + 192, y + 17, 672, 1493, 188, 141, 0, "LIVE_COLOR", null, null, 220f, null)
        val window = ChargeTextProtectionWindow()
        fun pair(at: Long, p: Int, o: Int) {
            window.observeBar(bar(false, o), at, at)
            window.observeBar(bar(true, p), at, at)
        }
        pair(SECOND, 38, 395)
        window.observeBar(bar(true, 125), SECOND + 1, SECOND + 1)
        window.observeBar(bar(false, 441), SECOND + 2, SECOND + 2)
        assertFalse(window.isProtected(SECOND + 2))
        window.observeBar(bar(true, 38, 790), SECOND + 3, SECOND + 3)
        window.observeBar(bar(false, 395, 640), SECOND + 3, SECOND + 3)
        pair(2 * SECOND, 38, 441) // A single bar moves on an ordinary entrance.
        assertFalse(window.isProtected(2 * SECOND))
        pair(8 * SECOND, 125, 441) // The old neutral pair expired.
        assertFalse(window.isProtected(8 * SECOND))
        pair(9 * SECOND, 38, 395)
        pair(10 * SECOND, 125, 441)
        assertTrue(window.isProtected(18 * SECOND))
        window.battlefieldReturned(8 * SECOND, 18 * SECOND)
        assertTrue(window.isProtected(18 * SECOND))
        window.battlefieldReturned(19 * SECOND, 19 * SECOND)
        assertFalse(window.isProtected(19 * SECOND))
    }

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
