package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import com.example.overdex.model.BattleResult

/** Runs the live decision over original source evidence. Original archive entries are immutable. */
internal class ReplayTerminalChargedFaintProjection(archive: MatchArchive, projected: List<ArchivedRealityArticle>) {
    private val source = archive.articles.filter { it.matchId == archive.matchId }
    private val unique = source.groupBy { it.articleId }.mapNotNull { (id, copies) ->
        copies.distinct().singleOrNull()?.let { id to it }
    }.toMap()
    private val existing = projected.filterNot {
        (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.basis == TerminalChargedFaintResolver.BASIS
    }
    val resolutionsByArticleId = TerminalChargedFaintResolver.resolve(source.mapNotNull { it.terminalFinishRecord() })
        .filterNot { loss -> existing.any { a ->
            (a.payload as? ArchivedActivePokemonFaintedWitnessed)?.let {
                it.side == loss.side.name && it.speciesName?.let(TeamLossResolver::speciesKey) ==
                    TeamLossResolver.speciesKey(loss.owner.speciesName) } == true
        } }.associateBy { "replay-terminal-charge:${it.side}:${it.sourceArticleId}:${it.atNanos}" }

    val articles = existing + resolutionsByArticleId.mapNotNull { (id, loss) ->
        val original = unique[loss.sourceArticleId] ?: return@mapNotNull null
        val sample = (original.payload as? ArchivedTerminalParticleObservations)?.samples
            ?.singleOrNull { it.sourceAtNanos == loss.atNanos } ?: return@mapNotNull null
        original.copy(articleId = id, sourceId = "REPLAY_TERMINAL_CHARGED_FAINT_PROJECTION",
            perceivedAt = sample.wallTimeMillis, monotonicTimeNanos = sample.sourceAtNanos,
            payload = ArchivedActivePokemonFaintedWitnessed(loss.side.name, loss.owner.speciesName,
                loss.owner.speciesId, TerminalChargedFaintResolver.BASIS),
            predecessorIds = emptyList(), evidenceReferences = loss.evidenceReferences)
    }
}

internal fun ArchivedRealityArticle.terminalFinishRecord(): TerminalFinishRecord? {
    val at = monotonicTimeNanos ?: return null
    val p = runCatching<TestimonyPayload?> {
        when (val p = payload) {
            is ArchivedTerminalParticleObservations -> TerminalParticleObservations(ActivePokemonSide.valueOf(p.requestedRecipient),
                p.phaseAtNanos, p.sourceDeadlineNanos, p.samples, p.ended, p.totalSamples)
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
    }.getOrElse { ScreenIgnoredByUser } ?: return null
    if (!TerminalChargedFaintResolver.relevant(p, sourceId)) return null
    return TerminalFinishRecord(articleId, at, sourceId, confidence, p)
}
