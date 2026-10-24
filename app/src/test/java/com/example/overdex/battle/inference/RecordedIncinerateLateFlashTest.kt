package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Original arrival order and timestamps from 0330b719, archive seconds 226–245.1. */
class RecordedIncinerateLateFlashTest {
    @Test fun `five recorded damage cycles generate five uses and not six energy awards`() = runBlocking {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/2026-10-23-incinerate-late-flash.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        val fusion = FastMoveUseInference()
        val energy = FastMoveEnergyInference(TurtonatorKnowledge)
        val uses = mutableListOf<FastMoveUseDerivation>()
        val awards = mutableListOf<FastMoveEnergyDerivation>()
        suspend fun publish(newUses: List<FastMoveUseDerivation>, receivedAt: Long) {
            uses += newUses
            for (use in newUses) {
                awards += energy.accept(use.observedArticle.copy(
                    id = ArticleId(use.payload.useId), payload = use.payload,
                    recordedAt = receivedAt, predecessorIds = use.predecessorIds,
                ))
            }
        }
        for (archived in archive.articles) {
            // Old conclusions are retained in the fixture for audit, not fed
            // back as observations or as another opportunity to award energy.
            if (archived.payload is ArchivedFastMoveUseObserved ||
                archived.payload is ArchivedFastMoveEnergyDerived) continue
            val article = live(archived)
            awards += energy.accept(article)
            publish(fusion.accept(article), article.recordedAt)
        }
        publish(fusion.flush(), archive.articles.maxOf { it.recordedAt })
        assertEquals(6, archive.articles.count { it.payload is ArchivedFastMoveUseObserved })
        assertEquals(5, uses.size)
        assertEquals(5, awards.size)
        assertEquals(100, awards.sumOf { it.payload.totalEnergyGenerated })
        val loss = ArticleId("4a7b0fa9-5c96-43d6-b172-c448e3278a96")
        val flash = ArticleId("b5875904-1ab2-4d60-af5a-55bd14c81105")
        val paired = uses.single { loss in it.predecessorIds }
        assertTrue(flash in paired.predecessorIds)
        assertEquals(35_562_412_503_658L, paired.observedArticle.monotonicTimeNanos)
        assertEquals(setOf("HP_DAMAGE_TICK", "HP_BORDER_PULSE", "PLAYER_CHARGE_FILL_INCREASE"),
            paired.payload.evidenceKinds.toSet())
        val originalLosses = archive.articles.filter { it.payload is ArchivedActiveHpBarDamageTickMeasured }
            .map { it.articleId }.toSet()
        assertEquals(originalLosses, uses.map { it.observedArticle.id.value }.toSet())
        assertTrue(uses.all { it.payload.attackerSpeciesName == "Turtonator" })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            is ArchivedPlayerActiveIdentityResolved -> ActivePokemonSpeciesWitnessed(
                ActivePokemonSide.PLAYER, p.speciesName, p.speciesId,
                playerTeamSlot = p.teamSlot, appearanceStartedAtNanos = p.appearanceStartedAtNanos)
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(side(p.side), p.speciesName, p.speciesId)
            is ArchivedFastMoveIdentified -> FastMoveIdentified(side(p.side), p.speciesName, p.moveName,
                p.moveDurationNanos, p.observedMedianIntervalNanos, p.cadenceSampleCount, p.basis)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(side(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(side(p.damagedBarSide), p.intervalNanos,
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarMotionCadenceMeasured -> ActiveHpBarMotionCadenceMeasured(side(p.movingSide),
                p.intervalNanos, p.verticalExcursionPixels, p.sampleCount)
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> PlayerChargeMoveEnergyFillIncreased(
                p.beforeBySlot, p.afterBySlot, p.changedSlots)
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> FastMoveRecipientVisualArtifactMeasured(
                side(p.damagedSide), p.changedPixelFraction, p.meanColorDistance, p.meanRed, p.meanGreen, p.meanBlue,
                p.centroidX, p.centroidY, p.changedSampleCount)
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> FastMoveRecipientVisualCadenceMeasured(side(p.damagedSide), p.intervalNanos)
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            else -> RawTestimony("Other recorded measurement advances the source and receipt watermarks")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt, SourceId(a.sourceId), payload,
            confidence = a.confidence, matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }

    private fun side(value: String) = ActivePokemonSide.valueOf(value)

    private object TurtonatorKnowledge : PokemonKnowledge {
        private val pokemon = Pokemon(776, "Turtonator", listOf(PokemonType.FIRE, PokemonType.DRAGON), "Alola",
            fastMoves = listOf(Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5)), chargedMoves = emptyList())
        override suspend fun getPokemonByName(name: String) = pokemon.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int) = pokemon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
    }
}
