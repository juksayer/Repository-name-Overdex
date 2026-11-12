package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.reality.RealityArticle

/** Count and verified-HP exclusions are revisable source-history conclusions. */
internal class FaintedCombatants(private val onRebuild: ((String, Int) -> Unit)? = null,
    private val cache: FaintHistoryCache? = null) {
    private val legacyFaintTimes = mutableMapOf<Pair<ActivePokemonSide, String>, Long>()
    private val histories = ActivePokemonSide.entries.associateWith { LiveTeamLossHistory(it, onRebuild, cache) }.toMutableMap()
    private val resolvedFaintTimes = mutableMapOf<Pair<ActivePokemonSide, String>, Long>()
    private val latestSpecies = mutableMapOf<ActivePokemonSide, RealityArticle>()
    private val latestPositiveHp = mutableMapOf<ActivePokemonSide, Long>()
    private val pendingRecoveries = mutableSetOf<Pair<ActivePokemonSide, String>>()
    private var attemptFromNanos = Long.MIN_VALUE

    /** Return original, previously blocked identities once independent HP supports their survival. */
    fun observe(article: RealityArticle): List<RealityArticle> {
        val at = article.monotonicTimeNanos ?: return emptyList()
        if (at < attemptFromNanos) return emptyList()
        if (article.payload == ObservationRestartedByUser || article.payload == ScreenIgnoredByUser) {
            attemptFromNanos = at
            legacyFaintTimes.clear(); resolvedFaintTimes.clear(); latestSpecies.clear()
            latestPositiveHp.clear(); pendingRecoveries.clear()
            ActivePokemonSide.entries.forEach { histories[it] = LiveTeamLossHistory(it, onRebuild, cache) }
            return emptyList()
        }
        (article.payload as? ActivePokemonSpeciesWitnessed)?.let { species ->
            if (at >= (latestSpecies[species.side]?.monotonicTimeNanos ?: Long.MIN_VALUE))
                latestSpecies[species.side] = article
        }
        (article.payload as? ActiveHpBarMeasured)?.takeIf { it.filledFraction > 0f }?.let { hp ->
            latestPositiveHp[hp.side] = maxOf(at, latestPositiveHp[hp.side] ?: Long.MIN_VALUE)
        }
        val faint = article.payload as? ActivePokemonFaintedWitnessed
        if (faint != null && !isRevisable(faint)) {
            faint.speciesName?.let { name ->
                val key = faint.side to key(faint.side, name)
                legacyFaintTimes[key] = minOf(at, legacyFaintTimes[key] ?: at)
            }
        }
        for ((side, history) in histories) if (history.accept(article)) {
            val previous = resolvedFaintTimes.keys.filter { it.first == side }.toSet()
            resolvedFaintTimes.keys.removeAll { it.first == side }
            history.currentFaints.forEach { (candidate, at) ->
                val key = side to TeamLossResolver.speciesKey(candidate.speciesName)
                resolvedFaintTimes[key] = minOf(at, resolvedFaintTimes[key] ?: Long.MAX_VALUE)
            }
            pendingRecoveries += previous - resolvedFaintTimes.keys
        }
        return latestSpecies.mapNotNull { (side, identity) ->
            val species = identity.payload as ActivePokemonSpeciesWitnessed
            val key = side to TeamLossResolver.speciesKey(species.speciesName)
            if (key !in pendingRecoveries || contains(side, species.speciesName) ||
                (latestPositiveHp[side] ?: Long.MIN_VALUE) < requireNotNull(identity.monotonicTimeNanos) ||
                !hasIndependentReturnProof(side, identity)) null
            else {
                pendingRecoveries.remove(key)
                identity.takeUnless { it.id == article.id }
            }
        }
    }

    private fun hasIndependentReturnProof(side: ActivePokemonSide, identity: RealityArticle): Boolean {
        val history = histories.getValue(side)
        if (history.terminalReturnProof(identity.id.value)) return true
        if (history.hpReconciliation.contradictedEmptyEvidence.values.any { references ->
            identity.id.value in references && references.any { reference ->
                val source = history.source(reference)
                val hp = source?.payload as? ActiveHpBarMeasured
                hp?.side == side && hp.filledFraction > 0f &&
                    requireNotNull(source.monotonicTimeNanos) >= requireNotNull(identity.monotonicTimeNanos)
            }
        }) return true
        return history.resolutions.any { loss ->
            // The resolver's cited survival proof also excludes ambiguous/conflicting identities.
            loss.afterCount > 0 && identity.id.value in loss.evidenceReferences &&
                loss.evidenceReferences.any { reference ->
                    val source = history.source(reference)
                    val hp = source?.payload as? ActiveHpBarMeasured
                    hp != null && hp.side == side && hp.filledFraction > 0f &&
                        source?.monotonicTimeNanos?.let { it >= requireNotNull(identity.monotonicTimeNanos) } == true
                }
        }
    }

    fun contains(side: ActivePokemonSide, name: String) =
        legacyFaintTimes.containsKey(side to key(side, name)) ||
            resolvedFaintTimes.containsKey(side to TeamLossResolver.speciesKey(name))
    fun at(side: ActivePokemonSide, name: String, time: Long): Boolean =
        listOfNotNull(legacyFaintTimes[side to key(side, name)],
            resolvedFaintTimes[side to TeamLossResolver.speciesKey(name)])
            .minOrNull()?.let { time >= it } == true

    /** Old named count conclusions cannot close a current actor unless raw evidence still supports them. */
    fun supports(faint: ActivePokemonFaintedWitnessed, at: Long): Boolean =
        !isRevisable(faint) || faint.speciesName?.let { this.at(faint.side, it, at) } == true

    fun hpFaintTimes(): Map<ActivePokemonSide, Set<Long>> = histories.mapValues { (_, history) ->
        (history.hpReconciliation.faints.map { it.atNanos } + history.terminalFaints.map { it.atNanos }).toSet()
    }

    private fun isRevisable(faint: ActivePokemonFaintedWitnessed) =
        isRevisableVisualFaint(faint.basis) || faint.basis.contains("BALL_COUNT_DECREASE", ignoreCase = true)

    private fun key(side: ActivePokemonSide, name: String) = if (side == ActivePokemonSide.PLAYER)
        name.uppercase().filter(Char::isLetterOrDigit) else TeamRosterSpeciesAttributor.speciesKey(name)
}
