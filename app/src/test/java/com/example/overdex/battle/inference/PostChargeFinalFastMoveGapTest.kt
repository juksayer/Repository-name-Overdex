package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic boundary probes, not a claim about the number of attacks in a recorded battle.
 * The first test deliberately documents an unresolved gap: positive ordinary HP does not
 * close an unconfirmed charged pause, so a witnessed finishing impact is discarded at result.
 */
class PostChargeFinalFastMoveGapTest {
    @Test fun `known gap - missing used announcement holds final Incinerate through result despite normal HP return`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("normal-return", 18.0, normalHp(.1f)),
            source("normal-still-visible", 19.0, normalHp(.1f)),
            // A full independently specified 2.5-second cycle fits after normal return.
            source("finishing-impact", 20.5, damage(.1f, 0f)),
            source("opponent-faint", 20.6,
                ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Blissey")),
            source("result", 21.0, MatchEnded(BattleResult.WIN)),
        )
        val uses = sources.flatMap(inference::accept) + inference.flush()
        assertTrue("Known gap: the 12-second unconfirmed pause loses this synthetic terminal hit", uses.isEmpty())

        // Late testimony remains raw history; it cannot reopen an already closed live engine.
        assertTrue(inference.accept(source("late-used", 17.0, ChargeMoveUsedAnnounced)).isEmpty())
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `observed used announcement preserves the same terminal impact and its original time`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("used", 17.0, ChargeMoveUsedAnnounced),
            source("normal-return", 18.0, normalHp(.1f)),
            source("normal-still-visible", 19.0, normalHp(.1f)),
            source("finishing-impact", 20.5, damage(.1f, 0f)),
            source("opponent-faint", 20.6,
                ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Blissey")),
            source("result", 21.0, MatchEnded(BattleResult.WIN)),
        )
        val use = (sources.flatMap(inference::accept) + inference.flush()).single()
        assertEquals("fast-move-use:finishing-impact", use.payload.useId)
        assertEquals(ActivePokemonSide.PLAYER, use.payload.attackingSide)
        assertEquals("Camerupt", use.payload.attackerSpeciesName)
        assertEquals(nanos(20.5), use.observedArticle.monotonicTimeNanos)
        assertEquals(listOf("finishing-impact"), use.predecessorIds.map { it.value })
    }

    @Test fun `charge impact and result alone never supply a final Fast Move`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("used", 17.0, ChargeMoveUsedAnnounced),
            source("charged-loss", 17.3, damage(.8f, .1f)),
            source("result", 17.6, MatchEnded(BattleResult.WIN)),
        )
        assertTrue((sources.flatMap(inference::accept) + inference.flush()).isEmpty())
    }

    @Test fun `late used announcement before result releases only measured post-tail impact`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("charged-loss", 17.3, damage(.8f, .1f)),
            source("normal-return", 18.0, normalHp(.1f)),
            source("finishing-impact", 20.5, damage(.1f, 0f)),
            source("late-used", 17.0, ChargeMoveUsedAnnounced).copy(recordedAt = 20_800),
            source("result", 21.0, MatchEnded(BattleResult.WIN)),
        )
        val use = (sources.flatMap(inference::accept) + inference.flush()).single()
        assertEquals("fast-move-use:finishing-impact", use.payload.useId)
        assertEquals(nanos(20.5), use.observedArticle.monotonicTimeNanos)
        assertEquals(listOf("finishing-impact"), use.predecessorIds.map { it.value })
    }

    @Test fun `confirmed charge tail remains closed through its exact boundary and cannot invent a resumed use`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("used", 17.0, ChargeMoveUsedAnnounced),
            source("within-tail", 17.5, damage(.8f, .5f)),
            source("exact-tail-end", 17.75, damage(.5f, .4f)),
            source("clock", 20.0, RawTestimony("later source time")),
        )
        assertTrue((sources.flatMap(inference::accept) + inference.flush()).isEmpty())
        inference.accept(source("real-later-impact", 20.5, damage(.4f, .3f)))
        assertEquals(listOf("fast-move-use:real-later-impact"), inference.flush().map { it.payload.useId })
    }

    @Test fun `unconfirmed pause expiry permits later measured impact without generating attacks in the gap`() {
        val inference = prepared()
        val sources = listOf(
            source("ready", 10.0, GetReadyWitnessed),
            source("held-charge-loss", 17.3, damage(.8f, .1f)),
            source("normal-return", 22.1, normalHp(.1f)),
            source("later-impact", 24.6, damage(.1f, 0f)),
            source("clock", 27.5, RawTestimony("later source time")),
        )
        val uses = sources.flatMap(inference::accept) + inference.flush()
        assertEquals(listOf("fast-move-use:later-impact"), uses.map { it.payload.useId })
        assertEquals(nanos(24.6), uses.single().observedArticle.monotonicTimeNanos)
    }

    private fun prepared() = FastMoveUseInference().apply {
        accept(source("go", 0.0, MatchStarted))
        accept(source("player", 1.0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt")))
        accept(source("opponent", 1.0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Blissey")))
        observeIdentity(FastMoveIdentified(ActivePokemonSide.PLAYER, "Camerupt", "Incinerate",
            nanos(2.5), null, 0, "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"))
    }

    private fun normalHp(fill: Float) = ActiveHpBarMeasured(
        ActivePokemonSide.OPPONENT, 380, 450, 572, 468, fill, 672, 1493,
    )
    private fun damage(before: Float, after: Float) = ActiveHpBarDamageTickMeasured(
        ActivePokemonSide.OPPONENT, before, after, before - after,
    )
    private fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
    private fun source(id: String, seconds: Double, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), (seconds * 1000).toLong(), (seconds * 1000).toLong(), SourceId("TEST"), payload,
        monotonicTimeNanos = nanos(seconds),
    )
}
