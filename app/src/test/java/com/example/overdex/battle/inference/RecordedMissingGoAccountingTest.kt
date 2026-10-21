package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

/** Oct 15 09:00 Moondrop record; original article order and relative times. */
class RecordedMissingGoAccountingTest {
    @Test fun `recorded impacts still count on both sides without inventing a start or rewriting history`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/recorded-no-go-20261015.json")).readText())
        assertFalse(archive.articles.any { it.payload is ArchivedMatchStarted || it.payload is ArchivedCountdownGlyphWitnessed })
        val before = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveUseInference()
        val uses = archive.articles.flatMap { engine.accept(live(it)) } + engine.flush()
        assertEquals(ActivePokemonSide.entries.toSet(), uses.map { it.payload.attackingSide }.toSet())
        val firstDamage = archive.articles.first { it.payload is ArchivedActiveHpBarDamageTickMeasured }
        assertTrue(uses.any { firstDamage.articleId in it.predecessorIds.map(ArticleId::value) })
        assertTrue(uses.all { it.observedArticle.monotonicTimeNanos!! >= firstDamage.monotonicTimeNanos!! })
        assertEquals(uses.size, uses.map { it.payload.useId }.toSet().size)
        assertTrue(uses.any { it.payload.attackerSpeciesName == "Abomasnow" })
        assertTrue(uses.any { it.payload.attackerSpeciesName == "Camerupt" })
        assertTrue(uses.any { it.payload.attackerSpeciesName == "Sealeo" })
        // The first recorded QTE has an independently witnessed Get Ready and
        // announcement. HP loss during it must not become a Fast Move.
        val qte = archive.articles.first { it.payload is ArchivedGetReadyWitnessed }.monotonicTimeNanos!!
        val charge = archive.articles.first { it.payload is ArchivedChargeMoveUsedAnnounced }.monotonicTimeNanos!!
        assertFalse(uses.any { it.observedArticle.monotonicTimeNanos!! in qte..charge + 750_000_000L })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        println("October 15 recorded accounting: ${uses.groupingBy { it.payload.attackingSide }.eachCount()}; total=${uses.size}")
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
            is ArchivedActiveHpBarMotionCadenceMeasured -> ActiveHpBarMotionCadenceMeasured(side(p.movingSide), p.intervalNanos, p.verticalExcursionPixels, p.sampleCount)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(side(p.damagedBarSide), p.intervalNanos, p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> FastMoveRecipientVisualArtifactMeasured(side(p.damagedSide),
                p.changedPixelFraction, p.meanColorDistance, p.meanRed, p.meanGreen, p.meanBlue, p.centroidX, p.centroidY, p.changedSampleCount)
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> FastMoveRecipientVisualCadenceMeasured(side(p.damagedSide), p.intervalNanos)
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> PlayerChargeMoveEnergyFillIncreased(p.beforeBySlot, p.afterBySlot, p.changedSlots)
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            is ArchivedMatchEnded -> MatchEnded(BattleResult.valueOf(p.result))
            else -> RawTestimony("Other recorded article advances the delivery watermark")
        }
        return RealityArticle(id = ArticleId(a.articleId), perceivedAt = a.perceivedAt, recordedAt = a.recordedAt,
            sourceId = SourceId(a.sourceId), payload = payload, confidence = a.confidence,
            matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }

    private fun side(value: String) = ActivePokemonSide.valueOf(value)
}
