package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.StatChangeTiming

/** Recognition attention, not PCM retention. Delayed cues retain their source-time window. */
internal class StatSoundListeningGate {
    private data class Window(val from: Long, val until: Long)
    private val windows = ArrayDeque<Window>()
    private var attemptFrom = Long.MIN_VALUE
    private var stopped = false
    private var ended = false
    @Volatile var generation = 0L
        private set

    @Synchronized fun observe(payload: TestimonyPayload, at: Long?, sourceId: String = "") {
        if (at != null && at < attemptFrom) return
        when (payload) {
            ObservationRestartedByUser -> {
                generation++; windows.clear(); stopped = false; ended = false
                attemptFrom = at ?: attemptFrom
            }
            ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                generation++; windows.clear(); stopped = true
            }
            is MatchEnded -> { ended = true; windows.clear() }
            else -> {
                if (at == null || stopped || ended) return
                val duration = when (payload) {
                    AttackIncoming, GetReadyWitnessed -> CHARGED_PHASE_NANOS
                    ChargeMoveUsedAnnounced, is ChargeMoveQteVibrationPatternInferred -> EFFECT_TAIL_NANOS
                    is RawTestimony -> if (sourceId == "QTE_PROGRESS_TEXT_WITNESS" &&
                        QTE_GRADE.matches(payload.data.toString().trim())) EFFECT_TAIL_NANOS else return
                    else -> return
                }
                windows += Window(at, at + duration)
                // Retain the newest source windows, independent of delivery order.
                val retained = windows.sortedByDescending { it.until }.take(MAX_WINDOWS)
                windows.clear(); windows.addAll(retained)
            }
        }
    }

    @Synchronized fun allowsScan(at: Long, expectedGeneration: Long = generation, now: Long = at): Boolean =
        expectedGeneration == generation && !stopped && !ended &&
            at >= attemptFrom && now - at in 0..MAX_AUDIO_LAG_NANOS &&
            windows.any { at in it.from..it.until && now in it.from..it.until }

    /** A result may close attention while a previously requested finite probe finishes. */
    @Synchronized fun acceptsPendingProbe(at: Long, expectedGeneration: Long): Boolean =
        expectedGeneration == generation && !stopped && at >= attemptFrom

    @Synchronized fun canRequestProbe(at: Long): Boolean =
        !stopped && !ended && at >= attemptFrom

    companion object {
        const val CHARGED_PHASE_NANOS = 15_000_000_000L
        const val EFFECT_TAIL_NANOS = StatChangeTiming.MAX_CUE_GAP_NANOS + 1_000_000_000L
        private const val MAX_AUDIO_LAG_NANOS = 2_000_000_000L
        private const val MAX_WINDOWS = 8
        private val QTE_GRADE = Regex("^(NICE|GREAT|EXCELLENT)[!.]*$", RegexOption.IGNORE_CASE)
    }
}
