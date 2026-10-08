package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.BattleCryCandidateMeasurement
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class DecisiveBattleCryMatch(
    val speciesId: Int,
    val similarity: Float,
    val distinctSpeciesMargin: Float,
)

/**
 * A clean acoustic result may testify to species identity. The complete ranked
 * measurement remains the primary evidence, and later visual testimony may
 * confirm or correct this provisional conclusion.
 */
internal object DecisiveBattleCryRule {
    private const val MINIMUM_SIMILARITY = 0.88f
    private const val MINIMUM_DISTINCT_SPECIES_MARGIN = 0.035f

    fun match(
        payload: BattleCryCandidatesMeasured,
        cueAtNanos: Long? = null,
        matchStartedAtNanos: Long? = null,
    ): DecisiveBattleCryMatch? {
        if (payload.cueKind == "FAST_MOVE_IMPACT" || payload.candidates.isEmpty()) return null
        // Countdown-shaped false positives can occur throughout combat as a
        // moving Pokemon crosses that crop. A real opening cry may finish its
        // short post-roll just after GO; it cannot begin tens of seconds later.
        if (payload.cueKind.startsWith("COUNTDOWN_") &&
            cueAtNanos != null && matchStartedAtNanos != null &&
            cueAtNanos > matchStartedAtNanos + OPENING_AUDIO_TAIL_NANOS
        ) return null
        val ranked = payload.candidates.sortedByDescending(BattleCryCandidateMeasurement::similarity)
        val best = ranked.first()
        val runnerUp = ranked.firstOrNull { it.speciesId != best.speciesId } ?: return null
        val margin = best.similarity - runnerUp.similarity
        if (best.similarity < MINIMUM_SIMILARITY || margin < MINIMUM_DISTINCT_SPECIES_MARGIN) return null
        return DecisiveBattleCryMatch(best.speciesId, best.similarity, margin)
    }

    private const val OPENING_AUDIO_TAIL_NANOS = 3_000_000_000L
}

class DecisiveBattleCrySpeciesWitness(
    override val observerId: ObserverId = ObserverId(
        "DECISIVE_BATTLE_CRY_SPECIES_WITNESS",
        ObservationSource.AUDIO_CAPTURE,
    ),
    override val name: String = "Decisive Battle Cry Species Witness",
) : Observer {
    private var scope: CoroutineScope? = null
    private val openingSideGate = OpeningCrySideGate()

    override fun start(match: Match) {
        if (scope != null) return
        openingSideGate.reset()
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var matchStartedAtNanos: Long? = null
                match.articles.collect { article ->
                    if (article.payload is MatchStarted) {
                        matchStartedAtNanos = article.monotonicTimeNanos
                        return@collect
                    }
                    val candidates = article.payload as? BattleCryCandidatesMeasured ?: return@collect
                    val decisive = DecisiveBattleCryRule.match(
                        candidates,
                        cueAtNanos = article.monotonicTimeNanos,
                        matchStartedAtNanos = matchStartedAtNanos,
                    ) ?: return@collect
                    val species = match.pokemonKnowledge.getPokemonById(decisive.speciesId) ?: return@collect
                    val roster = match.playerRosterSpecies()
                    val leadId = match.playerLeadSpeciesId()
                    val side = when {
                        candidates.cueKind == "SPECIES_ENTRY" && decisive.speciesId == leadId ->
                            openingSideGate.sidesFor(
                                candidates.candidates.map { it.speciesId to it.similarity },
                                leadId,
                            ).singleOrNull()
                        roster.size >= 3 && match.sideForRosterKnownSpecies(species.name) == ActivePokemonSide.OPPONENT ->
                            ActivePokemonSide.OPPONENT
                        candidates.cueKind == "SPECIES_ENTRY" &&
                            match.sideForRosterKnownSpecies(species.name) == ActivePokemonSide.PLAYER ->
                            ActivePokemonSide.PLAYER
                        else -> null
                    } ?: return@collect

                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonSpeciesWitnessed(side, species.name, species.id),
                        timestamp = article.perceivedAt,
                        confidence = decisive.similarity,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime(),
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Decisive battle cry species witness stopped")
        scope = null
        openingSideGate.reset()
    }
}
