package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** The short decision dial is relevant after a faint or during a charged sequence, never ordinary combat. */
class DecisionTimerCaptureGate(private val now: () -> Long = System::nanoTime) : Observer {
    override val observerId = ObserverId("DECISION_TIMER_CAPTURE_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Decision Timer Capture Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var from = Long.MIN_VALUE
    private var hardDeadline = Long.MIN_VALUE
    private var deadline = Long.MIN_VALUE
    private var latestTriggerAt = Long.MIN_VALUE
    private var latestTimerAt = Long.MIN_VALUE
    private var numericTimerSeen = false
    private var resolvedThrough = Long.MIN_VALUE
    private var attemptFrom = Long.MIN_VALUE
    private var ended = false

    @Synchronized fun isEnabled(): Boolean = !ended && now() < deadline
    @Synchronized fun acceptsFrame(at: Long): Boolean =
        isEnabled() && at >= from && at >= attemptFrom && at < deadline && at <= now()

    @Synchronized internal fun request(at: Long) {
        val current = now()
        if (ended || at < attemptFrom || at > current || current - at >= MAX_WINDOW_NANOS ||
            at <= latestTriggerAt || at <= latestTimerAt || at <= resolvedThrough) return
        latestTriggerAt = at
        // Supporting cues within one assignment cannot stretch its hard deadline indefinitely.
        if (current < hardDeadline && deadline > current) return
        from = at
        numericTimerSeen = false
        hardDeadline = at + MAX_WINDOW_NANOS
        deadline = hardDeadline
    }

    @Synchronized internal fun measured(seconds: Int?, at: Long) {
        if (ended || at < attemptFrom || at > now() || at <= latestTimerAt ||
            (seconds != null && seconds !in 0..15)) return
        latestTimerAt = at
        if (at < from || at >= hardDeadline) return
        if (seconds == null) {
            // The reader's disappearance filter can retain a previous dial across
            // an expired assignment. Blank pixels cannot finish a new assignment
            // until its own numeric dial has actually been witnessed.
            if (!numericTimerSeen) return
            resolvedThrough = maxOf(resolvedThrough, at)
            deadline = Long.MIN_VALUE
            hardDeadline = Long.MIN_VALUE
        } else if (seconds in 0..15) {
            numericTimerSeen = true
            // Allow one second for rounding/clearance; a legal reading cannot outlive its assignment.
            deadline = minOf(hardDeadline, at + (seconds + 1L) * 1_000_000_000L)
        }
    }

    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        when (val payload = article.payload) {
            ObservationRestartedByUser -> reset(at)
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> close()
            GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced, is ActivePokemonFaintedWitnessed,
            is ForcedReplacementPromptWitnessed -> request(at)
            is BattleDecisionTimerObserved -> measured(payload.secondsRemaining, at)
            else -> Unit
        }
    }
    override fun start(match: Match) {
        if (scope != null) return
        synchronized(this) { reset(Long.MIN_VALUE) }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { workers ->
            workers.launch(start = CoroutineStart.UNDISPATCHED) { match.articles.collect(::observe) }
        }
    }
    override fun stop() {
        scope?.cancel("Decision timer gate stopped"); scope = null
        synchronized(this) { close() }
    }
    private fun close() {
        ended = true; deadline = Long.MIN_VALUE; hardDeadline = Long.MIN_VALUE; numericTimerSeen = false
    }
    private fun reset(at: Long) {
        ended = false; attemptFrom = at; from = Long.MIN_VALUE; hardDeadline = Long.MIN_VALUE
        deadline = Long.MIN_VALUE; latestTriggerAt = Long.MIN_VALUE; latestTimerAt = Long.MIN_VALUE
        resolvedThrough = Long.MIN_VALUE; numericTimerSeen = false
    }
    companion object { const val MAX_WINDOW_NANOS = 15_000_000_000L }
}
