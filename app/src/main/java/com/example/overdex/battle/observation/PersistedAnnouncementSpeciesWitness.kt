package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Attributes a named, non-entry announcement from the complete Team Select roster.
 * A player-entry “Go, <species>!” has a direct player-side witness of its own.
 */
class PersistedAnnouncementSpeciesWitness(
    override val observerId: ObserverId = ObserverId(
        "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS", ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "Announcement Species Roster Attribution Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var knownSpeciesNames: Set<String>? = null
                var lastWitnessed: Pair<com.example.overdex.battle.custody.ActivePokemonSide, String>? = null
                match.articles.collect { article ->
                    if (article.sourceId.id != "ANNOUNCEMENT_WITNESS") return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    // Direct “Go,” testimony has an independent player-entry witness.
                    if (text.trim().uppercase().startsWith("GO,")) return@collect
                    val names = knownSpeciesNames ?: match.pokemonKnowledge.getAllSpeciesNames()
                        .also { knownSpeciesNames = it }
                    val speciesName = SpeciesTextResolver.resolve(text, names) ?: return@collect
                    val side = match.sideForRosterKnownSpecies(speciesName) ?: return@collect
                    val witnessed = side to speciesName
                    if (witnessed == lastWitnessed) return@collect
                    lastWitnessed = witnessed
                    val species = match.pokemonKnowledge.getPokemonByName(speciesName)
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonSpeciesWitnessed(side, speciesName, species?.id),
                        timestamp = article.perceivedAt,
                        confidence = null,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                    )
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
