package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle

/** Attention reasons only. None asserts a disappearance, a zero bar, or a knockout. */
enum class TerminalVisualTrigger {
    LOW_HP_TRANSITION, FAST_MOVE_WITH_LOW_HP, INDEPENDENT_IMPACT_WITH_LOW_HP,
    CHARGED_IMPACT, CHARGED_RETURN, DEPARTURE, PLAYER_QTE_PROGRESS,
}

internal data class TerminalVisualCaptureWindow(
    val id: Long, val side: ActivePokemonSide, val trigger: TerminalVisualTrigger,
    val from: Long, val until: Long, val evidenceReferences: List<String> = emptyList(),
)

internal data class TerminalVisualCaptureTicket(
    val windowId: Long, val side: ActivePokemonSide, val sourceAt: Long,
)

/** Scheduling context only: the requested recipient need not yet be visible in this territory. */
internal data class TerminalParticleAssignment(
    val side: ActivePokemonSide, val phaseAt: Long, val until: Long,
    val evidenceReferences: List<String>,
)

internal data class TerminalVisualCapturedSequence(
    val payload: TerminalVisualSequenceCaptured,
    val perceivedAt: Long,
    val evidenceReferences: List<String>,
)

/**
 * Bounded raw-image attention. A fresh event can request one fixed three-second
 * window, or one longer fixed QTE budget; repeated readings never move its end. Low HP is an attention hint,
 * not a faint conclusion. No bitmap, sprite classifier, or frame queue lives here.
 */
class TerminalVisualCaptureGate(private val now: () -> Long = System::nanoTime) {
    private data class Hp(val at: Long, val fraction: Float, val articleId: String)
    private data class Charge(val recipient: ActivePokemonSide, val at: Long, val articleId: String,
        val impactAt: Long? = null, val impactArticleId: String? = null, val returnedAt: Long? = null,
        val qteAt: Long? = null, val qteArticleId: String? = null)
    private data class Frame(
        val at: Long, var wallTime: Long? = null, var finished: Boolean = false,
        var preserved: Boolean = false, var articleId: String? = null,
    )
    private data class Window(
        val id: Long, val side: ActivePokemonSide, val trigger: TerminalVisualTrigger,
        val from: Long, val until: Long, val refs: List<String>,
        val interval: Long = FRAME_INTERVAL_NANOS, val limit: Int = MAX_FRAMES,
        val frames: MutableList<Frame> = mutableListOf(), var closed: Boolean = false,
    )
    private val windows = linkedMapOf<Long, Window>()
    private val lowHp = mutableMapOf<ActivePokemonSide, Hp>()
    private val latestHpAt = mutableMapOf<ActivePokemonSide, Long>()
    private val latestIdentity = mutableMapOf<ActivePokemonSide, Pair<Long, String>>()
    private val cooldownUntil = mutableMapOf<ActivePokemonSide, Long>()
    private val boundaries = mutableMapOf<ActivePokemonSide, Long>()
    private val triggerKeys = linkedSetOf<String>()
    private var nextId = 0L
    private var accepting = false
    private var sessionAt = Long.MIN_VALUE
    private var lifecycleAt = Long.MIN_VALUE
    private var charge: Charge? = null
    private var latestUsedAt = Long.MIN_VALUE
    private var dimensions: Pair<Int, Int>? = null
    private var dimensionsAt = Long.MIN_VALUE

    @Synchronized fun beginSession(at: Long = now()) {
        windows.clear(); lowHp.clear(); latestHpAt.clear(); latestIdentity.clear()
        cooldownUntil.clear(); boundaries.clear(); triggerKeys.clear()
        charge = null
        latestUsedAt = Long.MIN_VALUE
        dimensions = null; dimensionsAt = Long.MIN_VALUE
        accepting = true; sessionAt = at; lifecycleAt = at
    }

    @Synchronized fun endSession() {
        accepting = false
        windows.values.forEach { it.closed = true }
        lowHp.clear()
        charge = null
    }

