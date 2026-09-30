package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.BattleOverlayOpened
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.AudioCaptured
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.VisualCaptureGapObserved
import com.example.overdex.battle.custody.OutOfBattleMenuWitnessed
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.MatchRecordStarted
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.custody.SupportingMatchStart
import com.example.overdex.battle.custody.PlayerInactiveHpBarMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.FastMoveSoundMeasured
import com.example.overdex.battle.custody.PlayerInactiveSpeciesSpriteFingerprintMeasured
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.TeamSelectPartyWitnessed
import com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityArticle

/**
 * Mapper for converting a RealityArticle into an ArchivedRealityArticle and assembling a MatchArchive.
 */
object RealityArticleArchiveMapper {

    fun map(article: RealityArticle): ArchivedRealityArticle {
        val mId = article.matchId ?: throw IllegalArgumentException("RealityArticle ${article.id.value} has a null matchId and cannot be archived.")

        val archivedPayload: ArchivedTestimonyPayload = when (val p = article.payload) {
            is RawTestimony -> {
                when (val data = p.data) {
                    is String -> ArchivedRawText(data)
                    is Int -> ArchivedRawInt(data)
                    else -> throw IllegalArgumentException("Unsupported RawTestimony data type: ${data::class.java.name}")
                }
            }
            is MatchRecordStarted -> ArchivedMatchRecordStarted
            is BattleOverlayOpened -> ArchivedBattleOverlayOpened(p.reason.name)
            is VsScreenWitnessed -> ArchivedVsScreenWitnessed
            is AttackIncoming -> ArchivedAttackIncoming
            is PokemonIdentified -> ArchivedPokemonIdentified(p.species)
            is ActivePokemonSpeciesWitnessed -> ArchivedActivePokemonSpeciesWitnessed(
                side = p.side.name,
                speciesName = p.speciesName,
                speciesId = p.speciesId
            )
            is com.example.overdex.battle.custody.SpeciesCheckMeasured -> ArchivedSpeciesCheckMeasured(
                p.side.name, p.windowId, p.status, p.reason, p.triggerMonotonicNanos,
                p.elapsedNanos, p.targetNanos, p.speciesName
            )
            is TeamSelectPartyWitnessed -> ArchivedTeamSelectPartyWitnessed
            is PlayerTeamRosterSlotWitnessed -> ArchivedPlayerTeamRosterSlotWitnessed(p.slot, p.speciesName, p.speciesId)
            is PlayerTeamSlotConfigured -> ArchivedPlayerTeamSlotConfigured(
                p.slot, p.speciesName, p.speciesId, p.fastMoveName, p.chargedMoveNames
            )
            is SupportingMatchStart -> ArchivedSupportingMatchStart(
                frameIndex = p.frameIndex,
                upperColorfulPixelFraction = p.upperColorfulPixelFraction,
                lowerColorfulPixelFraction = p.lowerColorfulPixelFraction,
                basis = p.basis
            )
            is CountdownGlyphWitnessed -> ArchivedCountdownGlyphWitnessed(
                glyph = p.glyph,
                similarity = p.similarity,
                frameIndex = p.frameIndex,
                cropName = p.cropProvenance?.cropName,
                sourceWidth = p.cropProvenance?.sourceWidth,
                sourceHeight = p.cropProvenance?.sourceHeight,
                cropLeft = p.cropProvenance?.bounds?.left,
                cropTop = p.cropProvenance?.bounds?.top,
                cropRight = p.cropProvenance?.bounds?.right,
                cropBottom = p.cropProvenance?.bounds?.bottom,
                basis = p.basis
            )
            is CropCaptured -> ArchivedCropCaptured(
                artifactPath = p.artifact.relativePath,
                sha256 = p.artifact.sha256,
                byteCount = p.artifact.byteCount,
                mediaType = p.artifact.mediaType,
                cropName = p.cropProvenance.cropName,
                sourceWidth = p.cropProvenance.sourceWidth,
                sourceHeight = p.cropProvenance.sourceHeight,
                cropLeft = p.cropProvenance.bounds.left,
                cropTop = p.cropProvenance.bounds.top,
                cropRight = p.cropProvenance.bounds.right,
                cropBottom = p.cropProvenance.bounds.bottom
            )
            is com.example.overdex.battle.custody.AudioInputStatus -> ArchivedAudioInputStatus(p.captureSource, p.state)
            is AudioCaptured -> ArchivedAudioCaptured(
                artifactPath = p.artifact.relativePath,
                sha256 = p.artifact.sha256,
                byteCount = p.artifact.byteCount,
                mediaType = p.artifact.mediaType,
                sampleRateHz = p.sampleRateHz,
                channelCount = p.channelCount,
                durationNanos = p.durationNanos,
                cueKind = p.cueKind,
                captureSource = p.captureSource,
                peakAmplitude = p.peakAmplitude
            )
            is BattleCryCandidatesMeasured -> ArchivedBattleCryCandidatesMeasured(
                p.cueKind, p.candidates.map { ArchivedBattleCryCandidateMeasurement(it.speciesId, it.referenceSha256, it.similarity) }
            )
            is OutOfBattleMenuWitnessed -> ArchivedOutOfBattleMenuWitnessed
            is VisualCaptureGapObserved -> ArchivedVisualCaptureGapObserved(p.durationNanos)
            is WitnessOperating -> ArchivedWitnessOperating(p.operating)
            is ActivePokemonTypesWitnessed -> ArchivedActivePokemonTypesWitnessed(
                side = p.side.name,
                types = p.types.map { it.name },
                similarity = p.similarity,
                basis = p.basis
            )
            is GetReadyWitnessed -> ArchivedGetReadyWitnessed
            is ChargeMoveUsedAnnounced -> ArchivedChargeMoveUsedAnnounced
            is PlayerInactiveHpBarMeasured -> ArchivedPlayerInactiveHpBarMeasured(
                slot = p.slot,
                filledFraction = p.filledFraction
            )
            is ActiveHpBarMeasured -> ArchivedActiveHpBarMeasured(
                side = p.side.name,
                barLeft = p.barLeft,
                barTop = p.barTop,
                barRight = p.barRight,
                barBottom = p.barBottom,
                filledFraction = p.filledFraction
            )
            is ActiveHpBarMotionCadenceMeasured -> ArchivedActiveHpBarMotionCadenceMeasured(
                movingSide = p.movingSide.name,
                intervalNanos = p.intervalNanos,
                verticalExcursionPixels = p.verticalExcursionPixels,
                sampleCount = p.sampleCount
            )
            is PlayerChargeMoveEnergyFillIncreased -> ArchivedPlayerChargeMoveEnergyFillIncreased(
                beforeBySlot = p.beforeBySlot,
                afterBySlot = p.afterBySlot,
                changedSlots = p.changedSlots
            )
            is PlayerChargeMoveEnergyFillCadenceMeasured -> ArchivedPlayerChargeMoveEnergyFillCadenceMeasured(
                intervalNanos = p.intervalNanos,
                contributingSlots = p.contributingSlots
            )
            is ActiveHpBarBorderCadenceMeasured -> ArchivedActiveHpBarBorderCadenceMeasured(
                damagedBarSide = p.damagedBarSide.name,
                intervalNanos = p.intervalNanos,
                peakColorDistance = p.peakColorDistance,
                sampleCount = p.sampleCount,
                peakOrangeFraction = p.peakOrangeFraction,
                baselineWhiteFraction = p.baselineWhiteFraction
            )
            is ActiveHpBarBorderPulseObserved -> ArchivedActiveHpBarBorderPulseObserved(
                damagedBarSide = p.damagedBarSide.name,
                peakColorDistance = p.peakColorDistance,
                sampleCount = p.sampleCount,
                peakOrangeFraction = p.peakOrangeFraction,
                baselineWhiteFraction = p.baselineWhiteFraction
            )
            is FastMoveRecipientVisualArtifactMeasured -> ArchivedFastMoveRecipientVisualArtifactMeasured(
                damagedSide = p.damagedSide.name,
                changedPixelFraction = p.changedPixelFraction,
                meanColorDistance = p.meanColorDistance,
                meanRed = p.meanRed,
                meanGreen = p.meanGreen,
                meanBlue = p.meanBlue,
                centroidX = p.centroidX,
                centroidY = p.centroidY,
                changedSampleCount = p.changedSampleCount
            )
            is FastMoveRecipientVisualCadenceMeasured -> ArchivedFastMoveRecipientVisualCadenceMeasured(
                damagedSide = p.damagedSide.name,
                intervalNanos = p.intervalNanos
            )
            is FastMoveIdentified -> ArchivedFastMoveIdentified(
                side = p.side.name,
                speciesName = p.speciesName,
                moveName = p.moveName,
                moveDurationNanos = p.moveDurationNanos,
                observedMedianIntervalNanos = p.observedMedianIntervalNanos,
                cadenceSampleCount = p.cadenceSampleCount,
                basis = p.basis
            )
            is FastMoveEnergyDerived -> ArchivedFastMoveEnergyDerived(
                side = p.side.name,
                moveName = p.moveName,
                observedCompletedUses = p.observedCompletedUses,
                energyPerUse = p.energyPerUse,
                totalEnergyGenerated = p.totalEnergyGenerated,
                basis = p.basis
            )
            is ChargedMoveEnergySpent -> ArchivedChargedMoveEnergySpent(
                side = p.side.name,
                speciesName = p.speciesName,
                moveName = p.moveName,
                energyCost = p.energyCost,
                basis = p.basis
            )
            is FastMoveSoundMeasured -> ArchivedFastMoveSoundMeasured(
                audible = p.audible,
                onsetOffsetNanos = p.onsetOffsetNanos,
                soundDurationNanos = p.soundDurationNanos,
                spectralCentroidHz = p.spectralCentroidHz,
                peakAmplitude = p.peakAmplitude
            )
            is PlayerInactiveSpeciesSpriteFingerprintMeasured -> ArchivedPlayerInactiveSpeciesSpriteFingerprintMeasured(
                slot = p.slot,
                fingerprint = p.fingerprint,
                sampleLeft = p.sampleLeft,
                sampleTop = p.sampleTop,
                sampleRight = p.sampleRight,
                sampleBottom = p.sampleBottom
            )
            is MatchStarted -> ArchivedMatchStarted
            is MatchEnded -> ArchivedMatchEnded(p.result.name)
            else -> throw IllegalArgumentException("Unsupported TestimonyPayload type: ${p::class.java.name}")
        }

        return ArchivedRealityArticle(
            articleId = article.id.value,
            matchId = mId.value,
            perceivedAt = article.perceivedAt,
            recordedAt = article.recordedAt,
            sourceId = article.sourceId.id,
            payload = archivedPayload,
            predecessorIds = article.predecessorIds.map { it.value },
            confidence = article.confidence,
            sequenceNumber = article.sequenceNumber,
            evidenceReferences = article.evidenceReferences,
            monotonicTimeNanos = article.monotonicTimeNanos
        )
    }

    fun createArchive(matchId: MatchId, articles: List<RealityArticle>): MatchArchive {
        articles.forEach { article ->
            if (article.matchId != matchId) {
                throw IllegalArgumentException("RealityArticle ${article.id.value} matchId (${article.matchId}) does not match requested matchId ($matchId)")
            }
        }

        val archivedArticles = articles.map { map(it) }
        return MatchArchive(
            schemaVersion = 1,
            matchId = matchId.value,
            articles = archivedArticles
        )
    }
}
