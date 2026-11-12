package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.TeamLossObservation
import com.example.overdex.battle.inference.TeamLossResolution
import com.example.overdex.battle.inference.TeamLossResolver

/** Reconcile old count-derived conclusions without changing a single archived Article. */
internal class ReplayTeamLossProjection(archive: MatchArchive) {
    private val sourceArticles = archive.articles.filter { it.matchId == archive.matchId }
    private val uniqueSources = sourceArticles.groupBy { it.articleId }
        .mapNotNull { (id, copies) -> copies.distinct().singleOrNull()?.let { id to it } }.toMap()
    private val observationsBySide = listOf("PLAYER", "OPPONENT").associateWith { side ->
        // Keep contradictory copies here: the shared resolver needs them as
        // unknown barriers rather than silently bridging the missing evidence.
        sourceArticles.mapNotNull { article ->
            val at = article.monotonicTimeNanos ?: return@mapNotNull null
            when (val p = article.payload) {
                is ArchivedActivePokemonSpeciesWitnessed -> if (p.side == side)
                    TeamLossObservation.Identity(article.articleId, at, p.speciesName, p.speciesId) else null
                is ArchivedActiveHpBarMeasured -> if (p.side == side && p.filledFraction > 0f && p.filledFraction <= 1f)
                    TeamLossObservation.Alive(article.articleId, at) else null
                is ArchivedPlayerPokeBallCountMeasured -> if (side == "PLAYER" && p.maximumCount == 3)
                    TeamLossObservation.Count(article.articleId, at, p.visibleCount) else null
                is ArchivedOpponentBattleResourceCountMeasured -> if (side == "OPPONENT" &&
                    p.resource == "POKE_BALLS" && p.maximumCount == 3)
                    TeamLossObservation.Count(article.articleId, at, p.visibleCount) else null
                else -> null
            }
        }
    }
    private val reconciledSides = observationsBySide.filterValues { observations ->
        observations.filterIsInstance<TeamLossObservation.Count>().map { it.articleId }.distinct().size >= 2
    }.keys
    val resolutionsByArticleId: Map<String, TeamLossResolution> = buildMap {
        observationsBySide.forEach { (side, observations) ->
            TeamLossResolver.resolve(observations).forEach { resolution ->
                resolution.resolvedCandidates.forEach { candidate ->
                    // One captured 3 -> 1 count can resolve two owners without
                    // supplying either owner's individual faint clock. Keep
                    // their Articles distinct while both cite the same loss.
                    val id = "replay-team-loss:$side:${resolution.lossId}" +
                        if (resolution.beforeCount - resolution.afterCount == 1) "" else
                            ":${candidate.appearanceArticleId}"
                    put(id, resolution.copy(resolved = candidate))
                }
            }
        }
    }
    val articles: List<ArchivedRealityArticle> = archive.articles.filterNot { article ->
        val faint = article.payload as? ArchivedActivePokemonFaintedWitnessed
        article.matchId == archive.matchId && faint != null && faint.side in reconciledSides &&
            ("POKE_BALL_COUNT" in faint.basis || faint.basis == BASIS || positivelyContradicted(article, faint))
    } + resolutionsByArticleId.mapNotNull { (id, resolution) ->
        val candidate = resolution.resolved ?: return@mapNotNull null
        val source = uniqueSources[resolution.lossId] ?: return@mapNotNull null
        val side = if (source.payload is ArchivedPlayerPokeBallCountMeasured) "PLAYER" else "OPPONENT"
        source.copy(
            articleId = id,
            sourceId = "REPLAY_TEAM_LOSS_PROJECTION",
            payload = ArchivedActivePokemonFaintedWitnessed(side, candidate.speciesName, candidate.speciesId, BASIS),
            predecessorIds = emptyList(),
            evidenceReferences = resolution.evidenceReferences,
            monotonicTimeNanos = resolution.sourceAtNanos,
        )
    }

    private fun positivelyContradicted(article: ArchivedRealityArticle, faint: ArchivedActivePokemonFaintedWitnessed): Boolean {
        val at = article.monotonicTimeNanos ?: return false
        val name = faint.speciesName ?: return false
        val observations = observationsBySide[faint.side].orEmpty()
        if (observations.groupBy { it.articleId }.values.any { it.distinct().size > 1 }) return false
        val identityObservations = observations.filterIsInstance<TeamLossObservation.Identity>()
        val namedIdentities = identityObservations.filter {
            it.speciesId != null && TeamLossResolver.speciesKey(it.speciesName).isNotBlank()
        }
        val contradictoryNames = namedIdentities.groupBy { TeamLossResolver.speciesKey(it.speciesName) }
            .filterValues { group -> group.map { it.speciesId }.distinct().size > 1 }.keys
        val contradictoryIds = namedIdentities.groupBy { it.speciesId }
            .filterValues { group -> group.map { TeamLossResolver.speciesKey(it.speciesName) }.distinct().size > 1 }.keys
        val identities = identityObservations.groupBy { it.atNanos }.toSortedMap()
        // An absent count drop is not a contradiction: a final count may have
        // disappeared before capture. Require independent later survival proof.
        return observations.filterIsInstance<TeamLossObservation.Alive>().any { alive ->
            val known = identities.entries.lastOrNull { it.key <= alive.atNanos }?.value.orEmpty()
            alive.atNanos > at && known.mapNotNull { it.speciesId }.distinct().size <= 1 &&
                known.none { TeamLossResolver.speciesKey(it.speciesName) in contradictoryNames ||
                    it.speciesId in contradictoryIds } &&
                known.map { TeamLossResolver.speciesKey(it.speciesName) }.distinct().singleOrNull() ==
                TeamLossResolver.speciesKey(name)
        }
    }

    companion object {
        const val BASIS = "RECONCILED_TEAM_COUNT_LOSS"
    }
}