    /** External cues must carry their original source clock and supporting article IDs. */
    @Synchronized fun request(
        side: ActivePokemonSide, trigger: TerminalVisualTrigger, sourceAt: Long,
        evidenceReferences: List<String> = emptyList(),
    ): Boolean {
        expire()
        if (!accepting || !triggerFresh(sourceAt) || sourceAt < sessionAt ||
            sourceAt <= (boundaries[side] ?: Long.MIN_VALUE)) return false
        // Return has one owned-phase budget and must use the checked helper.
        if (trigger == TerminalVisualTrigger.CHARGED_RETURN || trigger == TerminalVisualTrigger.PLAYER_QTE_PROGRESS) return false
        if (trigger == TerminalVisualTrigger.CHARGED_IMPACT &&
            (charge?.qteAt != null || charge?.recipient != side || charge?.impactAt != sourceAt ||
                charge?.impactArticleId?.let { it in evidenceReferences } != true)) return false
        val hp = lowHp[side]?.takeIf { sourceAt - it.at in 0..LOW_HP_FRESH_NANOS }
        if (trigger in LOW_HP_REQUIRED && hp == null) return false
        val refs = (evidenceReferences + listOfNotNull(hp?.articleId)).filter { it.isNotBlank() }.distinct()
        if (refs.isEmpty()) return false
        val key = "$side:$trigger:${evidenceReferences.sorted().joinToString(",").ifEmpty { sourceAt.toString() }}"
        if (!triggerKeys.add(key)) return false
        while (triggerKeys.size > 128) triggerKeys.remove(triggerKeys.first())
        if (windows.values.any { it.side == side && !it.closed } ||
            sourceAt < (cooldownUntil[side] ?: Long.MIN_VALUE)) return false
        openWindow(side, trigger, sourceAt, refs)
        return true
    }

    private fun openWindow(side: ActivePokemonSide, trigger: TerminalVisualTrigger, sourceAt: Long, refs: List<String>,
        duration: Long = WINDOW_NANOS, interval: Long = FRAME_INTERVAL_NANOS, limit: Int = MAX_FRAMES) {
        val window = Window(++nextId, side, trigger, sourceAt, sourceAt + duration, refs, interval, limit)
        windows[window.id] = window
        cooldownUntil[side] = window.until + COOLDOWN_NANOS
        // Pending publication is bounded too. Raw crop testimony remains in custody.
        while (windows.size > 8) windows.remove(windows.keys.first())
    }

    @Synchronized internal fun takeFrame(side: ActivePokemonSide, sourceAt: Long): TerminalVisualCaptureTicket? {
        expire()
        if (!accepting || !fresh(sourceAt)) return null
        val window = windows.values.lastOrNull { it.side == side && !it.closed } ?: return null
        if (sourceAt !in window.from until window.until || window.frames.size >= window.limit ||
            window.frames.lastOrNull()?.let { sourceAt - it.at < window.interval } == true) return null
        window.frames += Frame(sourceAt)
        if (window.frames.size == window.limit) window.closed = true
        return TerminalVisualCaptureTicket(window.id, side, sourceAt)
    }

    @Synchronized internal fun isCapturing(side: ActivePokemonSide): Boolean {
        expire()
        return accepting && windows.values.any { it.side == side && !it.closed }
    }

    @Synchronized internal fun captureWindow(side: ActivePokemonSide): TerminalVisualCaptureWindow? {
        expire()
        if (!accepting) return null
        return windows.values.lastOrNull { it.side == side && !it.closed }?.let {
            TerminalVisualCaptureWindow(it.id, it.side, it.trigger, it.from, it.until, it.refs)
        }
    }

    /**
     * Cheap measurements have their own cadence, independent of PNG attempt quotas.
     * Preparation permits observing the transition before delayed Used testimony.
     * All observations remain neutral; this assignment is not proof of impact.
     */
    @Synchronized internal fun particleAssignment(): TerminalParticleAssignment? {
        if (!accepting) return null
        val phase = charge ?: return null
        val until = minOf(phase.at + MAX_CHARGE_PHASE_NANOS,
            phase.qteAt?.plus(QTE_WINDOW_NANOS) ?: Long.MAX_VALUE,
            phase.impactAt?.plus(2 * WINDOW_NANOS) ?: Long.MAX_VALUE)
        if (now() !in phase.at until until) return null
        return TerminalParticleAssignment(phase.recipient, phase.at, until,
            listOfNotNull(phase.articleId, phase.qteArticleId, phase.impactArticleId).distinct())
    }

