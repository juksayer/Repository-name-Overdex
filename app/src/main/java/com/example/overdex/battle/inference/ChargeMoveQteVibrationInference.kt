package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ChargeMoveQteVibrationPatternInferred
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
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
 * Recognizes the player's Nice, Great, and Excellent charge-move milestones.
 * Individual device-motion pulses remain primary measurements. The inferred
 * pattern only groups short pulses that are linked to a visible charge-move cue.
 */
class ChargeMoveQteVibrationInference {
    private val pulses = ArrayDeque<RealityArticle>()
    private val getReadyCues = ArrayDeque<RealityArticle>()
    private val chargeMoveCues = ArrayDeque<RealityArticle>()
    private val usedPulseIds = mutableSetOf<ArticleId>()
    private val completedCueIds = mutableSetOf<ArticleId>()

    fun accept(article: RealityArticle): ChargeMoveQteVibrationDerivation? {
        when (val payload = article.payload) {
            is DeviceMotionPulseMeasured -> {
                if (!payload.isShortVibrationLike()) return null
                pulses.addUnique(article)
                pulses.trimHistory()
                return deriveForLatestGetReady(article.monotonicTimeNanos ?: return null)
            }
            GetReadyWitnessed -> {
                getReadyCues.addUnique(article)
                getReadyCues.trimHistory()
                return deriveForGetReady(article)
            }
            ChargeMoveUsedAnnounced -> {
                chargeMoveCues.addUnique(article)
                chargeMoveCues.trimHistory()
                return deriveForChargeMoveCue(article)
            }
            else -> return null
        }
    }

    private fun deriveForLatestGetReady(atNanos: Long): ChargeMoveQteVibrationDerivation? {
        val cue = getReadyCues
            .filterNot { it.id in completedCueIds }
            .filter { cueArticle ->
                val cueAt = cueArticle.monotonicTimeNanos ?: return@filter false
                atNanos >= cueAt && atNanos - cueAt <= QTE_WINDOW_NANOS
            }
            .maxByOrNull { it.monotonicTimeNanos ?: Long.MIN_VALUE }
            ?: return null
        return derive(cue = cue, closingCue = null, minimumPulseCount = MAX_MILESTONE_COUNT)
    }

    private fun deriveForGetReady(cue: RealityArticle): ChargeMoveQteVibrationDerivation? {
        val cueAt = cue.monotonicTimeNanos ?: return null
        val closingCue = chargeMoveCues
            .filterNot { it.id in completedCueIds }
            .filter { closing ->
                val closingAt = closing.monotonicTimeNanos ?: return@filter false
                closingAt >= cueAt && closingAt - cueAt <= QTE_WINDOW_NANOS
            }
            .minByOrNull { it.monotonicTimeNanos ?: Long.MAX_VALUE }
        return if (closingCue != null) {
            derive(cue, closingCue, minimumPulseCount = 1)
        } else {
            derive(cue, closingCue = null, minimumPulseCount = MAX_MILESTONE_COUNT)
        }
    }

    private fun deriveForChargeMoveCue(closingCue: RealityArticle): ChargeMoveQteVibrationDerivation? {
        val closingAt = closingCue.monotonicTimeNanos ?: return null
        val cue = getReadyCues
            .filterNot { it.id in completedCueIds }
            .filter { ready ->
                val readyAt = ready.monotonicTimeNanos ?: return@filter false
                closingAt >= readyAt && closingAt - readyAt <= QTE_WINDOW_NANOS
            }
            .maxByOrNull { it.monotonicTimeNanos ?: Long.MIN_VALUE }
        return derive(
            cue = cue,
            closingCue = closingCue,
            // A visible Get Ready boundary makes one or two pulses meaningful.
            // Without it, require all three grades before interpreting motion.
            minimumPulseCount = if (cue != null) 1 else MAX_MILESTONE_COUNT,
        )
    }

