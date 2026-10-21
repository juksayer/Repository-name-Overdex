package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

/** Unmodified Moondrop articles, with the original arrival order and source times. */
class RecordedPairedCameruptTest {
    @Test fun `five HP losses and their delayed flashes remain five attacks`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/2026-10-18-paired-camerupt.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveUseInference()
        val uses = archive.articles.flatMap { engine.accept(live(it)) } + engine.flush()
        val camerupt = uses.filter { it.payload.attackingSide == ActivePokemonSide.OPPONENT }
        assertEquals(5, camerupt.size)
        listOf(
            "8c4a5ad4-f449-48c7-a6ee-9c00836fb97a" to "2f6e55c4-1c99-4781-90c1-c53873ed2df3",
            "8aaf12bb-0d4e-4c92-ae7c-138eeb0fdff4" to "ad6d8e06-338c-4142-bdd3-0654ca667958",
        ).forEach { (loss, flash) ->
            assertEquals(1, camerupt.count { ArticleId(loss) in it.predecessorIds && ArticleId(flash) in it.predecessorIds })
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(side(p.side), p.speciesName, p.speciesId)
            is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(side(p.side), p.speciesName, p.speciesId, p.basis)
            is ArchivedFastMoveIdentified -> FastMoveIdentified(side(p.side), p.speciesName, p.moveName,
                p.moveDurationNanos, p.observedMedianIntervalNanos, p.cadenceSampleCount, p.basis)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(side(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(side(p.damagedBarSide), p.intervalNanos,
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            ArchivedMatchStarted -> MatchStarted
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            else -> RawTestimony("Other recorded article advances the delivery watermark")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt, SourceId(a.sourceId), payload,
            confidence = a.confidence, matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }
    private fun side(value: String) = ActivePokemonSide.valueOf(value)
}
