package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.battle.reality.RealityArticle

/** Source-time projection only: a candidate requests independent readers, never a faint. */
internal class CryDepartureEvidence {
    private val history = CryAppearanceHistory()
    private val cries = linkedMapOf<String, LocalizedCryOccurrence>()
    private val emitted = mutableSetOf<String>()
    private var attemptFrom = Long.MIN_VALUE
    var unavailable = false
        private set

    @Synchronized fun speciesIds(): Set<Int> = history.speciesIds()

    @Synchronized fun observe(article: RealityArticle): List<RepeatCryDepartureCandidate> {
        val at = article.monotonicTimeNanos ?: return emptyList()
        if (at < attemptFrom) return emptyList()
        if (article.payload == ObservationRestartedByUser) {
            history.reset(); cries.clear(); emitted.clear(); unavailable = false; attemptFrom = at
            return emptyList()
        }
        if (unavailable) return emptyList()
        when (val payload = article.payload) {
            is ActivePokemonSpeciesWitnessed -> {
                val entry = article.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
                if (!entry && !isSideLocatedSpeciesSource(article.sourceId.id)) return emptyList()
                // Never consume downstream appearanceStartedAtNanos or audio-inferred identities.
                history.accept(CryAppearanceHistory.Identity(article.id.value, at, payload.side,
                    payload.speciesId?.takeIf { it > 0 }, entry))
            }
            is BattleCryLocalized -> {
                if (article.sourceId.id != SOURCE || article.evidenceReferences.isNullOrEmpty() ||
                    payload.speciesId <= 0 || !payload.correlation.isFinite() ||
                    payload.correlation !in 0f..1f || payload.durationNanos <= 0 ||
                    payload.durationNanos > 4_000_000_000L || at > Long.MAX_VALUE - payload.durationNanos) return emptyList()
                val occurrence = LocalizedCryOccurrence(article.id.value, payload.speciesId, at,
                    at + payload.durationNanos, listOf(article.id.value) + article.evidenceReferences)
                val old = cries[occurrence.id]
                if ((old != null && old != occurrence) || (old == null && cries.size >= MAX_CRIES)) {
                    unavailable = true
                    return emptyList()
                }
                cries[occurrence.id] = occurrence
            }
            else -> return emptyList()
        }
        if (history.unavailable) { unavailable = true; return emptyList() }
        return RepeatCryDepartureRule.candidates(cries.values.toList(), history.intervals()).filter {
            // Late independent identities can refine an ambiguous scheduling hint. Keep both
            // immutable records with their distinct basis; neither supplies a faint assertion.
            emitted.add("${it.cryOccurrenceIds}|${it.possibleAppearanceIds}|${it.side}")
        }
    }

    companion object {
        const val SOURCE = "LOCALIZED_BATTLE_CRY_WITNESS"
        const val MAX_CRIES = 256
    }
}

/** Reuse battle lifecycle signals, but never stop cry acquisition on a derived faint count. */
internal class CryListeningGate {
    private val gate = FastMoveListeningGate()
    val generation get() = gate.generation
    fun observe(article: RealityArticle) {
        if (article.payload !is ActivePokemonFaintedWitnessed)
            gate.observe(article.payload, article.monotonicTimeNanos, article.id.value)
    }
    fun accepts(from: Long, until: Long, generation: Long) = gate.accepts(from, until, generation)
    fun allows(at: Long) = gate.allows(at)
    fun triggerIds() = gate.triggerIds()
}