    private fun derive(
        cue: RealityArticle?,
        closingCue: RealityArticle?,
        minimumPulseCount: Int,
    ): ChargeMoveQteVibrationDerivation? {
        if (cue?.id in completedCueIds || closingCue?.id in completedCueIds) return null
        val cueAt = cue?.monotonicTimeNanos
        val closingAt = closingCue?.monotonicTimeNanos
        val start = cueAt ?: (closingAt?.minus(QTE_WINDOW_NANOS) ?: return null)
        val end = closingAt ?: (cueAt?.plus(QTE_WINDOW_NANOS) ?: return null)
        val candidates = pulses
            .filterNot { it.id in usedPulseIds }
            .filter { pulse -> pulse.monotonicTimeNanos?.let { it in start..end } == true }
        val selected = latestPlausibleRun(candidates).takeLast(MAX_MILESTONE_COUNT)
        if (selected.size < minimumPulseCount) return null

        val times = selected.map { it.monotonicTimeNanos ?: return null }
        val windowNanos = times.last() - times.first()
        val basis = when {
            cue != null && closingCue != null ->
                "GET_READY_AND_CHARGE_MOVE_ANNOUNCEMENT_LINKED_SHORT_DEVICE_MOTION_PULSES"
            cue != null -> "GET_READY_LINKED_SHORT_DEVICE_MOTION_PULSES"
            else -> "CHARGE_MOVE_ANNOUNCEMENT_LINKED_THREE_SHORT_DEVICE_MOTION_PULSES"
        }
        selected.forEach { usedPulseIds += it.id }
        cue?.let { completedCueIds += it.id }
        closingCue?.let { completedCueIds += it.id }
        return ChargeMoveQteVibrationDerivation(
            payload = ChargeMoveQteVibrationPatternInferred(
                pulseCount = selected.size,
                windowNanos = windowNanos,
                basis = basis,
            ),
            predecessorIds = buildList {
                cue?.let { add(it.id) }
                addAll(selected.map { it.id })
                closingCue?.let { add(it.id) }
            }.distinct(),
            confidence = if (cue != null) 0.90f else 0.78f,
            observedArticle = selected.first(),
        )
    }

    private fun latestPlausibleRun(source: List<RealityArticle>): List<RealityArticle> {
        var run = mutableListOf<RealityArticle>()
        source.sortedBy { it.monotonicTimeNanos }.forEach { pulse ->
            val at = pulse.monotonicTimeNanos ?: return@forEach
            val previousAt = run.lastOrNull()?.monotonicTimeNanos
            when {
                previousAt == null -> run += pulse
                at - previousAt < MIN_INTER_PULSE_NANOS -> run[run.lastIndex] = pulse
                at - previousAt <= MAX_INTER_PULSE_NANOS -> run += pulse
                else -> run = mutableListOf(pulse)
            }
            while (run.size > MAX_MILESTONE_COUNT) run.removeAt(0)
        }
        return run
    }

    private fun DeviceMotionPulseMeasured.isShortVibrationLike(): Boolean =
        durationNanos in MIN_PULSE_DURATION_NANOS..MAX_PULSE_DURATION_NANOS

    private fun ArrayDeque<RealityArticle>.addUnique(article: RealityArticle) {
        if (none { it.id == article.id }) addLast(article)
    }

    private fun ArrayDeque<RealityArticle>.trimHistory() {
        while (size > MAX_HISTORY) removeFirst()
    }

    private companion object {
        const val MAX_MILESTONE_COUNT = 3
        const val MIN_PULSE_DURATION_NANOS = 25_000_000L
        const val MAX_PULSE_DURATION_NANOS = 300_000_000L
        const val MIN_INTER_PULSE_NANOS = 80_000_000L
        const val MAX_INTER_PULSE_NANOS = 3_000_000_000L
        const val QTE_WINDOW_NANOS = 10_000_000_000L
        const val MAX_HISTORY = 512
    }
}
