package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Attributes entry announcements from their explicit species field and independent
 * side evidence. A missed earlier announcement must never make a later
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
                val pending = ArrayDeque<Pair<RealityArticle, SpeciesTextResolution>>()

                suspend fun publish(article: RealityArticle, resolution: SpeciesTextResolution): Boolean {
                    val speciesName = resolution.speciesName
                    val atNanos = article.monotonicTimeNanos ?: return false
                    val attribution = match.entrySpeciesAttribution(speciesName, atNanos) ?: return false
                    val witnessed = attribution.side to speciesName
                    if (witnessed == lastWitnessed && kotlin.math.abs(atNanos - lastWitnessedAt) < DUPLICATE_WINDOW_NANOS) {
                        return true
                    }
                    lastWitnessed = witnessed
                    lastWitnessedAt = atNanos
                    val species = match.pokemonKnowledge.getPokemonByName(speciesName)
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonSpeciesWitnessed(attribution.side, speciesName, species?.id,
                            ocrText = (article.payload as? RawTestimony)?.data as? String,
                            ocrMatchBasis = resolution.basis),
                        timestamp = article.perceivedAt,
                        confidence = minOf(attribution.confidence, resolution.confidence),
                        evidenceReferences = (listOf(article.id.value) + attribution.evidenceArticleIds).distinct(),
                        monotonicTimeNanos = atNanos,
                    )
                    return true
                }

                match.articles.collect { article ->
                    pending.removeAll { (entry, _) ->
                        (entry.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos
                    }
                    if ((article.payload is ActivePokemonSpeciesWitnessed && isSideLocatedSpeciesSource(article.sourceId.id)) ||
                        article.payload is ActivePokemonFaintedWitnessed) {
                        // A name can arrive before its side-specific badge OCR
                        // or the preceding faint. Resolve it from that evidence, while
                        // retaining the entry's original timestamp and both links.
                        for ((entry, resolution) in pending.toList()) {
                            val age = (article.monotonicTimeNanos ?: continue) - (entry.monotonicTimeNanos ?: continue)
                            if (age > SIDE_CONFIRMATION_WINDOW_NANOS ||
                                (age >= -DUPLICATE_WINDOW_NANOS && publish(entry, resolution))) {
                                pending.remove(entry to resolution)
                            }
                        }
                        return@collect
                    }
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
                    val resolution = EntryAnnouncementSpeciesTextResolver.resolveDetailed(rawText, names)
                    Log.d("ENTRY_ANNOUNCEMENT_SPECIES", "announcement=$rawText resolved=${resolution?.speciesName ?: "none"} catalogue=${names.size}")
                    resolution ?: return@collect
                    if (!publish(article, resolution)) {
                        // The name itself remains useful testimony even when the
                        // announcement no longer establishes a trainer side.
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PokemonIdentified(resolution.speciesName),
                            timestamp = article.perceivedAt,
                            confidence = resolution.confidence,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = atNanos
                        )
                        pending.addLast(article to resolution)
                        while (pending.size > 16) pending.removeFirst()
                    }
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
        const val SIDE_CONFIRMATION_WINDOW_NANOS = 8_000_000_000L
    }
}

object EntryAnnouncementSpeciesTextResolver {
    private val entryPattern = Regex("^\\s*Go,\\s*(.+?)[!.]*\\s*$", RegexOption.IGNORE_CASE)

    fun resolve(rawText: String, knownSpeciesNames: Set<String>): String? =
        resolveDetailed(rawText, knownSpeciesNames)?.speciesName

    internal fun resolveDetailed(rawText: String, knownSpeciesNames: Set<String>): SpeciesTextResolution? {
        val singleLine = rawText.trim().replace(Regex("\\s+"), " ")
        val speciesField = entryPattern.matchEntire(singleLine)?.groupValues?.get(1) ?: return null
        return SpeciesTextResolver.match(speciesField, knownSpeciesNames).resolution
    }
}
