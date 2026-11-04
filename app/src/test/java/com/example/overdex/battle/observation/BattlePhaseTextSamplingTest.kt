package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePhaseTextSamplingTest {
    @Test fun `late entry opens after both species and valid persistent HP loss without GO`() {
        val sampling = BattlePhaseTextSampling()
        sampling.observe(article("player", species(ActivePokemonSide.PLAYER), 10))
        sampling.observe(article("opponent", species(ActivePokemonSide.OPPONENT), 11))
        sampling.observe(article("full-bar", ActiveHpBarMeasured(ActivePokemonSide.OPPONENT,
            10, 10, 110, 20, 1f), 12))
        assertFalse(sampling.isEnabled(12))
        sampling.observe(article("loss", loss(), 13))
        assertFalse("Sampling must not predate its source evidence", sampling.isEnabled(12))
        assertTrue(sampling.isEnabled(13))
        assertTrue(sampling.isEnabled(14))
    }

    @Test fun `VS countdown text and inconsistent HP change cannot admit prebattle sampling`() {
        val sampling = BattlePhaseTextSampling()
        sampling.observe(article("vs", VsScreenWitnessed, 1))
        sampling.observe(article("countdown", CountdownGlyphWitnessed("GO", 1f, 1), 2))
        sampling.observe(article("text", RawTestimony("Get Ready!"), 3))
        sampling.observe(article("player", species(ActivePokemonSide.PLAYER), 4))
        sampling.observe(article("opponent", species(ActivePokemonSide.OPPONENT), 5))
        sampling.observe(article("inconsistent", ActiveHpBarDamageTickMeasured(
            ActivePokemonSide.OPPONENT, 1f, .9f, .2f), 6))
        assertFalse(sampling.isEnabled(7))
    }

    @Test fun `a later species cannot retroactively qualify an earlier loss`() {
        val sampling = BattlePhaseTextSampling()
        sampling.observe(article("player", species(ActivePokemonSide.PLAYER), 1))
        sampling.observe(article("early-loss", loss(), 2))
        sampling.observe(article("opponent", species(ActivePokemonSide.OPPONENT), 3))
        assertFalse(sampling.isEnabled(4))
        sampling.observe(article("later-loss", loss(), 5))
        assertTrue(sampling.isEnabled(5))
    }

    @Test fun `typed charged phases and an established start admit independent text sampling`() {
        listOf(GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced, MatchStarted).forEach { cue ->
            val sampling = BattlePhaseTextSampling()
            sampling.observe(article("cue", cue, 10))
            assertFalse(sampling.isEnabled(9))
            assertTrue(sampling.isEnabled(10))
        }
    }

    @Test fun `result and restart close sampling and old delivered combat cannot reopen it`() {
        val sampling = BattlePhaseTextSampling()
        sampling.observe(article("ready", GetReadyWitnessed, 10))
        sampling.observe(article("result", MatchEnded(BattleResult.WIN), 20))
        assertFalse(sampling.isEnabled(20))
        sampling.observe(article("restart", ObservationRestartedByUser, 30))
        assertFalse(sampling.isEnabled(31))
        sampling.observe(article("old-player", species(ActivePokemonSide.PLAYER), 11))
        sampling.observe(article("old-opponent", species(ActivePokemonSide.OPPONENT), 12))
        sampling.observe(article("old-loss", loss(), 13))
        assertFalse(sampling.isEnabled(31))
        sampling.observe(article("player", species(ActivePokemonSide.PLAYER), 31))
        sampling.observe(article("opponent", species(ActivePokemonSide.OPPONENT), 32))
        sampling.observe(article("loss", loss(), 33))
        assertTrue(sampling.isEnabled(33))
    }

    @Test fun `ignore session stop and observer stop disable sampling`() {
        listOf(ScreenIgnoredByUser, ObservationSessionStoppedByUser).forEach { stop ->
            val sampling = BattlePhaseTextSampling()
            sampling.observe(article("ready", GetReadyWitnessed, 10))
            sampling.observe(article("stop", stop, 20))
            sampling.observe(article("late-ready", GetReadyWitnessed, 21))
            assertFalse(sampling.isEnabled(22))
        }
        val sampling = BattlePhaseTextSampling()
        sampling.observe(article("ready", GetReadyWitnessed, 10))
        assertTrue(sampling.isEnabled(11))
        sampling.stop()
        sampling.observe(article("delayed-ready", GetReadyWitnessed, 12))
        assertFalse(sampling.isEnabled(13))
    }

    private fun species(side: ActivePokemonSide) = ActivePokemonSpeciesWitnessed(
        side, if (side == ActivePokemonSide.PLAYER) "Turtonator" else "Grimmsnarl")
    private fun loss() = ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f)
    private fun article(id: String, payload: TestimonyPayload, at: Long) = RealityArticle(
        ArticleId(id), at, at, SourceId("test"), payload, monotonicTimeNanos = at)
}
