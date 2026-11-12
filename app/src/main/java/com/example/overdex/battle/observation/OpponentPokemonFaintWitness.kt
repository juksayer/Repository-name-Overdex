package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.inference.VerifiedHpFaintResolver
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Publishes named losses resolved from source-time team or verified HP history. Count
 * measurements already preserve unresolved losses; an entry alone is not a KO.
 * Presentation re-evaluates that same history as later evidence arrives.
 */
class PokemonFaintWitness(
    private val side: ActivePokemonSide = ActivePokemonSide.OPPONENT,
    override val observerId: ObserverId = ObserverId(
        "${side.name}_POKEMON_FAINT_WITNESS",
        ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "${side.name} Pokémon Faint"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        val tracker = TeamFaintTracker(side)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    tracker.acceptAll(article).forEach { faint ->
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActivePokemonFaintedWitnessed(
                                side = side,
                                speciesName = faint.speciesName,
                                speciesId = faint.speciesId,
                                basis = faint.basis
                            ),
                            timestamp = faint.sourceArticle.perceivedAt,
                            confidence = if (faint.cryCorroborated) 0.99f else 0.96f,
                            evidenceReferences = faint.evidenceReferences,
                            monotonicTimeNanos = requireNotNull(faint.sourceArticle.monotonicTimeNanos)
                        )
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("${side.name} faint witness stopped")
        scope = null
    }
}

internal data class TeamFaintDecision(
    val speciesName: String,
    val speciesId: Int?,
    val evidenceReferences: List<String>,
    val cryCorroborated: Boolean,
    val basis: String,
    /** The source count/empty frame's capture clock survives delayed recognition. */
    val sourceArticle: RealityArticle,
)

/** Live adapter for the same source-time resolver used by completed Replay. */
internal class TeamFaintTracker(private val side: ActivePokemonSide = ActivePokemonSide.OPPONENT) {
    private val history = com.example.overdex.battle.inference.LiveTeamLossHistory(side)
    private val deliveredOwners = mutableSetOf<Pair<String, String>>()
    private val cries = linkedMapOf<String, RealityArticle>()

    val resolutions get() = history.resolutions

    fun accept(article: RealityArticle): TeamFaintDecision? = acceptAll(article).firstOrNull()

    fun acceptAll(article: RealityArticle): List<TeamFaintDecision> {
        (article.payload as? BattleCryCandidatesMeasured)?.takeIf {
            it.candidates.isNotEmpty() && it.cueKind != "FAST_MOVE_IMPACT"
        }?.let { cries[article.id.value] = article }
        if (!history.accept(article)) return emptyList()
        val countDecisions = history.resolutions.flatMap { loss ->
            val source = history.source(loss.lossId) ?: return@flatMap emptyList()
            val aggregate = loss.beforeCount - loss.afterCount > 1
            loss.resolvedCandidates.mapNotNull { candidate ->
                val owner = loss.lossId to TeamLossResolver.speciesKey(candidate.speciesName)
                if (owner in deliveredOwners) return@mapNotNull null
                // An aggregate confirms losses over an interval. A cry near its
                // final count cannot date or corroborate every earlier departure.
                val cry = if (aggregate) null else cries.values.filter { evidence ->
                    val at = evidence.monotonicTimeNanos ?: return@filter false
                    loss.sourceAtNanos - at in 0..CRY_CORROBORATION_WINDOW_NANOS &&
                        (evidence.payload as BattleCryCandidatesMeasured).candidates.any {
                            it.speciesId == candidate.speciesId
                        }
                }.maxByOrNull { it.monotonicTimeNanos!! }
                deliveredOwners += owner
                TeamFaintDecision(
                    speciesName = candidate.speciesName,
                    speciesId = candidate.speciesId,
                    evidenceReferences = (loss.evidenceReferences + listOfNotNull(cry?.id?.value)).distinct(),
                    cryCorroborated = cry != null,
                    basis = "${side.name}_POKE_BALL_COUNT_DECREASE" + when {
                        aggregate -> "_AGGREGATE"
                        cry != null -> "_WITH_BATTLE_CRY"
                        else -> ""
                    },
                    sourceArticle = source,
                )
            }
        }
        val hpDecisions = history.hpReconciliation.faints.mapNotNull { loss ->
            val key = TeamLossResolver.speciesKey(loss.owner.speciesName)
            if (history.resolutions.any { count -> count.resolvedCandidates.any {
                    TeamLossResolver.speciesKey(it.speciesName) == key } }) return@mapNotNull null
            val source = history.source(loss.emptyArticleId) ?: return@mapNotNull null
            if (!deliveredOwners.add(loss.emptyArticleId to key)) return@mapNotNull null
            TeamFaintDecision(loss.owner.speciesName, loss.owner.speciesId,
                loss.evidenceReferences, false, VerifiedHpFaintResolver.BASIS, source)
        }
        // A later independent count is still archived, and can publish its own
        // corroborated conclusion. Consumers reconcile by actor, never event tally.
        val terminalDecisions = history.terminalFaints.mapNotNull { loss ->
            val key = TeamLossResolver.speciesKey(loss.owner.speciesName)
            if (history.resolutions.any { count -> count.resolvedCandidates.any {
                    TeamLossResolver.speciesKey(it.speciesName) == key } } ||
                history.hpReconciliation.faints.any { TeamLossResolver.speciesKey(it.owner.speciesName) == key }) return@mapNotNull null
            val source = history.source(loss.sourceArticleId) ?: return@mapNotNull null
            val sample = (source.payload as? com.example.overdex.battle.custody.TerminalParticleObservations)
                ?.samples?.singleOrNull { it.sourceAtNanos == loss.atNanos } ?: return@mapNotNull null
            if (!deliveredOwners.add(loss.sourceArticleId to key)) return@mapNotNull null
            TeamFaintDecision(loss.owner.speciesName, loss.owner.speciesId, loss.evidenceReferences, false,
                com.example.overdex.battle.inference.TerminalChargedFaintResolver.BASIS,
                source.copy(perceivedAt = sample.wallTimeMillis, monotonicTimeNanos = sample.sourceAtNanos))
        }
        return countDecisions + hpDecisions + terminalDecisions
    }

    private companion object {
        const val CRY_CORROBORATION_WINDOW_NANOS = 5_000_000_000L
    }
}
