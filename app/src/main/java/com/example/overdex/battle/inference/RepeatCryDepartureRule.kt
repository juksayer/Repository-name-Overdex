package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide

/** A localized acoustic occurrence, NOT an entire capture window or envelope ranking. */
internal data class LocalizedCryOccurrence(
    val id: String,
    val speciesId: Int,
    val fromNanos: Long,
    val untilNanos: Long,
    val evidenceReferences: List<String>,
)

/** Independently established source-time appearance; null species means unknown, not absent. */
internal data class CryAppearanceInterval(
    val id: String,
    val side: ActivePokemonSide,
    val speciesId: Int?,
    val fromNanos: Long,
    val untilNanos: Long,
    val evidenceReferences: List<String>,
    // First visual sighting is an upper bound on entry, not necessarily its instant.
    // The preceding last sighting supplies the lower bound; exact supplied intervals default to fromNanos.
    val earliestPossibleFromNanos: Long = fromNanos,
)

internal data class RepeatCryDepartureCandidate(
    val speciesId: Int,
    val atNanos: Long,
    val possibleAppearanceIds: List<String>,
    val side: ActivePokemonSide?,
    val cryOccurrenceIds: List<String>,
    val evidenceReferences: List<String>,
)

/**
 * Rebuildable sequence projection for one Match. It never returns a faint or move use.
 * Callers supply localized, accepted cry measurements and independent appearance history.
 * Delayed delivery is immaterial; source times establish ordering. A mirror remains ambiguous
 * regardless of how many repeated cries are seen. Missing coverage cannot disambiguate it.
 * LiveLocalizedCryWitness supplies preserved waveform localizations and independent visual history.
 */
internal object RepeatCryDepartureRule {
    fun candidates(
        cries: List<LocalizedCryOccurrence>,
        appearances: List<CryAppearanceInterval>,
    ): List<RepeatCryDepartureCandidate> {
        // Conflicting source IDs cannot silently select one interpretation by arrival order.
        val validCries = cries.groupBy { it.id }.values.mapNotNull { versions ->
            versions.distinct().singleOrNull()?.takeIf {
                it.id.isNotBlank() && it.speciesId > 0 && it.fromNanos < it.untilNanos &&
                    it.evidenceReferences.isNotEmpty() && it.evidenceReferences.none(String::isBlank)
            }
        }.sortedWith(compareBy({ it.fromNanos }, { it.id }))
        val intervals = appearances.groupBy { it.id }.values.mapNotNull { versions ->
            versions.distinct().singleOrNull()?.takeIf {
                it.id.isNotBlank() && it.fromNanos < it.untilNanos && it.earliestPossibleFromNanos <= it.fromNanos &&
                    it.evidenceReferences.isNotEmpty() && it.evidenceReferences.none(String::isBlank)
            }
        }
        val result = mutableListOf<RepeatCryDepartureCandidate>()
        val previous = mutableMapOf<Int, Pair<LocalizedCryOccurrence, List<CryAppearanceInterval>>>()
        val coveredThrough = mutableMapOf<Int, Long>()
        val emitted = mutableSetOf<String>()
        for (cry in validCries) {
            val overlaps = cry.fromNanos < (coveredThrough[cry.speciesId] ?: Long.MIN_VALUE)
            coveredThrough[cry.speciesId] = maxOf(coveredThrough[cry.speciesId] ?: Long.MIN_VALUE, cry.untilNanos)
            // Several windows/references covering one sound do not supply independent repetitions.
            if (overlaps) continue
            // A cry tail can continue into the replacement interval; attribute its onset.
            val covering = intervals.filter { it.fromNanos <= cry.fromNanos && it.untilNanos > cry.fromNanos }
            val possible = intervals.filter { it.earliestPossibleFromNanos <= cry.fromNanos && it.untilNanos > cry.fromNanos }
            val bySide = covering.groupBy { it.side }
            val possibleBySide = possible.groupBy { it.side }
            // A prior cry can precede the first visual reading, within the interval bounded
            // by independent old/new sightings. Missing opposing history still cannot assign it.
            val possibleCoverageKnown = ActivePokemonSide.entries.all { side ->
                possibleBySide[side]?.any { it.speciesId != null } == true &&
                    possibleBySide[side]?.none { it.speciesId == null } == true
            }
            val possibleOwners = if (possibleCoverageKnown) possible.filter { it.speciesId == cry.speciesId }.sortedBy { it.id }
                else emptyList()
            val currentKnown = ActivePokemonSide.entries.all { bySide[it]?.size == 1 && bySide[it]?.single()?.speciesId != null }
            val eligible = if (currentKnown) covering.filter { it.speciesId == cry.speciesId }.sortedBy { it.id }
                else emptyList()
            val prior = previous[cry.speciesId]
            if (prior != null) {
                val shared = eligible.filter { current -> prior.second.any { it.id == current.id } }
                val key = shared.joinToString("|") { it.id }
                if (shared.isNotEmpty() && emitted.add(key)) {
                    // Even a uniquely shared appearance cannot resolve a cry that could have
                    // come from either active side at either occurrence.
                    val side = shared.singleOrNull()?.side?.takeIf { eligible.size == 1 && prior.second.size == 1 }
                    result += RepeatCryDepartureCandidate(
                        cry.speciesId, cry.fromNanos, shared.map { it.id }, side,
                        listOf(prior.first.id, cry.id),
                        (prior.first.evidenceReferences + cry.evidenceReferences +
                            prior.second.flatMap { it.evidenceReferences } + covering.flatMap { it.evidenceReferences })
                            .distinct().sorted(),
                    )
                }
            }
            previous[cry.speciesId] = cry to possibleOwners
        }
        return result
    }
}
