package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle

/** Small source-evidence projection; neither faint conclusions nor HUD state are inputs. */
internal class LiveTeamLossHistory(private val side: ActivePokemonSide,
    private val onRebuild: ((String, Int) -> Unit)? = null,
    private val cache: FaintHistoryCache? = null) {
    private val observations = linkedMapOf<String, TeamLossObservation>()
    private val conflictingIds = mutableSetOf<String>()
    private val conflictingObservations = mutableListOf<TeamLossObservation>()
    private val sources = mutableMapOf<String, RealityArticle>()
    private val hpHistory = LiveVerifiedHpFaintHistory(side, onRebuild, cache)
    private val terminalHistory = LiveTerminalChargedFaintHistory(side, onRebuild, cache)
    private val limits = TeamFaintLimits(side == ActivePokemonSide.PLAYER)
    private var countsChanged = false
    fun faintLimits(): TeamFaintLimits {
        if (countsChanged) {
            limits.updateCounts(observations.values.filterIsInstance<TeamLossObservation.Count>(),
                conflictingObservations.filterIsInstance<TeamLossObservation.Count>().map { it.atNanos }.toSet())
            countsChanged = false
        }
        return limits
    }

    private var attemptFrom = Long.MIN_VALUE
    var resolutions: List<TeamLossResolution> = emptyList()
        private set
    val hpReconciliation get() = hpHistory.reconciliation
    val terminalFaints get() = terminalHistory.resolutions
    fun terminalReturnProof(identityId: String) = terminalHistory.hasIndependentReturnProof(identityId)
    private val candidates get() = resolutions.flatMap { loss ->
        loss.resolvedCandidates.map { it to loss.sourceAtNanos }
    } + hpReconciliation.faints.map { it.owner to it.atNanos } + terminalFaints.map { it.owner to it.atNanos }

    val currentFaints get() = candidates.let { all ->
        if (all.isEmpty()) emptyList() else faintLimits().select(
            all.sortedBy { it.second }.distinctBy { TeamLossResolver.speciesKey(it.first.speciesName) }
        ) { it.second }
    }

    fun source(articleId: String): RealityArticle? = sources[articleId] ?: hpHistory.source(articleId) ?: terminalHistory.source(articleId)

    fun accept(article: RealityArticle): Boolean {
        val at = article.monotonicTimeNanos ?: return false
        if (at < attemptFrom) return false
        when (val reading = article.payload) {
            is PlayerPokeBallCountMeasured -> if (reading.visibleCount == 0) return false
            is OpponentBattleResourceCountMeasured -> if (reading.resource == OpponentBattleResource.POKE_BALLS && reading.visibleCount == 0) return false
            else -> Unit
        }
        val terminalChanged = terminalHistory.accept(article)
        val resultChanged = (article.payload as? MatchEnded)?.let { limits.result(it.result) } == true
        val hpChanged = hpHistory.accept(article) || terminalChanged || resultChanged
        if (article.payload == ObservationRestartedByUser || article.payload == ScreenIgnoredByUser) {
            countsChanged = true
            attemptFrom = at
            observations.clear(); conflictingIds.clear(); conflictingObservations.clear(); sources.clear()
            val changed = resolutions.isNotEmpty()
            resolutions = emptyList()
            return changed || hpChanged
        }
        val id = article.id.value
        val observation = when (val p = article.payload) {
            is ActivePokemonSpeciesWitnessed -> if (p.side == side) {
                TeamLossObservation.Identity(id, at, p.speciesName, p.speciesId,
                    article.sourceId.id == "OPPONENT_SPECIES_OVERLAY_PIPELINE" && p.ocrMatchBasis?.startsWith("EXACT") == true &&
                        article.confidence?.let { it in .80f..1f } == true)
            } else null
            is ActiveHpBarMeasured -> if (p.side == side && p.filledFraction > 0f && p.filledFraction <= 1f) {
                TeamLossObservation.Alive(id, at,
                        article.sourceId.id == "OPPONENT_ACTIVE_HP_BAR_WITNESS" && p.fillColor in setOf("GREEN", "YELLOW", "RED") && article.confidence?.let { it in .80f..1f } == true)
            } else null
            is PlayerPokeBallCountMeasured -> if (side == ActivePokemonSide.PLAYER && p.maximumCount == 3) {
                TeamLossObservation.Count(id, at, p.visibleCount)
            } else null
            is OpponentBattleResourceCountMeasured -> if (side == ActivePokemonSide.OPPONENT &&
                p.resource == OpponentBattleResource.POKE_BALLS && p.maximumCount == 3) {
                TeamLossObservation.Count(id, at, p.visibleCount)
            } else null
            else -> null
        } ?: return hpChanged
        if (id in conflictingIds || observations[id] == observation) return hpChanged
        val wasCount = observations[id] is TeamLossObservation.Count
        if (observations.containsKey(id)) {
            conflictingIds += id
            conflictingObservations += requireNotNull(observations.remove(id))
            conflictingObservations += observation
            sources.remove(id)
        } else {
            observations[id] = observation
            sources[id] = article
            // Retain survival evidence for a future count, but HP alone cannot
            // create a team loss. Avoid rebuilding history every frame beforehand.
            if (observation is TeamLossObservation.Alive && resolutions.isEmpty()) return true
        }
        val input = observations.values.toList() + conflictingObservations
        if (wasCount || observation is TeamLossObservation.Count) countsChanged = true
        val compute = {
            onRebuild?.invoke("team_loss_${side.name}", input.size)
            TeamLossResolver.resolve(input)
        }
        resolutions = cache?.team(side, input, compute) ?: compute()
        return true
    }
}
