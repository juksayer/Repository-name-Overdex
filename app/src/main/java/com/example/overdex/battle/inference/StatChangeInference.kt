package com.example.overdex.battle.inference

import com.example.overdex.battle.audio.StatChangeCue
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import kotlin.math.abs

data class StatMoveEffect(val moveName: String, val stat: String, val stages: Int, val targetsUser: Boolean)
data class StatChangeDerivation(val payload: StatChangeObserved, val sound: RealityArticle, val moves: List<RealityArticle>)

/** The animation and announcement can precede one another; processing delay is separate. */
internal object StatChangeTiming { const val MAX_CUE_GAP_NANOS = 6_000_000_000L }

/** A possible move effect is not proof it happened: every derivation requires a matched sound. */
class StatChangeInference(private val effects: List<StatMoveEffect>) {
    private val sounds = ArrayDeque<RealityArticle>()
    private val moves = ArrayDeque<RealityArticle>()
    private val emitted = mutableMapOf<String, String>()

    fun accept(article: RealityArticle): List<StatChangeDerivation> {
        when (article.payload) {
            is StatChangeSoundMeasured -> if (sounds.none { it.id == article.id }) sounds.addLast(article)
            is ChargedMoveEnergySpent -> if (moves.none { it.id == article.id }) moves.addLast(article)
            else -> return emptyList()
        }
        while (sounds.size > 64) { emitted.remove(sounds.removeFirst().id.value) }
        while (moves.size > 64) moves.removeFirst()
        return sounds.mapNotNull { sound ->
            val payload = sound.payload as StatChangeSoundMeasured
            val cue = StatChangeCue.entries.firstOrNull { it.name == payload.cue } ?: return@mapNotNull null
            val at = sound.monotonicTimeNanos ?: return@mapNotNull null
            val candidates = moves.mapNotNull candidate@{ move ->
                val used = move.payload as ChargedMoveEnergySpent
                val moveAt = move.monotonicTimeNanos ?: return@candidate null
                if (abs(at - moveAt) > StatChangeTiming.MAX_CUE_GAP_NANOS) return@candidate null
                val compatible = effects.filter {
                    normalize(it.moveName) == normalize(used.moveName) && it.stat == cue.stat &&
                        it.stages.compareTo(0) == cue.direction
                }.distinct()
                val effect = compatible.singleOrNull() ?: return@candidate null
                Triple(move, effect, abs(at - moveAt))
            }.sortedBy { it.third }
            val candidate = candidates.firstOrNull() ?: return@mapNotNull null
            // With variable visual/audio timing, proximity cannot break a tie between compatible moves.
            val ambiguous = candidates.size > 1
            val key = if (ambiguous) "AMBIGUOUS" else candidate.first.id.value
            if (emitted[sound.id.value] == key) return@mapNotNull null
            if (ambiguous && sound.id.value !in emitted) return@mapNotNull null
            emitted[sound.id.value] = key
            if (ambiguous) return@mapNotNull StatChangeDerivation(
                StatChangeObserved(null, cue.stat, cue.direction, null, null, sound.id.value,
                    "CONFLICTING_CHARGED_MOVE_SIDES"), sound, candidates.map { it.first })
            val used = candidate.first.payload as ChargedMoveEnergySpent
            val side = if (candidate.second.targetsUser) used.side else when (used.side) {
                ActivePokemonSide.PLAYER -> ActivePokemonSide.OPPONENT
                ActivePokemonSide.OPPONENT -> ActivePokemonSide.PLAYER
            }
            StatChangeDerivation(StatChangeObserved(side, cue.stat, cue.direction,
                abs(candidate.second.stages), used.moveName, sound.id.value), sound, listOf(candidate.first))
        }
    }
    private fun normalize(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
}
