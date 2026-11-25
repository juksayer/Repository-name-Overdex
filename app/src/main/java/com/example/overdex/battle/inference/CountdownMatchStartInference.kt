package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleWitnessContracts
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.UUID

/** The first positive observation establishes that this attempt is underway at its capture time. */
internal object BattleStartEvidence {
    const val SOURCE = "WITNESSED_BATTLE_START"
    private val glyphs = setOf("3", "2", "1", "GO")

    fun accepts(p: TestimonyPayload, source: String, hasCapture: Boolean = false) = qualifies(p, source, hasCapture)
    fun accepts(row: ArchivedRealityArticle) = qualifies(row.payload, row.sourceId, !row.evidenceReferences.isNullOrEmpty())

    private fun qualifies(p: Any, source: String, hasCapture: Boolean): Boolean = when (p) {
        is CountdownGlyphWitnessed -> source == BattleWitnessContracts.countdownGlyph.witnessId && p.glyph in glyphs
        is ArchivedCountdownGlyphWitnessed -> source == BattleWitnessContracts.countdownGlyph.witnessId && p.glyph in glyphs
        is CropCaptured -> p.nativeTeamBadgePresent == true && nativeBadgeCrop(source, p.cropProvenance.cropName)
        is ArchivedCropCaptured -> p.nativeTeamBadgePresent == true && nativeBadgeCrop(source, p.cropName)
        is ActivePokemonSpeciesWitnessed -> hasCapture && nativeSpecies(source, p.speciesId, p.ocrMatchBasis)
        is ArchivedActivePokemonSpeciesWitnessed -> hasCapture && nativeSpecies(source, p.speciesId, p.ocrMatchBasis)
        is HpBarBorderPulse -> p.status == ApertureStatus.PRESENT
        is ArchivedHpBarBorderPulse -> p.status == "PRESENT"
        is CompleteHpBarObserved -> p.interiorColumns > 0 && p.status in measuredColors
        is ArchivedCompleteHpBarObserved -> p.interiorColumns > 0 && p.status in measuredColors
        is BattleDecisionTimerObserved -> p.secondsRemaining != null
        is ArchivedBattleDecisionTimerObserved -> p.secondsRemaining != null
        is FastMoveSoundMeasured -> p.audible && p.referenceMatchAccepted
        is ArchivedFastMoveSoundMeasured -> p.audible && p.referenceMatchAccepted
        is PlayerChargeButtonFillHeightMeasured -> p.fillHeightFraction?.let { it in 0f..1f } == true
        is ArchivedPlayerChargeButtonFillHeightMeasured -> p.fillHeightFraction?.let { it in 0f..1f } == true
        is RawTestimony -> hasCapture && battleAnnouncement(source, p.data as? String)
        is ArchivedRawText -> hasCapture && battleAnnouncement(source, p.value)
        is ActiveHpBarDamageTickMeasured -> settledLoss(p.beforeFraction, p.afterFraction, p.lostFraction)
        is ArchivedActiveHpBarDamageTickMeasured -> settledLoss(p.beforeFraction, p.afterFraction, p.lostFraction)
        GetReadyWitnessed, ArchivedGetReadyWitnessed, AttackIncoming, ArchivedAttackIncoming,
        ChargeMoveUsedAnnounced, ArchivedChargeMoveUsedAnnounced,
        is ActivePokemonTypesWitnessed, is ArchivedActivePokemonTypesWitnessed,
        is ActiveHpBarMeasured, is ArchivedActiveHpBarMeasured,
        is ActiveHpBarMotionCadenceMeasured, is ArchivedActiveHpBarMotionCadenceMeasured,
        is PlayerInactiveHpBarMeasured, is ArchivedPlayerInactiveHpBarMeasured,
        is PlayerPokeBallCountMeasured, is ArchivedPlayerPokeBallCountMeasured,
        is OpponentBattleResourceCountMeasured, is ArchivedOpponentBattleResourceCountMeasured,
        is PlayerShieldCountMeasured, is ArchivedPlayerShieldCountMeasured,
        is ForcedReplacementPromptWitnessed, is ArchivedForcedReplacementPromptWitnessed,
        is QteMoveTypeWitnessed, is ArchivedQteMoveTypeWitnessed,
        is PlayerChargeButtonTouchWitnessed, is ArchivedPlayerChargeButtonTouchWitnessed,
        is FastMoveUseObserved, is ArchivedFastMoveUseObserved,
        is FastMoveRecipientVisualArtifactMeasured, is ArchivedFastMoveRecipientVisualArtifactMeasured,
        is FastMoveEffectivenessWitnessed, is ArchivedFastMoveEffectivenessWitnessed,
        is ChargedMoveEnergySpent, is ArchivedChargedMoveEnergySpent,
        is ActivePokemonFaintedWitnessed, is ArchivedActivePokemonFaintedWitnessed,
        is BattleCryLocalized, is ArchivedBattleCryLocalized,
        is ActiveHpBarBorderCadenceMeasured, is ArchivedActiveHpBarBorderCadenceMeasured,
        is FastMoveRecipientVisualCadenceMeasured, is ArchivedFastMoveRecipientVisualCadenceMeasured,
        is PlayerChargeMoveEnergyFillCadenceMeasured, is ArchivedPlayerChargeMoveEnergyFillCadenceMeasured,
        is PlayerChargedMoveEffectivenessObserved, is ArchivedPlayerChargedMoveEffectivenessObserved,
        is StatChangeSoundMeasured, is ArchivedStatChangeSoundMeasured,
        is FinishingHitCredited, is ArchivedFinishingHitCredited,
        is StatChangeObserved, is ArchivedStatChangeObserved,
        is ActiveHpBarBorderPulseObserved, is ArchivedActiveHpBarBorderPulseObserved,
        is PlayerChargeMoveEnergyFillIncreased, is ArchivedPlayerChargeMoveEnergyFillIncreased,
        is ChargeMoveQteVibrationPatternInferred, is ArchivedChargeMoveQteVibrationPatternInferred -> true
        else -> false
    }

