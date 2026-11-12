package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.FastMoveSoundMeasured
import kotlin.math.abs

/**
 * Movement, an unclassified visual effect, and a charge-button boundary can
 * change while the player is idle. They corroborate a hit; they do not prove
 * that one occurred. Keep this rule identical in live inference and replay.
 */
object FastMoveUseEvidence {
    const val RECIPIENT_AUDIO_IMPACT = "RECIPIENT_VISUAL_ARTIFACT_WITH_MATCHED_AUDIO"
    const val MATCHED_AUDIO = "SIDE_ATTRIBUTED_AUDIO_REFERENCE"

    fun isImpact(kind: String): Boolean = kind == "HP_BORDER_PULSE" || kind == "HP_DAMAGE_TICK" ||
        kind == RECIPIENT_AUDIO_IMPACT

    fun establishesUse(kinds: Collection<String>): Boolean =
        kinds.any { it == "HP_BORDER_PULSE" || it == "HP_DAMAGE_TICK" } ||
            (RECIPIENT_AUDIO_IMPACT in kinds && MATCHED_AUDIO in kinds)

    /**
     * A completed recipient transient and a strict, independently side-resolved
     * sound may support one hit. Request/cue sides are deliberately not acoustic
     * ownership. Callers still guard appearances, charged phases and source IDs.
     */
    fun recipientAudioSupport(
        sound: FastMoveSoundMeasured,
        clipAtNanos: Long,
        damagedSide: ActivePokemonSide,
        impactAtNanos: Long,
        activeSpecies: Map<ActivePokemonSide, String>,
    ): RecipientAudioSupport? {
        if (!sound.audible || !sound.referenceMatchAccepted || activeSpecies.size != 2 ||
            sound.referenceSearchScopes.size != 2 ||
            sound.referenceSearchScopes.map { it.side }.distinct().size != 2) return null
        val combatants = sound.referenceSearchScopes.map { scope ->
            val active = activeSpecies[scope.side] ?: return null
            if (!compatibleName(active, scope.speciesName)) return null
            AudioPreferenceCombatant(scope.side, scope.speciesName, 0L, scope.moveNames)
        }
        val attackingSide = if (damagedSide == ActivePokemonSide.PLAYER) ActivePokemonSide.OPPONENT
            else ActivePokemonSide.PLAYER
        val choice = contextualAudioChoices(sound.referenceCandidates, combatants,
            sound.referenceMatchAccepted, sound.referenceSearchScopes)
            .singleOrNull { it.side == attackingSide && it.strict && !it.requiresImpactSupport } ?: return null
        val candidate = choice.candidate
        // Source windows are 1.2s. Reject malformed offsets instead of wrapping
        // clocks or using a partial reference as an unlimited matching window.
        if (candidate.matchingOffsetNanos !in 0..1_200_000_000L ||
            candidate.referenceOffsetNanos !in 0..10_000_000_000L ||
            candidate.matchedDurationNanos !in 1..1_200_000_000L) return null
        val segmentAt = runCatching { Math.addExact(clipAtNanos, candidate.matchingOffsetNanos) }.getOrNull() ?: return null
        val segmentEnd = runCatching { Math.addExact(segmentAt, candidate.matchedDurationNanos) }.getOrNull() ?: return null
        val occurrenceAt = runCatching { Math.subtractExact(segmentAt, candidate.referenceOffsetNanos) }.getOrNull() ?: return null
        if (impactAtNanos < segmentAt - AUDIO_IMPACT_ALIGNMENT_NANOS ||
            impactAtNanos > segmentEnd + AUDIO_IMPACT_ALIGNMENT_NANOS) return null
        return RecipientAudioSupport(attackingSide, candidate.moveName, occurrenceAt, segmentAt, segmentEnd,
            abs(impactAtNanos - (segmentAt + candidate.matchedDurationNanos / 2)),
            sound.referenceSearchScopes.flatMap { it.contextArticleIds }.distinct())
    }

    private fun compatibleName(active: String, scoped: String): Boolean {
        fun key(name: String) = name.lowercase().filter(Char::isLetterOrDigit)
        return key(active) == key(scoped) || ('(' !in active && key(active) == key(scoped.substringBefore('(')))
    }

    private const val AUDIO_IMPACT_ALIGNMENT_NANOS = 450_000_000L
}

data class RecipientAudioSupport(
    val attackingSide: ActivePokemonSide,
    val moveName: String,
    val occurrenceAtNanos: Long,
    val segmentAtNanos: Long,
    val segmentEndNanos: Long,
    val distanceNanos: Long,
    val contextArticleIds: List<String>,
)
