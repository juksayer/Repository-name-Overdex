package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import com.example.overdex.model.BattleResult

/** Runs the live decision over original source evidence. Original archive entries are immutable. */
internal class ReplayTerminalChargedFaintProjection(archive: MatchArchive, projected: List<ArchivedRealityArticle>,
    recoveredCries: List<ReplayRecoveredCry> = emptyList()) {
    private val source = archive.articles.filter { it.matchId == archive.matchId }
    private val unique = source.groupBy { it.articleId }.mapNotNull { (id, copies) ->
        copies.distinct().singleOrNull()?.let { id to it }
    }.toMap()
    private val existing = projected.filterNot {
        (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.basis == TerminalChargedFaintResolver.BASIS
    }
    private val records = source.mapNotNull { it.terminalFinishRecord() }
    // Match reviewed audio back to its original clip. Prefer the live onset when both
    // readers localized the same occurrence. Both paths retain their source clocks.
    private val audioRecords = recoveredCries.distinct().mapNotNull { cry ->
        if (records.any { (it.payload as? BattleCryLocalized)?.speciesId == cry.speciesId &&
                kotlin.math.abs(it.at - cry.estimatedOnsetNanos) < 100_000_000L }) return@mapNotNull null
        val fragment = cry.supportedFragment(unique) ?: return@mapNotNull null
        TerminalFinishRecord(fragment.articleId, cry.estimatedOnsetNanos, "LOCALIZED_BATTLE_CRY_WITNESS",
            fragment.correlation, BattleCryLocalized(cry.speciesId, fragment.referenceSha256,
                fragment.correlation, fragment.durationNanos, "replay-reviewed-waveform"),
            cryMatchedThroughNanos = fragment.captureAtNanos + fragment.queryOffsetNanos + fragment.durationNanos)
    }
    val resolutionsByArticleId = (DepartureCryFaint.resolve(records + audioRecords) + TerminalChargedFaintResolver.resolve(records))
        .distinctBy { it.side to TeamLossResolver.speciesKey(it.owner.speciesName) }
        .filterNot { loss -> existing.any { a ->
            (a.payload as? ArchivedActivePokemonFaintedWitnessed)?.let {
                it.side == loss.side.name && it.speciesName?.let(TeamLossResolver::speciesKey) ==
                    TeamLossResolver.speciesKey(loss.owner.speciesName) } == true
        } }.associateBy { "replay-terminal-charge:${it.side}:${it.sourceArticleId}:${it.atNanos}" }

    val articles = existing + resolutionsByArticleId.mapNotNull { (id, loss) ->
        val original = unique[loss.sourceArticleId] ?: return@mapNotNull null
        val sample = (original.payload as? ArchivedTerminalParticleObservations)?.samples
            ?.singleOrNull { it.sourceAtNanos == loss.atNanos }
        if (sample == null && original.payload !is ArchivedBattleCryLocalized && audioRecords.none { it.id == original.articleId }) return@mapNotNull null
        original.copy(articleId = id, sourceId = "REPLAY_TERMINAL_CHARGED_FAINT_PROJECTION",
            perceivedAt = sample?.wallTimeMillis ?: (original.perceivedAt + (loss.atNanos - original.monotonicTimeNanos!!) / 1_000_000L), monotonicTimeNanos = loss.atNanos,
            payload = ArchivedActivePokemonFaintedWitnessed(loss.side.name, loss.owner.speciesName,
                loss.owner.speciesId, loss.basis),
            predecessorIds = emptyList(), evidenceReferences = loss.evidenceReferences)
    }
}

internal fun ArchivedRealityArticle.terminalFinishRecord(): TerminalFinishRecord? {
    val at = monotonicTimeNanos ?: return null
    val p = terminalPayload() ?: return null
    if (!TerminalChargedFaintResolver.relevant(p, sourceId)) return null
    return TerminalFinishRecord(articleId, at, sourceId, confidence, p)
}

internal fun ArchivedRealityArticle.terminalPayload(): TestimonyPayload? =
    runCatching<TestimonyPayload?> {
        when (val p = payload) {
            is ArchivedPlayerTeamRosterConfirmation -> PlayerTeamRosterConfirmation(p.confirmed, List(3) { p.speciesBySlot.getOrNull(it) })
            is ArchivedPlayerTeamRosterSlotWitnessed -> if (p.slot in 1..3)
                PlayerTeamRosterSlotWitnessed(p.slot, p.speciesName, p.speciesId) else null
            is ArchivedPlayerTeamSlotConfigured -> runCatching {
                PlayerTeamSlotConfigured(p.slot, p.speciesName, p.speciesId, p.fastMoveName, p.chargedMoveNames, p.ownedPokemon)
            }.getOrNull()
            is ArchivedTerminalParticleObservations -> TerminalParticleObservations(ActivePokemonSide.valueOf(p.requestedRecipient),
                p.phaseAtNanos, p.sourceDeadlineNanos, p.samples, p.ended, p.totalSamples)
            is ArchivedBattleCryLocalized -> BattleCryLocalized(p.speciesId, p.referenceSha256, p.correlation, p.durationNanos, p.analysisVersion)
            is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(ActivePokemonSide.valueOf(p.side),p.speciesName,p.speciesId,p.basis)
            is ArchivedCompleteHpBarObserved -> CompleteHpBarObserved(ActivePokemonSide.valueOf(p.side),
                p.barLeft, p.barTop, p.barRight, p.barBottom, p.sourceWidth, p.sourceHeight,
                p.interiorColumns, p.liveColumns, p.damageColumns, p.status, p.candidateStartedAtNanos,
                p.appearanceStartedAtNanos, p.railBrightness, p.backgroundBrightness,
                p.backgroundMaskedColumns, p.unmaskedUncertainColumns)
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(ActivePokemonSide.valueOf(p.side),
                p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
            is ArchivedActiveHpBarMeasured -> ActiveHpBarMeasured(ActivePokemonSide.valueOf(p.side),
                p.barLeft, p.barTop, p.barRight, p.barBottom, p.filledFraction, p.sourceWidth, p.sourceHeight, p.fillColor)
            is ArchivedPlayerPokeBallCountMeasured -> PlayerPokeBallCountMeasured(p.visibleCount, p.maximumCount)
            is ArchivedOpponentBattleResourceCountMeasured -> OpponentBattleResourceCountMeasured(
                OpponentBattleResource.valueOf(p.resource), p.visibleCount, p.maximumCount)
            is ArchivedChargedMoveEnergySpent -> ChargedMoveEnergySpent(ActivePokemonSide.valueOf(p.side),
                p.speciesName, p.moveName, p.energyCost, p.basis)
            is ArchivedFastMoveUseObserved -> FastMoveUseObserved(p.useId,ActivePokemonSide.valueOf(p.attackingSide),
                ActivePokemonSide.valueOf(p.damagedSide),p.appearanceId,p.attackerSpeciesName,p.evidenceKinds,p.basis)
            is ArchivedSpeciesCheckMeasured -> SpeciesCheckMeasured(ActivePokemonSide.valueOf(p.side),
                p.windowId, p.status, p.reason, p.triggerMonotonicNanos, p.elapsedNanos, p.targetNanos, p.speciesName)
            is ArchivedMatchOutcomeLabelWitnessed -> MatchOutcomeLabelWitnessed(BattleResult.valueOf(p.result),
                p.rawText, p.basis, p.ocr, p.supportingFrameCount)
            is ArchivedRawText -> RawTestimony(p.value, p.ocr)
            is ArchivedMatchEnded -> MatchEnded(BattleResult.valueOf(p.result))
            ArchivedMatchStarted -> MatchStarted
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            ArchivedObservationRestartedByUser -> ObservationRestartedByUser
            ArchivedScreenIgnoredByUser -> ScreenIgnoredByUser
            else -> null
        }
    }.getOrElse { ScreenIgnoredByUser }
