package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*

/** Presentation only: keep our move list away from the game's charged-phase text. */
internal class ChargeTextProtectionWindow {
    var untilNanos: Long = Long.MIN_VALUE
        private set
    private var boundaryNanos = Long.MIN_VALUE
    private var latestPhaseNanos = Long.MIN_VALUE

    fun observe(article: RealityArticle, nowNanos: Long) {
        val at = article.monotonicTimeNanos ?: return
        if (at < boundaryNanos) return
        val duration = when (val p = article.payload) {
            GetReadyWitnessed, AttackIncoming -> 12_000_000_000L
            is ChargeMoveUsedAnnounced -> 3_000_000_000L
            is RawTestimony -> if (article.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
                p.data.toString().trim().uppercase() in setOf("NICE!", "GREAT!", "EXCELLENT!")) 8_000_000_000L else null
            else -> null
        }
        if (duration != null) {
            // A delayed reading must not grant a new full window at publication time.
            if (at <= nowNanos && at + duration > nowNanos) {
                latestPhaseNanos = maxOf(latestPhaseNanos, at)
                untilNanos = maxOf(untilNanos, at + duration)
            }
        } else if (article.payload is MatchEnded || article.payload is ActivePokemonFaintedWitnessed ||
            article.payload is ObservationSessionStoppedByUser || article.payload is ObservationRestartedByUser ||
            article.payload is ScreenIgnoredByUser ||
            article.payload is MatchStarted || article.payload is TeamSelectPartyWitnessed) {
            if (at >= latestPhaseNanos) {
                boundaryNanos = at
                untilNanos = Long.MIN_VALUE
            }
        }
    }

    fun isProtected(nowNanos: Long) = nowNanos < untilNanos
}

/** Has no testimony output: changing presentation cannot become battle evidence. */
internal class ChargeTextOverlayProtection(
    private val nowNanos: () -> Long = System::nanoTime,
    private val setProtected: (Boolean) -> Unit = DroidballOverlayPresentation::setChargeTextProtection,
) : Observer {
    override val observerId = ObserverId("CHARGE_TEXT_OVERLAY_PROTECTION", ObservationSource.SCREEN_CAPTURE)
    override val name = "Charge text overlay protection"
    override val managesAvailability = true
    private val stateLock = Any()
    private var scope: CoroutineScope? = null
    private var generation = 0L

    override fun start(match: Match) {
        val run: Long
        val workers: CoroutineScope
        synchronized(stateLock) {
            if (scope != null) return
            run = ++generation
            workers = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope = it }
            setProtected(false)
        }
        val window = ChargeTextProtectionWindow()
        var expiry: Job? = null
        var scheduledUntil = Long.MIN_VALUE
        workers.launch(start = CoroutineStart.UNDISPATCHED) {
            match.articles.collect { article ->
                synchronized(stateLock) {
                    if (generation != run || scope !== workers) return@collect
                    val now = nowNanos()
                    window.observe(article, now)
                    setProtected(window.isProtected(now))
                    if (scheduledUntil != window.untilNanos) {
                        scheduledUntil = window.untilNanos
                        expiry?.cancel()
                        val deadline = window.untilNanos
                        if (window.isProtected(now)) expiry = workers.launch {
                            delay((deadline - now + 999_999L) / 1_000_000L)
                            synchronized(stateLock) {
                                // An already-awake older timer cannot reveal labels in a newer phase.
                                if (generation == run && scope === workers && window.untilNanos == deadline &&
                                    !window.isProtected(nowNanos())) setProtected(false)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(stateLock) {
            ++generation
            scope?.cancel()
            scope = null
            setProtected(false)
        }
    }
}
