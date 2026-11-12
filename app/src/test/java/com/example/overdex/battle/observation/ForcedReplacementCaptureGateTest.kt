package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrTextEvidence
import org.junit.Assert.*
import org.junit.Test

class ForcedReplacementCaptureGateTest {
    private var now = 20 * SECOND
    private fun gate() = ForcedReplacementCaptureGate { now }

    @Test fun `ordinary combat opponent faint and red HP leave prompt reader asleep`() {
        val gate = gate()
        listOf(MatchStarted, ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT),
            hp(.1f), hp(0f, ActivePokemonSide.OPPONENT), RawTestimony("Switch")).forEach {
            gate.observe(article(it))
        }
        assertFalse(gate.isEnabled())
    }

    @Test fun `departure scheduling opens exactly three seconds and repeated cues cannot prolong it`() {
        val gate = gate()
        val initial = now
        gate.request(now)
        assertFalse(gate.acceptsFrame(now - 1))
        assertFalse(gate.acceptsFrame(now + 1))
        assertTrue(gate.acceptsFrame(now))
        repeat(2) { now += SECOND; gate.request(now); assertTrue(gate.isEnabled()) }
        now = initial + 3 * SECOND
        assertFalse(gate.isEnabled())
        assertFalse(gate.acceptsFrame(initial))
    }

    @Test fun `repeated zero HP does not reopen expired attention until positive player HP intervenes`() {
        val gate = gate()
        gate.observe(article(hp(0f)))
        assertTrue(gate.isEnabled())
        now += 3 * SECOND
        repeat(4) { gate.observe(article(hp(0f))); now += SECOND }
        assertFalse(gate.isEnabled())
        gate.observe(article(hp(.5f)))
        now += SECOND
        gate.observe(article(hp(0f)))
        assertTrue(gate.isEnabled())
    }

    @Test fun `initial counts do not wake but a valid player count decrease does`() {
        val gate = gate()
        gate.observe(article(PlayerPokeBallCountMeasured(3)))
        assertFalse(gate.isEnabled())
        now += SECOND
        gate.observe(article(PlayerPokeBallCountMeasured(2)))
        assertTrue(gate.isEnabled())
        now += 4 * SECOND
        gate.observe(article(PlayerPokeBallCountMeasured(3)))
        now += SECOND
        gate.observe(article(PlayerPokeBallCountMeasured(2)))
        assertFalse(gate.isEnabled())
    }

    @Test fun `charge release and new decision dial wake but countdown ticks cannot keep reading alive`() {
        val gate = gate()
        gate.observe(article(ChargeMoveUsedAnnounced))
        assertTrue(gate.isEnabled())
        now += 4 * SECOND
        gate.observe(article(BattleDecisionTimerObserved(10, "10", "TEST")))
        assertTrue(gate.isEnabled())
        repeat(5) { index -> now += SECOND; gate.observe(article(BattleDecisionTimerObserved(9 - index, "", "TEST"))) }
        assertFalse(gate.isEnabled())
        now += SECOND
        gate.observe(article(BattleDecisionTimerObserved(null, "", "TEST")))
        now += SECOND
        gate.observe(article(BattleDecisionTimerObserved(10, "10", "TEST")))
        assertTrue(gate.isEnabled())
    }

    @Test fun `successful prompt closes only its own assignment and suppresses remaining same-window cues`() {
        val gate = gate()
        gate.request(now)
        now += SECOND
        gate.observe(article(prompt()))
        assertFalse(gate.isEnabled())
        gate.request(now)
        now += SECOND
        gate.request(now)
        assertFalse(gate.isEnabled())
        now += 2 * SECOND
        gate.request(now)
        assertTrue(gate.isEnabled())
        gate.observe(article(prompt(), now - 4 * SECOND))
        assertTrue(gate.isEnabled())
    }

    @Test fun `future expired and old cues are refused and lifecycle boundaries cannot regress`() {
        val gate = gate()
        gate.request(now + 1)
        gate.request(now - 3 * SECOND)
        assertFalse(gate.isEnabled())
        gate.request(now)
        now += SECOND
        gate.observe(article(MatchEnded(BattleResult.LOSS)))
        gate.request(now)
        assertFalse(gate.isEnabled())
        gate.observe(article(ObservationRestartedByUser, now - SECOND))
        assertFalse(gate.isEnabled())
        now += SECOND
        gate.observe(article(ObservationRestartedByUser))
        gate.observe(article(ChargeMoveUsedAnnounced, now - SECOND))
        assertFalse(gate.isEnabled())
        gate.request(now)
        assertTrue(gate.isEnabled())
        gate.observe(article(ScreenIgnoredByUser, now - SECOND))
        assertTrue(gate.isEnabled())
        gate.stop()
        assertFalse(gate.isEnabled())
    }

    private fun hp(fraction: Float, side: ActivePokemonSide = ActivePokemonSide.PLAYER) =
        ActiveHpBarMeasured(side, 0, 0, 100, 10, fraction)
    private fun prompt() = ForcedReplacementPromptWitnessed("Switch in a new Pokémon?", "TEST",
        OcrTextEvidence("RAW", "Switch in a new Pokémon?", "Switch in a new Pokémon?", emptyList(), 1080, null))
    private fun article(payload: TestimonyPayload, at: Long = now) = RealityArticle(
        ArticleId(java.util.UUID.randomUUID().toString()), 0, 0, SourceId("TEST"), payload, monotonicTimeNanos = at)
    private companion object { const val SECOND = 1_000_000_000L }
}