    /** A new coordinate space retires all older attention; old frames cannot change it back. */
    @Synchronized internal fun observeDimensions(width: Int, height: Int, sourceAt: Long): Boolean {
        if (width <= 0 || height <= 0 || !accepting || sourceAt < sessionAt || sourceAt > now()) return false
        val size = width to height
        if (sourceAt < dimensionsAt) return size == dimensions
        dimensionsAt = sourceAt
        if (dimensions != null && dimensions != size) {
            windows.values.forEach { it.closed = true }
            lowHp.clear(); charge = null
            ActivePokemonSide.entries.forEach { boundaries[it] = maxOf(boundaries[it] ?: Long.MIN_VALUE, sourceAt) }
            latestUsedAt = maxOf(latestUsedAt, sourceAt)
        }
        dimensions = size
        return true
    }

    /** Trusted, match-owned early text. Only raw-image attention; no hit or faint assertion. */
    @Synchronized internal fun prepare(cue: AcceptedChargeCaptureCue): Boolean {
        val at = cue.capturedAtNanos
        if (!accepting || cue.cropArticleId.isBlank() || at < sessionAt || at < 0L ||
            cue.recognizedAtNanos < at || cue.recognizedAtNanos > now() ||
            boundaries.values.any { at <= it }) return false
        if (cue.kind != AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS) {
            return preparePhase(if (cue.kind == AcceptedChargeCaptureCueKind.GET_READY) opponent else player,
                at, cue.cropArticleId)
        }
        expire()
        if (now() - at !in 0 until QTE_WINDOW_NANOS || at <= latestUsedAt) return false
        val current = charge
        if (current != null && at < current.at) return false
        // Repeated grades share one phase even after that budget expires. Only
        // a later independently owned phase or a grade after its Used can rearm.
        if (current?.qteAt != null && (current.impactAt == null || at <= current.impactAt)) return false
        retireQte()
        windows.values.filter { it.side == opponent && !it.closed }.forEach { it.closed = true }
        val preparation = current?.takeIf { it.recipient == opponent && it.impactAt == null &&
            it.qteAt == null && at - it.at in 0..MAX_CHARGE_PHASE_NANOS }
        charge = Charge(opponent, preparation?.at ?: at,
            preparation?.articleId ?: cue.cropArticleId,
            qteAt = at, qteArticleId = cue.cropArticleId)
        val refs = listOfNotNull(charge?.articleId, cue.cropArticleId).distinct()
        openWindow(opponent, TerminalVisualTrigger.PLAYER_QTE_PROGRESS, at, refs,
            QTE_WINDOW_NANOS, QTE_FRAME_INTERVAL_NANOS, QTE_MAX_FRAMES)
        return true
    }

    private fun retireQte() {
        windows.values.filter { it.trigger == TerminalVisualTrigger.PLAYER_QTE_PROGRESS }.forEach { it.closed = true }
    }

    private fun preparePhase(recipient: ActivePokemonSide, at: Long, articleId: String): Boolean {
        if (!accepting || now() - at !in 0..MAX_CHARGE_PHASE_NANOS || at < sessionAt ||
            at <= latestUsedAt || boundaries.values.any { at <= it }) return false
        val current = charge
        if (current != null && at <= current.at) return false
        if (current?.recipient == recipient && at - current.at <= MAX_CHARGE_PHASE_NANOS) {
            if (current.qteAt != null && at <= current.qteAt) return false
            if (current.impactAt != null && at - current.impactAt < WINDOW_NANOS + COOLDOWN_NANOS) return false
            if (current.qteAt == null && current.impactAt == null) return false
        }
        retireQte()
        charge = Charge(recipient, at, articleId)
        return true
    }