    private fun settledLoss(before: Float, after: Float, lost: Float) = before in 0f..1f && after in 0f..1f &&
        before > after && lost > 0f && kotlin.math.abs(before - after - lost) < .001f

    private val measuredColors = setOf("LIVE_COLOR", "DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR")
    private fun nativeBadgeCrop(source: String, name: String): Boolean {
        val player = BattleWitnessContracts.playerActiveSpeciesTextCapture
        val opponent = BattleWitnessContracts.opponentActiveSpeciesTextCapture
        return source == player.witnessId && name == player.crop.cropName || source == opponent.witnessId && name == opponent.crop.cropName
    }
    private fun nativeSpecies(source: String, id: Int?, basis: String?) = id != null &&
        (source == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" ||
            isSideLocatedSpeciesSource(source) && basis?.startsWith("EXACT") == true)
    private val usedMove = Regex("""^.+? used .+?!$""", RegexOption.IGNORE_CASE)
    private fun battleAnnouncement(source: String, text: String?): Boolean {
        if (source != "ANNOUNCEMENT_WITNESS" || text == null) return false
        val line = text.trim()
        return line.endsWith('!') && (line.startsWith("Go, ", true) && line.length > 5 || usedMove.matches(line))
    }
}

class CountdownMatchStartInference {
    fun accept(article: RealityArticle): RealityArticle? {
        val at = article.monotonicTimeNanos ?: return null
        if (!BattleStartEvidence.accepts(article.payload, article.sourceId.id, !article.evidenceReferences.isNullOrEmpty())) return null
        return RealityArticle(
            id = ArticleId(UUID.randomUUID().toString()), perceivedAt = article.perceivedAt,
            recordedAt = System.currentTimeMillis(), sourceId = SourceId(BattleStartEvidence.SOURCE),
            payload = MatchStarted, predecessorIds = listOf(article.id),
            matchId = article.matchId, monotonicTimeNanos = at
        )
    }
}
