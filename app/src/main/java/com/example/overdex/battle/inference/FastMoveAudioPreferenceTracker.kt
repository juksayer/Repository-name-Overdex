package com.example.overdex.battle.inference

import com.example.overdex.battle.audio.FastMoveReferenceMatcher
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.FastMoveSoundCandidate
import com.example.overdex.battle.custody.FastMoveSoundSearchScope
import com.example.overdex.battle.reality.ArticleId

/** A low-confidence comparison for presentation; never a move identity or an attack count. */
data class FastMoveAudioPreference(val moveName: String, val evidenceIds: List<ArticleId>)

internal data class AudioPreferenceCombatant(
    val side: ActivePokemonSide,
    val speciesName: String,
    val appearanceStartedAtNanos: Long,
    val legalMoveNames: List<String>,
    val knownMoveName: String? = null,
)

internal data class ContextualAudioChoice(
    val side: ActivePokemonSide,
    val candidate: FastMoveSoundCandidate,
    val strict: Boolean,
    val requiresImpactSupport: Boolean,
    val legacyGlobalMatch: Boolean,
)

private fun audioKey(name: String) = name.uppercase().filter(Char::isLetterOrDigit)

/** A separate interpretation of immutable scores, never a replacement audio measurement. */
internal fun contextualAudioChoices(
    candidates: List<FastMoveSoundCandidate>,
    combatants: List<AudioPreferenceCombatant>,
    referenceMatchAccepted: Boolean,
    searchScopes: List<FastMoveSoundSearchScope>,
): List<ContextualAudioChoice> {
    if (combatants.size != 2 || combatants.map { it.side }.distinct().size != 2 ||
        combatants.any { it.legalMoveNames.isEmpty() }) return emptyList()
    val pools = combatants.associate { fighter ->
        val legal = fighter.legalMoveNames.map(::audioKey).toSet()
        val known = fighter.knownMoveName?.let(::audioKey)?.takeIf { it in legal }
        fighter.side to if (known == null) legal else setOf(known)
    }
    if (searchScopes.isNotEmpty()) {
        if (searchScopes.size != 2 || searchScopes.map { it.side }.distinct().size != 2) return emptyList()
        for (fighter in combatants) {
            val scope = searchScopes.singleOrNull { it.side == fighter.side } ?: return emptyList()
            // A missing requested reference is still a possible move. A stale or
            // partial search cannot silently turn the scored subset into a pool.
            if (audioKey(scope.speciesName) != audioKey(fighter.speciesName) ||
                scope.moveNames.map(::audioKey).toSet() != pools.getValue(fighter.side)) return emptyList()
        }
    }
    val measured = candidates.filter {
        it.similarity.isFinite() && it.changingSimilarity.isFinite() &&
            it.similarity in 0f..1f && it.changingSimilarity in 0f..1f &&
            it.matchingOffsetNanos >= 0 && it.referenceOffsetNanos >= 0 && it.matchedDurationNanos > 0
    }.distinctBy { audioKey(it.moveName) }
    val measuredNames = measured.map { audioKey(it.moveName) }.toSet()
    val complete = pools.values.all { measuredNames.containsAll(it) }
    val globalBest = measured.maxByOrNull { it.similarity }
    val legacyAccepted = searchScopes.isEmpty() && referenceMatchAccepted &&
        measured.size == candidates.size && FastMoveReferenceMatcher.accepts(measured)
    return combatants.mapNotNull { fighter ->
        val legal = pools.getValue(fighter.side)
        val ranked = measured.filter { audioKey(it.moveName) in legal }.sortedByDescending { it.similarity }
        val best = ranked.firstOrNull() ?: return@mapNotNull null
        if (pools.values.count { audioKey(best.moveName) in it } != 1) return@mapNotNull null
        val legacyStrict = legacyAccepted && best == globalBest
        val strict = legacyStrict || (complete && ranked.size >= 2 && FastMoveReferenceMatcher.accepts(ranked))
        val plausible = ranked.size >= 2 && measuredNames.containsAll(legal) &&
            best.similarity >= .85f && best.changingSimilarity >= .65f && ranked.drop(1).all {
                best.similarity - it.similarity >= .015f && best.changingSimilarity > it.changingSimilarity
            }
        if (!strict && !plausible) return@mapNotNull null
        val otherPool = pools.getValue(combatants.single { it.side != fighter.side }.side)
        // Another side's stronger fit may be the sound leaking into this fit.
        // Offsets alone cannot disambiguate: original-spectrum offsets were not saved.
        val confounded = measured.any {
            audioKey(it.moveName) in otherPool && it.similarity >= .85f && it.changingSimilarity >= .65f &&
                (it.similarity >= best.similarity || it.changingSimilarity >= best.changingSimilarity)
        }
        ContextualAudioChoice(fighter.side, best, strict, confounded, legacyStrict)
    }
}

