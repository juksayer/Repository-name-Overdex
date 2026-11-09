package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
import com.example.overdex.battle.custody.ObservationSessionStoppedByUser
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.model.AnchorRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QteIconSamplingStatus(val sampling: Boolean = false, val saved: Int = 0)

/** A bounded raw calibration sampler; additional experiments can use manual-only attention. */
class QteTypeIconSampling(
    private val savedRegion: () -> AnchorRegion,
    private val now: () -> Long = System::nanoTime,
    private val followsQte: Boolean = true,
    private val sampleLimit: Int = MAX_SAMPLES,
    private val settleNanos: Long = 0L,
    override val observerId: ObserverId = ObserverId("QTE_TYPE_ICON_SAMPLE_GATE", ObservationSource.SCREEN_CAPTURE),
    override val name: String = "QTE Type Icon Sample Gate",
) : Observer {
    init { require(sampleLimit > 0); require(settleNanos in 0 until WINDOW_NANOS) }
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var expiry: Job? = null
    private var accepting = false
    private var from = Long.MIN_VALUE
    private var until = Long.MIN_VALUE
    private var lastReady = Long.MIN_VALUE
    private var lastClosedAt = Long.MIN_VALUE
    @Volatile var region: AnchorRegion? = null
        private set
    private val _status = MutableStateFlow(QteIconSamplingStatus())
    val status = _status.asStateFlow()

    /** Manual samples use the visible draft without saving or moving any other box. */
    @Synchronized fun sample(draft: AnchorRegion): Boolean {
        if (!accepting || isEnabled()) return false
        open(now(), draft)
        return true
    }

    @Synchronized fun isEnabled(): Boolean {
        val enabled = accepting && now() < until && _status.value.saved < sampleLimit
        if (_status.value.sampling != enabled) _status.value = _status.value.copy(sampling = enabled)
        return enabled
    }

    /** Reject a replayed source frame from before the request, even if it arrives later. */
    @Synchronized fun acceptsFrame(capturedAt: Long): Boolean =
        isEnabled() && capturedAt >= from && capturedAt < until

    @Synchronized fun captured(at: Long) {
        if (at < from || at >= until) return
        _status.value = _status.value.copy(saved = _status.value.saved + 1)
        isEnabled()
    }

    @Synchronized internal fun ready(observedAt: Long) {
        if (!accepting || now() - observedAt !in 0 until WINDOW_NANOS) return
        if (observedAt <= lastClosedAt) return
        // Repeated OCR of one cue must not turn a ten-second sample into continuous recording.
        if (lastReady != Long.MIN_VALUE && observedAt - lastReady < WINDOW_NANOS) return
        lastReady = observedAt
        if (!isEnabled()) open(observedAt, savedRegion())
    }

    @Synchronized internal fun closeWindow(observedAt: Long) {
        lastClosedAt = maxOf(lastClosedAt, observedAt)
        if (observedAt >= from) {
            until = minOf(until, observedAt)
            isEnabled()
        }
    }

    @Synchronized internal fun beginSession() {
        accepting = true
        from = Long.MIN_VALUE
        until = Long.MIN_VALUE
        lastReady = Long.MIN_VALUE
        lastClosedAt = Long.MIN_VALUE
        region = null
        _status.value = QteIconSamplingStatus()
    }

    @Synchronized internal fun endSession() {
        expiry?.cancel()
        accepting = false
        until = Long.MIN_VALUE
        _status.value = _status.value.copy(sampling = false)
    }

    private fun open(at: Long, area: AnchorRegion) {
        from = at + settleNanos
        until = at + WINDOW_NANOS
        region = area
        _status.value = QteIconSamplingStatus(sampling = true)
        expiry?.cancel()
        expiry = scope?.launch {
            delay(((until - now()).coerceAtLeast(0L) + 999_999L) / 1_000_000L)
            isEnabled()
        }
    }

    internal fun observe(payload: TestimonyPayload, at: Long?) {
        when (payload) {
            GetReadyWitnessed -> if (followsQte) at?.let(::ready)
            ChargeMoveUsedAnnounced -> if (followsQte) at?.let(::closeWindow)
            is MatchEnded, ScreenIgnoredByUser, ObservationSessionStoppedByUser -> endSession()
            ObservationRestartedByUser -> beginSession()
            else -> Unit
        }
    }

    override fun start(match: Match) {
        if (scope != null) return
        beginSession()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    observe(article.payload, article.monotonicTimeNanos)
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("QTE sampling stopped")
        scope = null
        endSession()
    }

    companion object {
        const val WINDOW_NANOS = 10_000_000_000L
        const val CAPTURE_INTERVAL_NANOS = 125_000_000L
        const val MAX_SAMPLES = 80
    }
}
