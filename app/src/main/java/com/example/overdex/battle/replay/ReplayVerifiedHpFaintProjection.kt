package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.inference.VerifiedHpFaintObservation
import com.example.overdex.battle.inference.VerifiedHpFaintResolution
import com.example.overdex.battle.inference.VerifiedHpFaintResolver
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource

/** Completed-history reconstruction only; no inferred zero, count, move or result is archived. */
internal class ReplayVerifiedHpFaintProjection(
    archive: MatchArchive,
    countProjectedArticles: List<ArchivedRealityArticle>,
) {
    private val sources = archive.articles.filter { it.matchId == archive.matchId }
    private val uniqueSources = sources.groupBy { it.articleId }
        .mapNotNull { (id, copies) -> copies.distinct().singleOrNull()?.let { id to it } }.toMap()
    private val start = sources.filter { it.payload is ArchivedMatchStarted }
        .mapNotNull { it.monotonicTimeNanos }.minOrNull() ?: Long.MIN_VALUE
    private val end = sources.filter { it.payload is ArchivedMatchEnded }
        .mapNotNull { it.monotonicTimeNanos }.minOrNull() ?: Long.MAX_VALUE
    private val existing = countProjectedArticles.filterNot {
        com.example.overdex.battle.inference.isRevisableVisualFaint(
            (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.basis)
    }

    private val reconciliations = listOf("PLAYER", "OPPONENT").associateWith { side ->
        val observations = sources.mapNotNull { article ->
            val at = article.monotonicTimeNanos ?: return@mapNotNull null
            if (at > end) return@mapNotNull null
            val id = article.articleId
            if (id !in uniqueSources) return@mapNotNull VerifiedHpFaintObservation.Boundary(id, at)
            when (val p = article.payload) {
                is ArchivedActivePokemonSpeciesWitnessed -> if (p.side != side) null
                    else if (article.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS")
                        VerifiedHpFaintObservation.Boundary(id, at)
                    else VerifiedHpFaintObservation.Identity(id, at, p.speciesName, p.speciesId,
                        isSideLocatedSpeciesSource(article.sourceId) &&
                            article.confidence?.let { it.isFinite() && it in .80f..1f } == true)
                is ArchivedActiveHpBarMeasured -> if (p.side == side && at >= start) {
                    val normal = article.sourceId == "${side}_ACTIVE_HP_BAR_WITNESS"
                    val charged = article.sourceId == "CHARGED_IMPACT_${side}_HP_BAR_FRAME_WITNESS"
                    if (normal || charged) VerifiedHpFaintObservation.Hp(id, at,
                        p.barLeft, p.barTop, p.barRight, p.barBottom, p.filledFraction, p.fillColor,
                        p.sourceWidth, p.sourceHeight, article.confidence, normal)
                    else VerifiedHpFaintObservation.Boundary(id, at, clearIdentity = false)
                } else null
                is ArchivedSpeciesCheckMeasured -> if (p.side == side && p.status == "OPENED" &&
                    p.reason == "SWITCH") VerifiedHpFaintObservation.Boundary(id, p.triggerMonotonicNanos) else null
                ArchivedGetReadyWitnessed, ArchivedAttackIncoming, ArchivedChargeMoveUsedAnnounced ->
                    VerifiedHpFaintObservation.Boundary(id, at, clearIdentity = false)
                is ArchivedMatchEnded -> VerifiedHpFaintObservation.Boundary(id, at)
                ArchivedScreenIgnoredByUser, ArchivedObservationRestartedByUser ->
                    VerifiedHpFaintObservation.Boundary(id, at, discardAttempt = true)
                else -> null
            }
        }
        VerifiedHpFaintResolver.reconcile(observations)
    }

    val contradictedEmptyEvidence = reconciliations.values.flatMap { it.contradictedEmptyEvidence.entries }
        .associate { it.toPair() }

    // A count can corroborate an earlier HP-confirmed loss. Retain that timing
    // even when no additional synthetic faint article is needed.
    val corroboratingFaintsBySide = reconciliations.mapValues { it.value.faints }

    val resolutionsByArticleId: Map<String, VerifiedHpFaintResolution> = buildMap {
        reconciliations.forEach { (side, reconciliation) ->
            reconciliation.faints.forEach { resolution ->
                // Independent count/explicit faints already have their own track.
                // HP proof must not introduce a second KO marker or a second loss.
                val alreadyKnown = existing.any { article ->
                    val p = article.payload as? ArchivedActivePokemonFaintedWitnessed
                    article.matchId == archive.matchId && p?.side == side && p.speciesName?.let {
                        TeamLossResolver.speciesKey(it) == TeamLossResolver.speciesKey(resolution.owner.speciesName)
                    } == true
                }
                if (!alreadyKnown) put("replay-hp-faint:$side:${resolution.emptyArticleId}", resolution)
            }
        }
    }

    val articles = existing + resolutionsByArticleId.mapNotNull { (id, resolution) ->
        val source = uniqueSources[resolution.emptyArticleId] ?: return@mapNotNull null
        val side = (source.payload as ArchivedActiveHpBarMeasured).side
        source.copy(articleId = id, sourceId = "REPLAY_VERIFIED_HP_FAINT_PROJECTION",
            payload = ArchivedActivePokemonFaintedWitnessed(side, resolution.owner.speciesName,
                resolution.owner.speciesId, VerifiedHpFaintResolver.BASIS),
            predecessorIds = emptyList(), evidenceReferences = resolution.evidenceReferences)
    }
}
