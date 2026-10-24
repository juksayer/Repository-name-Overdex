package com.example.overdex.battle.inference

import com.example.overdex.battle.audio.FastMoveReferenceMatcher
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.FastMoveSoundCandidate
import com.example.overdex.battle.reality.ArticleId

/** A low-confidence comparison for presentation; never a move identity or an attack count. */
data class FastMoveAudioPreference(val moveName: String, val evidenceIds: List<ArticleId>)

internal data class AudioPreferenceCombatant(
    val side: ActivePokemonSide,
    val speciesName: String,
    val appearanceStartedAtNanos: Long,
    val legalMoveNames: List<String>,
)

/** One strict match or two disjoint weaker comparisons can favor one uniquely legal side. */
internal class FastMoveAudioPreferenceTracker {
    private data class Sample(val side: ActivePokemonSide, val name: String, val at: Long,
        val evidenceIds: List<ArticleId>, val strict: Boolean)
    private var context = emptyList<AudioPreferenceCombatant>()
    private val samples = mutableListOf<Sample>()

    fun clear() { samples.clear(); context = emptyList() }

    fun observe(id: ArticleId, at: Long, candidates: List<FastMoveSoundCandidate>, combatants: List<AudioPreferenceCombatant>,
        referenceMatchAccepted: Boolean = false, contextEvidence: List<ArticleId> = emptyList()) {
        if (context != combatants) { clear(); context = combatants }
        if (combatants.size != 2 || combatants.any { it.legalMoveNames.isEmpty() }) { samples.clear(); return }
        val previous = samples.lastOrNull()
        if (previous != null && at <= previous.at) return // Delayed/duplicate publications cannot refute newer evidence.
        val best = FastMoveReferenceMatcher.plausibleCandidate(candidates)
        val owners = combatants.filter { fighter -> fighter.legalMoveNames.any { key(it) == key(best?.moveName.orEmpty()) } }
        val legalNames = combatants.flatMap { it.legalMoveNames }.map(::key).toSet()
        val measured = candidates.associateBy { key(it.moveName) }
        val otherLegal = candidates.filter { key(it.moveName) in legalNames && key(it.moveName) != key(best?.moveName.orEmpty()) }
        // Accepted captures retain the global spectrum/changing-spectrum leaders, not every
        // low-ranking legal move. Recheck that strict margin; do not apply the weaker path's
        // complete-catalogue requirement to an already accepted global comparison.
        val strict = referenceMatchAccepted && FastMoveReferenceMatcher.accepts(candidates)
        if (best == null || owners.size != 1 || (!strict && (!measured.keys.containsAll(legalNames) ||
            otherLegal.any { best.similarity - it.similarity < .015f || best.changingSimilarity <= it.changingSimilarity }))) {
            samples.clear(); return
        }
        if (at < combatants.maxOf { it.appearanceStartedAtNanos }) return
        val owner = owners.single()
        if (previous != null && (at - previous.at > MAX_AGE || previous.side != owner.side || key(previous.name) != key(best.moveName))) {
            samples.clear()
        }
        // Overlapping 1.2-second captures are one comparison, not repeated corroboration.
        if (samples.lastOrNull()?.let { at - it.at < WINDOW_DURATION } == true) return
        samples += Sample(owner.side, best.moveName, at, (listOf(id) + contextEvidence).distinct(), strict)
        while (samples.size > 2) samples.removeAt(0)
    }

    fun preference(side: ActivePokemonSide, combatants: List<AudioPreferenceCombatant>, now: Long): FastMoveAudioPreference? {
        if (context != combatants) return null
        val last = samples.lastOrNull() ?: return null
        if (samples.size < 2 && !last.strict) return null
        if (last.side != side || now - last.at !in 0..MAX_AGE) return null
        return FastMoveAudioPreference(last.name, samples.flatMap { it.evidenceIds }.distinct())
    }

    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
    private companion object {
        const val WINDOW_DURATION = 1_200_000_000L
        const val MAX_AGE = 6_000_000_000L
    }
}
