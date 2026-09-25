package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Attributes entry announcements from the already preserved Team Select roster.
 * Announcement order is not a side label: a trainer-leader battle can announce the
 * opponent first. A roster match is PLAYER; a non-roster species is OPPONENT.
 */
class PersistedPlayerEntrySpeciesWitness(
    override val observerId: ObserverId = ObserverId("ENTRY_ANNOUNCEMENT_SPECIES_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Entry Announcement Species Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var knownSpeciesNames: Set<String>? = null
                var lastAnnouncementText: String? = null
                var lastWitnessed: Pair<ActivePokemonSide, String>? = null
                match.articles.collect { article ->
                    if (article.sourceId.id != "ANNOUNCEMENT_WITNESS") return@collect
                    val rawText = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    // Pokémon GO's entry form explicitly reads “Go, <species>!”.
                    val normalizedText = rawText.trim().replace(Regex("\\s+"), " ")
                    if (!normalizedText.uppercase().startsWith("GO,")) return@collect
                    // A badge persists across several frames. It is one observed
                    // announcement, not several entries merely because it was captured repeatedly.
                    if (normalizedText == lastAnnouncementText) return@collect
                    lastAnnouncementText = normalizedText
                    val names = knownSpeciesNames ?: match.pokemonKnowledge.getAllSpeciesNames()
                        .also { knownSpeciesNames = it }
                    val speciesName = SpeciesTextResolver.resolve(rawText, names)
                    Log.d("ENTRY_ANNOUNCEMENT_SPECIES", "announcement=$rawText resolved=${speciesName ?: "none"} catalogue=${names.size}")
                    speciesName ?: return@collect
                    val side = match.sideForRosterKnownSpecies(speciesName)
                        ?: ActivePokemonSide.OPPONENT
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

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }
}
