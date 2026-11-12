package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Acquire inactive-card shade/timer pixels briefly after a witnessed player appearance change.
 * A faint replacement can produce the same cue: this gate certifies neither a voluntary switch
 * nor a countdown value. Initial-entry/countdown-clearance evidence has a separate assignment.
 */
class SwitchTimerCaptureGate(private val now: () -> Long = System::nanoTime) : Observer {
    override val observerId = ObserverId("SWITCH_TIMER_CAPTURE_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Switch Timer Capture Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var names = emptySet<String>()
    private var appearanceAt: Long? = null
    private var latestIdentityAt = Long.MIN_VALUE
    private var from = Long.MIN_VALUE
    private var deadline = Long.MIN_VALUE
    private var attemptFrom = Long.MIN_VALUE
    private var ended = false

    @Synchronized fun isEnabled(): Boolean = !ended && now() in from until deadline

    @Synchronized fun acceptsFrame(sourceAt: Long): Boolean =
        isEnabled() && sourceAt >= attemptFrom && sourceAt in from until deadline && sourceAt <= now()

    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        if (at < attemptFrom || at > now()) return
        when (val payload = article.payload) {
            ObservationRestartedByUser -> reset(at)
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> close()
            is ActivePokemonSpeciesWitnessed -> if (payload.side == ActivePokemonSide.PLAYER)
                identity(setOf(key(payload.speciesName)), at, payload.appearanceStartedAtNanos)
            is PlayerActiveIdentityResolved -> identity(
                setOf(key(payload.observedSpeciesName), key(payload.speciesName)), at,
                payload.appearanceStartedAtNanos,
            )
            else -> Unit
        }
    }

    private fun identity(observedNames: Set<String>, at: Long, explicitAppearance: Long?) {
        if (ended || at < latestIdentityAt || observedNames.any(String::isBlank) ||
            (explicitAppearance != null && (explicitAppearance > at || explicitAppearance < attemptFrom))) return
        val previousAppearance = appearanceAt
        // Refinement of an already known appearance (for example its roster form) is not a switch.
        if (explicitAppearance != null && explicitAppearance == previousAppearance) {
            names = names + observedNames
            latestIdentityAt = maxOf(latestIdentityAt, at)
            return
        }
        if (explicitAppearance != null && previousAppearance != null && explicitAppearance < previousAppearance) return
        val first = names.isEmpty()
        val changedName = names.intersect(observedNames).isEmpty()
        val changedAppearance = explicitAppearance != null && previousAppearance != null &&
            explicitAppearance > previousAppearance
        // Same-time raw/resolved delivery may enrich the baseline, never open a second assignment.
        val changed = !first && at > latestIdentityAt && (changedName || changedAppearance)
        latestIdentityAt = at
        if (first || changed) {
            names = observedNames
            appearanceAt = explicitAppearance ?: at
        } else if (!changedName) names = names + observedNames
        if (changed) {
            from = explicitAppearance ?: at
            deadline = from + WINDOW_NANOS
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
        scope?.cancel("Switch timer gate stopped"); scope = null
        synchronized(this) { close() }
    }

    private fun close() { ended = true; from = Long.MIN_VALUE; deadline = Long.MIN_VALUE }
    private fun reset(at: Long) {
        names = emptySet(); appearanceAt = null; latestIdentityAt = Long.MIN_VALUE
        from = Long.MIN_VALUE; deadline = Long.MIN_VALUE; attemptFrom = at; ended = false
    }

    companion object {
        const val WINDOW_NANOS = 3_000_000_000L
        private fun key(name: String) = name.trim().lowercase(Locale.ROOT)
    }
}