/** One unconfounded strict match or two disjoint comparisons can favor each uniquely legal side. */
internal class FastMoveAudioPreferenceTracker {
    private data class Sample(val side: ActivePokemonSide, val name: String, val at: Long,
        val evidenceIds: List<ArticleId>, val strict: Boolean)
    private var context = emptyList<AudioPreferenceCombatant>()
    private val samples = mutableMapOf<ActivePokemonSide, MutableList<Sample>>()
    private var lastObservedAt: Long? = null

    fun clear() { samples.clear(); context = emptyList(); lastObservedAt = null }

    fun observe(id: ArticleId, at: Long, candidates: List<FastMoveSoundCandidate>, combatants: List<AudioPreferenceCombatant>,
        referenceMatchAccepted: Boolean = false, contextEvidence: List<ArticleId> = emptyList(),
        searchScopes: List<FastMoveSoundSearchScope> = emptyList(),
        allowedSides: Set<ActivePokemonSide> = combatants.map { it.side }.toSet()): List<ContextualAudioChoice> {
        if (context != combatants) { clear(); context = combatants }
        val choices = contextualAudioChoices(candidates, combatants, referenceMatchAccepted, searchScopes)
            .filter { it.side in allowedSides }
        // Re-evaluation after delayed impact support must not count the audio twice.
        if (lastObservedAt?.let { at <= it } == true) return choices
        if (combatants.isEmpty() || at < combatants.maxOf { it.appearanceStartedAtNanos }) return choices
        lastObservedAt = at
        samples.keys.retainAll(choices.map { it.side }.toSet())
        choices.forEach { choice ->
            val history = samples.getOrPut(choice.side) { mutableListOf() }
            val previous = history.lastOrNull()
            if (previous != null && (at - previous.at > MAX_AGE || audioKey(previous.name) != audioKey(choice.candidate.moveName))) {
                history.clear()
            }
            // Overlapping 1.2-second captures are one comparison, not repeated corroboration.
            if (history.lastOrNull()?.let { at - it.at < WINDOW_DURATION } == true) return@forEach
            history += Sample(choice.side, choice.candidate.moveName, at, (listOf(id) + contextEvidence).distinct(),
                choice.strict && !choice.requiresImpactSupport)
            while (history.size > 2) history.removeAt(0)
        }
        return choices
    }

    fun preference(side: ActivePokemonSide, combatants: List<AudioPreferenceCombatant>, now: Long): FastMoveAudioPreference? {
        if (context != combatants) return null
        val history = samples[side] ?: return null
        val last = history.lastOrNull() ?: return null
        if (history.size < 2 && !last.strict) return null
        if (last.side != side || now - last.at !in 0..MAX_AGE) return null
        return FastMoveAudioPreference(last.name, history.flatMap { it.evidenceIds }.distinct())
    }

    private companion object {
        const val WINDOW_DURATION = 1_200_000_000L
        const val MAX_AGE = 6_000_000_000L
    }
}
