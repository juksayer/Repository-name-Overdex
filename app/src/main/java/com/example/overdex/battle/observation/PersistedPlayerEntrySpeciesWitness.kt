package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.PokemonIdentified
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
 * Attributes entry announcements from their explicit species field. A complete
 * roster is strongest. A missed earlier announcement must never make a later
 * opponent switch inherit the player side by arrival order.
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
                var lastAnnouncementAt = Long.MIN_VALUE
                var lastWitnessed: Pair<ActivePokemonSide, String>? = null
                var lastWitnessedAt = Long.MIN_VALUE
                val sideTracker = EntryAnnouncementSideTracker()

                match.articles.collect { article ->
                    if (article.sourceId.id != "ANNOUNCEMENT_WITNESS") return@collect
                    val rawText = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    // Pokémon GO's entry form explicitly reads “Go, <species>!”.
                    val normalizedText = rawText.trim().replace(Regex("\\s+"), " ")
                    if (!normalizedText.uppercase().startsWith("GO,")) return@collect
                    // A badge persists across several frames. It is one observed
                    // announcement, not several entries merely because it was captured
                    // repeatedly. The same Pokémon may legitimately return later.
                    val atNanos = article.monotonicTimeNanos ?: return@collect
                    if (normalizedText == lastAnnouncementText &&
                        atNanos - lastAnnouncementAt < DUPLICATE_WINDOW_NANOS
                    ) return@collect
                    lastAnnouncementText = normalizedText
                    lastAnnouncementAt = atNanos
                    val names = knownSpeciesNames ?: match.pokemonKnowledge.getAllSpeciesNames()
                        .also { knownSpeciesNames = it }
                    val speciesName = EntryAnnouncementSpeciesTextResolver.resolve(rawText, names)
                    Log.d("ENTRY_ANNOUNCEMENT_SPECIES", "announcement=$rawText resolved=${speciesName ?: "none"} catalogue=${names.size}")
                    speciesName ?: return@collect
                    val attribution = sideTracker.attribute(
                        speciesName,
                        match.playerRosterSpecies()
                    )
                    if (attribution == null) {
                        // The name itself remains useful testimony even when the
                        // announcement no longer establishes a trainer side.
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PokemonIdentified(speciesName),
                            timestamp = article.perceivedAt,
                            confidence = 0.98f,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = atNanos
                        )
                        return@collect
                    }
                    val witnessed = attribution.side to speciesName
                    if (witnessed == lastWitnessed && atNanos - lastWitnessedAt < DUPLICATE_WINDOW_NANOS) {
                        return@collect
                    }
                    lastWitnessed = witnessed
                    lastWitnessedAt = atNanos
                    val species = match.pokemonKnowledge.getPokemonByName(speciesName)
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonSpeciesWitnessed(attribution.side, speciesName, species?.id),
                        timestamp = article.perceivedAt,
                        confidence = attribution.confidence,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = atNanos
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }

    private companion object {
        const val DUPLICATE_WINDOW_NANOS = 2_000_000_000L
    }
}

object EntryAnnouncementSpeciesTextResolver {
    private val entryPattern = Regex("^\\s*Go,\\s*(.+?)[!.]*\\s*$", RegexOption.IGNORE_CASE)

    fun resolve(rawText: String, knownSpeciesNames: Set<String>): String? {
        val singleLine = rawText.trim().replace(Regex("\\s+"), " ")
        val speciesField = entryPattern.matchEntire(singleLine)?.groupValues?.get(1) ?: return null
        return SpeciesTextResolver.resolve(speciesField, knownSpeciesNames)
    }
}