    /**
     * One measured return transition may replace the impact window with a fixed
     * tail. It cites the same owned phase and can end no later than impact+6s.
     * Repeated normal frames cannot spend another return or extend that tail.
     */
    @Synchronized fun requestChargedReturn(side: ActivePokemonSide, sourceAt: Long): Boolean {
        val current = charge?.takeIf { it.recipient == side } ?: return false
        if (current.qteAt != null) return false
        val impactAt = current.impactAt ?: return false
        if (!accepting || !fresh(sourceAt) || sourceAt < sessionAt || current.returnedAt != null ||
            sourceAt <= (boundaries[side] ?: Long.MIN_VALUE) || sourceAt - impactAt !in 0..WINDOW_NANOS) return false
        charge = current.copy(returnedAt = sourceAt)
        windows.values.filter { it.side == side && !it.closed }.forEach { it.closed = true }
        openWindow(side, TerminalVisualTrigger.CHARGED_RETURN, sourceAt,
            listOf(current.articleId, requireNotNull(current.impactArticleId)))
        return true
    }

    /** A failed or obscured attempt consumes its cadence slot, never becomes an absent sprite. */
    @Synchronized internal fun finishFrame(ticket: TerminalVisualCaptureTicket, wallTime: Long, preserved: Boolean) {
        val frame = windows[ticket.windowId]?.frames?.firstOrNull { it.at == ticket.sourceAt } ?: return
        frame.wallTime = wallTime; frame.preserved = preserved; frame.finished = true
    }

    /** Called only for this sampler's actual CropCaptured articles, after normal publication. */
    @Synchronized internal fun published(side: ActivePokemonSide, sourceAt: Long, articleId: String) {
        windows.values.lastOrNull { it.side == side && it.frames.any { frame -> frame.at == sourceAt } }
            ?.frames?.firstOrNull { it.at == sourceAt }?.articleId = articleId
    }

    @Synchronized internal fun drainCompleted(force: Boolean = false): List<TerminalVisualCapturedSequence> {
        expire()
        val complete = mutableListOf<TerminalVisualCapturedSequence>()
        val iterator = windows.iterator()
        while (iterator.hasNext()) {
            val (_, window) = iterator.next()
            if (!window.closed) continue
            val pending = window.frames.any { !it.finished || (it.preserved && it.articleId == null) }
            if (pending && !force && now() < window.until + PUBLICATION_GRACE_NANOS) continue
            val actual = window.frames.filter { it.finished && it.preserved && it.articleId != null }.sortedBy { it.at }
            if (actual.isNotEmpty()) complete += TerminalVisualCapturedSequence(
                TerminalVisualSequenceCaptured(window.side, window.trigger.name, actual.first().at, actual.last().at, actual.size),
                requireNotNull(actual.last().wallTime),
                (window.refs + actual.map { requireNotNull(it.articleId) }).distinct(),
            )
            iterator.remove()
        }
        return complete
    }

