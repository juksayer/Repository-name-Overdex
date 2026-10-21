package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

/** Original arrival order and source times from the Moondrop's 20:47 match. */
class RecordedFastMoveFusionTest {
    @Test fun `six Greninja HP losses and six flashes describe six attacks rather than eight`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/2026-10-18-greninja-opening.json")).readText())
        val unchanged = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveUseInference()
        val uses = (archive.articles.flatMap { engine.accept(live(it)) } + engine.flush())
            .filter { it.payload.attackingSide == ActivePokemonSide.PLAYER }
        assertEquals(uses.joinToString("\n") { "${it.observedArticle.monotonicTimeNanos}: ${it.predecessorIds}" }, 6, uses.size)
        val pairs = listOf(
            "b7e229bd-756b-4072-a5f2-97be9b87e1f9" to "1012473c-db13-4960-b0e0-8c851b5c71d8",
            "a247f569-5fe9-41a8-94ca-4fb6b4bc3fb1" to "987ccaa9-98ad-4b9c-8918-26eb9796df02",
            "b036b5d6-7fc3-4164-8926-8e2fc8fb1d14" to "8c810f6e-e525-4786-8903-aeba15636d47",
            "4b0b6433-fdc5-497f-860d-b42e44d7d626" to "b341ce44-356c-459e-aebc-0c271356add6",
            "23af4814-7a3a-44d5-9262-fa48bdf4b49a" to "8080f6d8-df45-4131-917c-b41c62a4153b",
            "c9fe2dea-faee-41d8-be6b-6321906b5a8a" to "700d19f6-5c8f-4ef0-96ae-a412888564f7",
        )
        for ((loss, flash) in pairs) {
            val use = uses.single { ArticleId(loss) in it.predecessorIds }
            assertTrue("$loss should be corroborated by $flash", ArticleId(flash) in use.predecessorIds)
            assertEquals(archive.articles.single { it.articleId == loss }.monotonicTimeNanos,
                use.observedArticle.monotonicTimeNanos)
            assertEquals("Greninja", use.payload.attackerSpeciesName)
        }
        assertEquals(unchanged, MatchArchiveSerializer.serialize(archive))
    }

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(side(p.side), p.speciesName, p.speciesId)
            is ArchivedFastMoveIdentified -> FastMoveIdentified(side(p.side), p.speciesName, p.moveName,
                p.moveDurationNanos, p.observedMedianIntervalNanos, p.cadenceSampleCount, p.basis)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(side(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(side(p.damagedBarSide), p.intervalNanos,
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarMotionCadenceMeasured -> ActiveHpBarMotionCadenceMeasured(side(p.movingSide), p.intervalNanos,
                p.verticalExcursionPixels, p.sampleCount)
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> PlayerChargeMoveEnergyFillIncreased(p.beforeBySlot, p.afterBySlot, p.changedSlots)
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> FastMoveRecipientVisualArtifactMeasured(side(p.damagedSide),
                p.changedPixelFraction, p.meanColorDistance, p.meanRed, p.meanGreen, p.meanBlue,
                p.centroidX, p.centroidY, p.changedSampleCount)
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
