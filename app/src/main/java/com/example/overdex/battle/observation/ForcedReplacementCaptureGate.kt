package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** A short chance to read the replacement prompt, never a declaration that a Pokémon fainted. */
class ForcedReplacementCaptureGate(private val now: () -> Long = System::nanoTime) : Observer {
    override val observerId = ObserverId("FORCED_REPLACEMENT_CAPTURE_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Forced Replacement Message Attention"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var from = Long.MIN_VALUE
    private var deadline = Long.MIN_VALUE
    private var requestedThrough = Long.MIN_VALUE
    private var resolvedThrough = Long.MIN_VALUE
    private var attemptFrom = Long.MIN_VALUE
    private var lifecycleAt = Long.MIN_VALUE
    private var latestHpAt = Long.MIN_VALUE
    private var latestCountAt = Long.MIN_VALUE
    private var latestTimerAt = Long.MIN_VALUE
    private var playerEmpty = false
    private var playerCount: Int? = null
    private var timerSeconds: Int? = null
    private var ended = false

    @Synchronized fun isEnabled(): Boolean = !ended && now() < deadline
    @Synchronized fun acceptsFrame(at: Long): Boolean =
        isEnabled() && at >= from && at >= attemptFrom && at < deadline && at <= now()

    /** A source-timed scheduling hint; repeated hints cannot prolong an active assignment. */
    @Synchronized fun request(at: Long) {
        val current = now()
        if (ended || at < attemptFrom || at > current || at <= requestedThrough ||
            at <= resolvedThrough || current - at >= WINDOW_NANOS) return
        requestedThrough = at
        if (current < deadline) return
        from = at
        deadline = at + WINDOW_NANOS
    }

    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        val payload = article.payload
        if (payload is ObservationRestartedByUser || payload is ScreenIgnoredByUser ||
            payload is ObservationSessionStoppedByUser || payload is MatchEnded) {
            if (at < lifecycleAt || at > now()) return
            lifecycleAt = at
            if (payload is ObservationRestartedByUser) reset(at) else close()
            return
        }
        if (ended || at < attemptFrom || at > now()) return
        when (payload) {
            is ActiveHpBarMeasured -> if (payload.side == ActivePokemonSide.PLAYER)
                observeHp(payload.filledFraction, at)
            is ActiveHpBarDamageTickMeasured -> if (payload.damagedSide == ActivePokemonSide.PLAYER)
                observeHp(payload.afterFraction, at)
            is ActivePokemonFaintedWitnessed -> if (payload.side == ActivePokemonSide.PLAYER) request(at)
            is PlayerPokeBallCountMeasured -> if (at > latestCountAt && payload.maximumCount == 3 && payload.visibleCount in 0..3) {
                latestCountAt = at
                val previous = playerCount
                // A count increase cannot repeatedly rearm one disappearance.
                if (previous == null || payload.visibleCount <= previous) {
                    playerCount = payload.visibleCount
                    if (previous != null && payload.visibleCount < previous) request(at)
                }
            }
            ChargeMoveUsedAnnounced -> request(at)
            is BattleDecisionTimerObserved -> if (at > latestTimerAt &&
                (payload.secondsRemaining == null || payload.secondsRemaining in 0..15)) {
                latestTimerAt = at
                val seconds = payload.secondsRemaining
                // Repeated countdown ticks are one dial, not continuous work.
                if (seconds != null && (timerSeconds == null || seconds > requireNotNull(timerSeconds))) request(at)
                timerSeconds = seconds
            }
            is ForcedReplacementPromptWitnessed -> if (at >= from && at < deadline) {
                // Suppress remaining cues from this same short assignment after success.
                resolvedThrough = maxOf(resolvedThrough, deadline - 1L)
                deadline = Long.MIN_VALUE
            }
            else -> Unit
        }
    }

    private fun observeHp(fraction: Float, at: Long) {
        if (at <= latestHpAt || !fraction.isFinite() || fraction !in 0f..1f) return
        latestHpAt = at
        if (fraction == 0f && !playerEmpty) request(at)
        playerEmpty = fraction == 0f
    }

    override fun start(match: Match) {
        if (scope != null) return
        synchronized(this) { reset(Long.MIN_VALUE); lifecycleAt = Long.MIN_VALUE }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { workers ->
            workers.launch(start = CoroutineStart.UNDISPATCHED) { match.articles.collect(::observe) }
        }
    }
    override fun stop() {
        scope?.cancel("Forced replacement attention stopped"); scope = null
        synchronized(this) { close() }
    }
    private fun close() { ended = true; deadline = Long.MIN_VALUE }
    private fun reset(at: Long) {
        ended = false; attemptFrom = at; from = Long.MIN_VALUE; deadline = Long.MIN_VALUE
        requestedThrough = Long.MIN_VALUE; resolvedThrough = Long.MIN_VALUE
        latestHpAt = Long.MIN_VALUE; latestCountAt = Long.MIN_VALUE; latestTimerAt = Long.MIN_VALUE
        playerEmpty = false; playerCount = null; timerSeconds = null
    }
    companion object { const val WINDOW_NANOS = 3_000_000_000L }
}
