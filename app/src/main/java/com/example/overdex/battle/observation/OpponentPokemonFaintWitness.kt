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
import com.example.overdex.battle.inference.LiveTeamLossHistory
import com.example.overdex.battle.custody.TestimonyCustody

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
    private var attached: MatchFaintSignals? = null

    override fun start(match: Match) {
        if (attached === match.faintSignals) return
        stop()
        match.faintSignals.attach(side, this, SourceId(observerId.id))
        attached = match.faintSignals
    }

    override fun stop() {
        attached?.detach(side, this)
        attached = null
    }
}

/** Each side has one producer and a single fixed destination, the match's evidence ledger.
 * HUD and Replay can read the recorded facts without consuming or repeating a signal. */
internal class MatchFaintSignals(
    private val custody: TestimonyCustody,
    opponentHistory: LiveTeamLossHistory = LiveTeamLossHistory(ActivePokemonSide.OPPONENT),
    private val history: () -> List<RealityArticle>,
) {
    private data class Producer(val owner: PokemonFaintWitness, val source: SourceId)
    private val producers = mutableMapOf<ActivePokemonSide, Producer>()
    private val opponent = TeamFaintTracker(ActivePokemonSide.OPPONENT, opponentHistory)
    private val trackers = mutableMapOf(ActivePokemonSide.OPPONENT to opponent)
    private val attachedBefore = mutableSetOf<ActivePokemonSide>()
    private var opponentChanged = false

    @Synchronized fun takeOpponentHudUpdate(): Set<String>? {
        if (!opponentChanged) return null
        opponentChanged = false
        return opponent.currentFaintNames()
    }

    @Synchronized fun attach(side: ActivePokemonSide, owner: PokemonFaintWitness, source: SourceId) {
        check(side !in producers) { "A faint witness is already active for ${side.name}" }
        val producer = Producer(owner, source)
        val tracker = trackers.getOrPut(side) { TeamFaintTracker(side) }
        // Restore all facts first. An old prefix must not announce a faint already
        // contradicted later in the ledger. The tracker retains its budget across reattachment.
        if (attachedBefore.add(side)) history().forEach(tracker::observe)
        producers[side] = producer
        publish(side, producer, tracker)
    }

    @Synchronized fun detach(side: ActivePokemonSide, owner: PokemonFaintWitness) {
        if (producers[side]?.owner === owner) producers.remove(side)
    }

    @Synchronized fun observe(article: RealityArticle) {
        for ((side, tracker) in trackers) {
            if (!tracker.observe(article)) continue
            if (side == ActivePokemonSide.OPPONENT) opponentChanged = true
            producers[side]?.let { publish(side, it, tracker) }
        }
    }

    private fun publish(side: ActivePokemonSide, producer: Producer, tracker: TeamFaintTracker) {
        for (faint in tracker.decisions()) {
            custody.submitTestimony(producer.source,
                ActivePokemonFaintedWitnessed(side, faint.speciesName, faint.speciesId, faint.basis),
                faint.sourceArticle.perceivedAt, if (faint.cryCorroborated) 0.99f else 0.96f,
                faint.evidenceReferences, requireNotNull(faint.sourceArticle.monotonicTimeNanos))
        }
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
internal class TeamFaintTracker(
    private val side: ActivePokemonSide = ActivePokemonSide.OPPONENT,
    private val history: LiveTeamLossHistory = LiveTeamLossHistory(side),
) {
    fun currentFaintNames() = history.currentFaints.map { it.first.speciesName }.toSet()
    private val deliveredOwners = mutableMapOf<String, Long>()
    private val countSlots = mutableMapOf<Int, String>()
    private val cries = linkedMapOf<String, RealityArticle>()

    val resolutions get() = history.resolutions

    fun accept(article: RealityArticle): TeamFaintDecision? = acceptAll(article).firstOrNull()

    fun acceptAll(article: RealityArticle): List<TeamFaintDecision> =
        if (observe(article)) decisions() else emptyList()

    fun observe(article: RealityArticle): Boolean {
        (article.payload as? BattleCryCandidatesMeasured)?.takeIf {
            it.candidates.isNotEmpty() && it.cueKind != "FAST_MOVE_IMPACT"
        }?.let { cries[article.id.value] = article }
        return history.accept(article)
    }

    fun decisions(): List<TeamFaintDecision> {
        val countDecisions = history.resolutions.flatMap { loss ->
            val source = history.source(loss.lossId) ?: return@flatMap emptyList()
            val aggregate = loss.beforeCount - loss.afterCount > 1
            loss.resolvedCandidates.mapNotNull { candidate ->
                val key = TeamLossResolver.speciesKey(candidate.speciesName)
                val slots = (3 - loss.beforeCount) until (3 - loss.afterCount)
                if (slots.any { countSlots[it] == key }) return@mapNotNull null
                val slot = slots.firstOrNull { it !in countSlots } ?: return@mapNotNull null
                if (key in deliveredOwners) {
                    countSlots[slot] = key
                    return@mapNotNull null
                }
                if (!reserve(key, loss.sourceAtNanos)) return@mapNotNull null
                countSlots[slot] = key
                // An aggregate confirms losses over an interval. A cry near its
                // final count cannot date or corroborate every earlier departure.
                val cry = if (aggregate) null else cries.values.filter { evidence ->
                    val at = evidence.monotonicTimeNanos ?: return@filter false
                    loss.sourceAtNanos - at in 0..CRY_CORROBORATION_WINDOW_NANOS &&
                        (evidence.payload as BattleCryCandidatesMeasured).candidates.any {
                            it.speciesId == candidate.speciesId
                        }
                }.maxByOrNull { it.monotonicTimeNanos!! }
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
            if (!reserve(key, loss.atNanos)) return@mapNotNull null
            TeamFaintDecision(loss.owner.speciesName, loss.owner.speciesId,
                loss.evidenceReferences, false, VerifiedHpFaintResolver.BASIS, source)
        }
        val terminalDecisions = history.terminalFaints.mapNotNull { loss ->
            val key = TeamLossResolver.speciesKey(loss.owner.speciesName)
            if (history.resolutions.any { count -> count.resolvedCandidates.any {
                    TeamLossResolver.speciesKey(it.speciesName) == key } } ||
                history.hpReconciliation.faints.any { TeamLossResolver.speciesKey(it.owner.speciesName) == key }) return@mapNotNull null
            val source = history.source(loss.sourceArticleId) ?: return@mapNotNull null
            val sample = (source.payload as? com.example.overdex.battle.custody.TerminalParticleObservations)
                ?.samples?.singleOrNull { it.sourceAtNanos == loss.atNanos } ?: return@mapNotNull null
            if (!reserve(key, loss.atNanos)) return@mapNotNull null
            TeamFaintDecision(loss.owner.speciesName, loss.owner.speciesId, loss.evidenceReferences, false,
                com.example.overdex.battle.inference.TerminalChargedFaintResolver.BASIS,
                source.copy(perceivedAt = sample.wallTimeMillis, monotonicTimeNanos = sample.sourceAtNanos))
        }
        return countDecisions + hpDecisions + terminalDecisions
    }

    private fun reserve(owner: String, at: Long): Boolean {
        if (owner in deliveredOwners) return false
        if (!history.faintLimits().permits(deliveredOwners.values, at, publishing = true)) return false
        deliveredOwners[owner] = at
        return true
    }

    private companion object {
        const val CRY_CORROBORATION_WINDOW_NANOS = 5_000_000_000L
    }
}
