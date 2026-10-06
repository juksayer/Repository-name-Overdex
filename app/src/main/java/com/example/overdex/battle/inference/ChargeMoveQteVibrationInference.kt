package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ChargeMoveQteVibrationPatternInferred
import com.example.overdex.battle.custody.DeviceMotionPulseMeasured
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.ArrayDeque

data class ChargeMoveQteVibrationDerivation(
    val payload: ChargeMoveQteVibrationPatternInferred,
    val predecessorIds: List<ArticleId>,
    val confidence: Float,
    val observedArticle: RealityArticle
)

/**
 * Recognizes the three short haptic milestones associated with the player's
 * charged-move quick-time event. Individual device-motion pulses remain the
 * primary measurements and are never replaced by this conclusion.
 */
class ChargeMoveQteVibrationInference {
    private val pending = ArrayDeque<RealityArticle>()
    private var getReadyCue: RealityArticle? = null

    fun accept(article: RealityArticle): ChargeMoveQteVibrationDerivation? {
        if (article.payload is GetReadyWitnessed) {
            getReadyCue = article
            pending.clear()
            return null
        }
        if (article.payload !is DeviceMotionPulseMeasured) return null
        val atNanos = article.monotonicTimeNanos ?: return null
        val cue = getReadyCue ?: return null
        val cueAt = cue.monotonicTimeNanos ?: return null
        if (atNanos < cueAt || atNanos - cueAt > MAX_AFTER_GET_READY_NANOS) {
            getReadyCue = null
            pending.clear()
            return null
        }

        pending.lastOrNull()?.monotonicTimeNanos?.let { previousAt ->
            val gap = atNanos - previousAt
            if (gap < MIN_INTER_PULSE_NANOS) return null
            if (gap > MAX_INTER_PULSE_NANOS) pending.clear()
        }
        pending += article
        while (pending.size > REQUIRED_PULSE_COUNT) pending.removeFirst()
        if (pending.size < REQUIRED_PULSE_COUNT) return null

        val pulses = pending.toList()
        val times = pulses.map { it.monotonicTimeNanos ?: return null }
        val gapsArePlausible = times.zipWithNext().all { (a, b) ->
            b - a in MIN_INTER_PULSE_NANOS..MAX_INTER_PULSE_NANOS
        }
        val windowNanos = times.last() - times.first()
        if (!gapsArePlausible || windowNanos > MAX_PATTERN_WINDOW_NANOS) {
            pending.removeFirst()
            return null
        }

        pending.clear()
        getReadyCue = null
        return ChargeMoveQteVibrationDerivation(
            payload = ChargeMoveQteVibrationPatternInferred(
                pulseCount = pulses.size,
                windowNanos = windowNanos
            ),
            predecessorIds = listOf(cue.id) + pulses.map { it.id },
            confidence = 0.90f,
            observedArticle = pulses.first()
        )
    }

    private companion object {
        const val REQUIRED_PULSE_COUNT = 3
        const val MIN_INTER_PULSE_NANOS = 80_000_000L
        const val MAX_INTER_PULSE_NANOS = 2_500_000_000L
        const val MAX_PATTERN_WINDOW_NANOS = 5_500_000_000L
        const val MAX_AFTER_GET_READY_NANOS = 9_000_000_000L
    }
}
