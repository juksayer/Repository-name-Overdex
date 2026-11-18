package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*

/** Presentation only: keep our move list away from the game's charged-phase text. */
internal class ChargeTextProtectionWindow(private var boundaryNanos: Long = Long.MIN_VALUE) {
    private var phaseUntil = Long.MIN_VALUE
    private var bannerBoundaryNanos = boundaryNanos
    private var bannerUntil = Long.MIN_VALUE
    val untilNanos get() = maxOf(phaseUntil, bannerUntil)
    private var latestPhaseNanos = Long.MIN_VALUE
    private var camera = ChargeHudCameraCue()

    fun observeBar(bar: CompleteHpBarObserved, at: Long, now: Long) {
        if (at <= boundaryNanos || at > now || now - at > SECOND) return
        if (camera.observe(bar, at)) hold(at, now, 12 * SECOND)
    }

    fun prepare(cue: AcceptedChargeCaptureCue, now: Long) {
        val duration = if (cue.kind == AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS) 8 * SECOND else 12 * SECOND
        hold(cue.capturedAtNanos, now, duration, untilFastMove = cue.kind != AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS)
    }

    fun battlefieldReturned(at: Long, now: Long) {
        if (phaseUntil != Long.MAX_VALUE && at <= now && at < phaseUntil && at >= latestPhaseNanos) closePhase(at)
    }

    fun bannerVisible(at: Long, now: Long) {
        if (at > bannerBoundaryNanos && at <= now && now - at < BANNER_TAIL)
            bannerUntil = maxOf(bannerUntil, at + BANNER_TAIL)
    }

    private fun hold(at: Long, now: Long, duration: Long, untilFastMove: Boolean = false) {
        if (at <= boundaryNanos || at > now || now - at >= duration) return
        latestPhaseNanos = maxOf(latestPhaseNanos, at)
        phaseUntil = if (untilFastMove) Long.MAX_VALUE else maxOf(phaseUntil, at + duration)
    }

    private fun closePhase(at: Long) {
        boundaryNanos = at
        phaseUntil = Long.MIN_VALUE
        camera = ChargeHudCameraCue()
    }

    fun observe(article: RealityArticle, nowNanos: Long) {
        val at = article.monotonicTimeNanos ?: return
        if (at < boundaryNanos || at > nowNanos) return
        val duration = when (val p = article.payload) {
            GetReadyWitnessed, AttackIncoming -> 12_000_000_000L
            ChargeMoveUsedAnnounced, is ChargedMoveEnergySpent -> 12 * SECOND
            // A HUD word may occupy a separate OCR line. An exact grade line can
            // hide our labels without changing what any battle witness accepts.
            is RawTestimony -> if (article.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
                (p.data as? String)?.lineSequence()?.any(::isPlayerQteGradeText) == true) 8 * SECOND else null
            else -> null
        }
        if (duration != null) {
            // A delayed reading must not grant a new full window at publication time.
            hold(at, nowNanos, duration, untilFastMove = article.payload !is RawTestimony)
        } else if (article.payload is FastMoveUseObserved) {
            // Use the move's capture time, including when it arrives before an older charge report.
            if (at > latestPhaseNanos) {
                if (phaseUntil != Long.MIN_VALUE) closePhase(at) else boundaryNanos = at
            }
        } else if (article.payload is ActivePokemonFaintedWitnessed) {
            battlefieldReturned(at, nowNanos)
        } else if (article.payload is MatchEnded ||
            article.payload is ObservationSessionStoppedByUser || article.payload is ObservationRestartedByUser ||
            article.payload is ScreenIgnoredByUser ||
            article.payload is MatchStarted || article.payload is TeamSelectPartyWitnessed) {
            if (at >= latestPhaseNanos) {
                closePhase(at)
                bannerBoundaryNanos = at
                if (bannerUntil <= at + BANNER_TAIL) bannerUntil = Long.MIN_VALUE
            }
        }
    }

    fun isProtected(nowNanos: Long) = nowNanos < untilNanos

    private companion object {
        const val SECOND = 1_000_000_000L
        const val BANNER_TAIL = 1_250_000_000L
    }
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
    private class Session(startedAt: Long) {
        val window = ChargeTextProtectionWindow(startedAt)
        val workers = CoroutineScope(Dispatchers.Default + SupervisorJob())
        var expiry: Job? = null
        var deadline = Long.MIN_VALUE
        var detach: (() -> Unit)? = null
    }
    private var active: Session? = null

    fun bannerVisible(at: Long) = synchronized(stateLock) {
        active?.let { session ->
            val now = nowNanos()
            session.window.bannerVisible(at, now)
            update(session, now)
        }
        Unit
    }

    fun observeBar(bar: CompleteHpBarObserved, at: Long) = synchronized(stateLock) {
        active?.let { session ->
            val now = nowNanos()
            session.window.observeBar(bar, at, now)
            update(session, now)
        }
        Unit
    }

    fun battlefieldReturned(at: Long) = synchronized(stateLock) {
        active?.let { session ->
            val now = nowNanos()
            session.window.battlefieldReturned(at, now)
            update(session, now)
        }
        Unit
    }

    private fun update(session: Session, now: Long) {
        val deadline = session.window.untilNanos
        if (session.deadline == deadline) return
        session.deadline = deadline
        session.expiry?.cancel()
        val protected = session.window.isProtected(now)
        setProtected(protected)
        if (protected && deadline != Long.MAX_VALUE) session.expiry = session.workers.launch {
            delay((deadline - now + 999_999L) / 1_000_000L)
            synchronized(stateLock) {
                if (active === session && session.deadline == deadline && !session.window.isProtected(nowNanos()))
                    setProtected(false)
            }
        }
    }

    override fun start(match: Match) {
        synchronized(stateLock) {
            if (active != null) return
            val session = Session(match.observationAttemptStartedAtNanos)
            active = session
            setProtected(false)
            session.detach = match.acceptedChargeCapturePreparation.attach(AcceptedChargeCaptureConsumer.HUD) { cue ->
                synchronized(stateLock) {
                    if (active === session) {
                        val now = nowNanos()
                        session.window.prepare(cue, now)
                        update(session, now)
                    }
                }
            }
            session.workers.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    synchronized(stateLock) {
                        if (active !== session) return@collect
                        val now = nowNanos()
                        session.window.observe(article, now)
                        update(session, now)
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(stateLock) {
            val session = active
            active = null
            session?.detach?.invoke()
            session?.workers?.cancel()
            setProtected(false)
        }
    }
}