    /** Source-ordered HP/identity context; archived conclusions cannot arm capture. */
    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        when (val payload = article.payload) {
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> if (at >= lifecycleAt) {
                lifecycleAt = at
                endSession()
            }
            ObservationRestartedByUser -> if (at >= lifecycleAt && triggerFresh(at)) beginSession(maxOf(at, now()))
            MatchStarted -> if (!accepting && at > lifecycleAt && triggerFresh(at)) beginSession(at)
            GetReadyWitnessed, AttackIncoming -> {
                preparePhase(if (payload == GetReadyWitnessed) opponent else player, at, article.id.value)
            }
            ChargeMoveUsedAnnounced -> {
                if (!accepting || at < sessionAt || at > now()) return
                latestUsedAt = maxOf(latestUsedAt, at)
                val current = charge ?: return
                if (at - current.at !in 0..MAX_CHARGE_PHASE_NANOS || current.impactAt != null ||
                    current.qteAt?.let { at < it } == true) return
                charge = current.copy(impactAt = at, impactArticleId = article.id.value)
                // Early QTE capture already owns its whole budget; delayed Used
                // adds phase closure without stacking or extending image work.
                if (current.qteAt != null || !triggerFresh(at)) return
                request(current.recipient, TerminalVisualTrigger.CHARGED_IMPACT, at,
                    listOf(current.articleId, article.id.value))
            }
            is RawTestimony -> if (article.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
                payload.ocr == null && isPlayerQteGradeText(payload.data as? String ?: "") && !article.evidenceReferences.isNullOrEmpty()) {
                prepare(AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS,
                    article.evidenceReferences.orEmpty().first(), at, now(), article.perceivedAt))
            }
            is ActivePokemonSpeciesWitnessed -> {
                // Identity may publish late. Its source boundary still retires
                // old low-HP attention, without reopening a capture window.
                if (!accepting || at > now() || at < sessionAt) return
                val key = payload.speciesName.lowercase().substringBefore('(').trim()
                val old = latestIdentity[payload.side]
                if (old != null && at <= old.first) return
                latestIdentity[payload.side] = at to key
                if ((old != null && old.second != key) || payload.appearanceStartedAtNanos != null) {
                    appearanceBoundary(payload.side, payload.appearanceStartedAtNanos?.takeIf { it in 0L..at } ?: at)
                }
            }
            is ActivePokemonFaintedWitnessed -> appearanceBoundary(payload.side, at)
            is PlayerActiveIdentityResolved -> appearanceBoundary(player, payload.appearanceStartedAtNanos)
            is SpeciesCheckMeasured -> if (payload.status == "OPENED" && payload.reason in setOf("SWITCH", "FAINT", "EMPTY_HP")) {
                appearanceBoundary(payload.side, payload.triggerMonotonicNanos)
            }
            is ActiveHpBarMeasured -> {
                if (!accepting || !fresh(at) || at < sessionAt || at <= (latestHpAt[payload.side] ?: Long.MIN_VALUE) ||
                    at <= (boundaries[payload.side] ?: Long.MIN_VALUE)) return
                latestHpAt[payload.side] = at
                val previous = lowHp[payload.side]
                if (payload.filledFraction > 0f && payload.filledFraction <= LOW_HP_FRACTION) {
                    lowHp[payload.side] = Hp(at, payload.filledFraction, article.id.value)
                    if (previous == null) {
                        request(payload.side, TerminalVisualTrigger.LOW_HP_TRANSITION, at, listOf(article.id.value))
                    }
                } else lowHp.remove(payload.side)
            }
            is FastMoveUseObserved -> request(payload.damagedSide, TerminalVisualTrigger.FAST_MOVE_WITH_LOW_HP,
                at, listOf(article.id.value))
            else -> Unit
        }
    }

    private fun appearanceBoundary(side: ActivePokemonSide, at: Long) {
        if (!accepting || at < sessionAt || at > now()) return
        boundaries[side] = maxOf(boundaries[side] ?: Long.MIN_VALUE, at)
        lowHp[side]?.takeIf { it.at <= at }?.let { lowHp.remove(side) }
        windows.values.filter { it.side == side && it.from <= at }.forEach { it.closed = true }
        val current = charge
        if (current?.qteAt != null && current.qteAt <= at) {
            retireQte(); charge = null
        } else if (current != null && current.qteAt == null && current.at <= at) charge = null
    }

    private fun expire() { windows.values.filter { now() >= it.until }.forEach { it.closed = true } }
    private fun fresh(at: Long): Boolean = now() - at in 0..MAX_SOURCE_AGE_NANOS
    private fun triggerFresh(at: Long): Boolean = now() - at in 0 until WINDOW_NANOS

    companion object {
        const val WINDOW_NANOS = 3_000_000_000L
        const val FRAME_INTERVAL_NANOS = 100_000_000L
        const val MAX_FRAMES = 30
        const val QTE_WINDOW_NANOS = 8_500_000_000L
        const val QTE_FRAME_INTERVAL_NANOS = 250_000_000L
        const val QTE_MAX_FRAMES = 34
        const val MAX_SOURCE_AGE_NANOS = 500_000_000L
        const val LOW_HP_FRESH_NANOS = 1_000_000_000L
        const val COOLDOWN_NANOS = 1_000_000_000L
        private const val PUBLICATION_GRACE_NANOS = 2_000_000_000L
        private const val MAX_CHARGE_PHASE_NANOS = 20_000_000_000L
        private const val LOW_HP_FRACTION = .25f
        private val player = ActivePokemonSide.PLAYER
        private val opponent = ActivePokemonSide.OPPONENT
        private val LOW_HP_REQUIRED = setOf(TerminalVisualTrigger.LOW_HP_TRANSITION,
            TerminalVisualTrigger.FAST_MOVE_WITH_LOW_HP, TerminalVisualTrigger.INDEPENDENT_IMPACT_WITH_LOW_HP)
    }
}
