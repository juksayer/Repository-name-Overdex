package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide

/**
 * Keeps actual recipient pixels flowing for three seconds after measured camera
 * return, even if the HP outline disappears. Anchors locate pixels, never copy HP.
 */
internal class PostChargeRecipientWatchPolicy {
    data class Request(val side: ActivePokemonSide, val anchor: ActiveHpBarMeasurement, val generation: Long)
    private var preparedAt = Long.MIN_VALUE
    private var impactAt: Long? = null
    private var cameraSeen = false
    private var pendingUntil = Long.MIN_VALUE
    private var firstPairAt: Long? = null
    private var until = Long.MIN_VALUE
    private var latestFrame = Long.MIN_VALUE
    private var size: Pair<Int, Int>? = null
    private var generation = 0L
    private val anchors = mutableMapOf<ActivePokemonSide, ActiveHpBarMeasurement>()

    @Synchronized fun prepare(at: Long) {
        if (at <= preparedAt) return
        clear()
        preparedAt = at
        pendingUntil = at + MAX_PHASE_NANOS
    }

    @Synchronized fun impact(at: Long) {
        if (at < preparedAt) return
        // A repeated used-move cue cannot start a second tail after this one.
        // A genuinely new phase is opened by prepare, not by repeated OCR.
        if (impactAt?.let { at >= it } == true) return
        if (preparedAt == Long.MIN_VALUE || at > pendingUntil) prepare(at)
        impactAt = at
        pendingUntil = minOf(preparedAt + MAX_PHASE_NANOS, at + WINDOW_NANOS)
    }

    @Synchronized fun cameraMeasured(at: Long) {
        if (at >= preparedAt && at <= pendingUntil) cameraSeen = true
    }

    /** Returns true once for a new, independently measured two-bar return. */
    @Synchronized fun observe(width: Int, height: Int, at: Long,
        measurements: Map<ActivePokemonSide, ActiveHpBarMeasurement>): Boolean {
        if (at <= latestFrame) return false
        latestFrame = at
        val currentSize = width to height
        if (size != null && size != currentSize) { clear(); preparedAt = at }
        size = currentSize
        if (at <= preparedAt || at >= maxOf(pendingUntil, until)) return false
        val pair = ActivePokemonSide.entries.associateWith { side -> measurements[side]?.takeIf { bar ->
            bar.filledFraction > 0f && bar.left >= 0 && bar.right <= width && bar.top >= 0 && bar.bottom < height &&
                (if (side == ActivePokemonSide.PLAYER) bar.centerX < width * .4f else bar.centerX > width * .6f)
        } }
        if (pair.values.any { it == null }) { if (until == Long.MIN_VALUE) firstPairAt = null; return false }
        if (until != Long.MIN_VALUE) {
            pair.forEach { (side, bar) -> anchors[side] = requireNotNull(bar) }
            return false
        }
        // A late/missing announcement can be replaced by measured charge-camera
        // geometry. Ordinary bars seen before a camera cut are not a return.
        if (!cameraSeen && impactAt?.let { at > it + IMPACT_GUARD_NANOS } != true) return false
        val first = firstPairAt
        if (first == null || at - first > MAX_PAIR_GAP_NANOS) { firstPairAt = at; return false }
        if (at - first < MIN_PAIR_INTERVAL_NANOS) return false
        pair.forEach { (side, bar) -> anchors[side] = requireNotNull(bar) }
        until = at + WINDOW_NANOS
        return true
    }

    @Synchronized fun requests(at: Long): List<Request> =
        if (at < latestFrame || at >= until) emptyList()
        else anchors.map { (side, bar) -> Request(side, bar, generation) }

    @Synchronized fun current(request: Request, at: Long) = request.generation == generation && at < until

    @Synchronized fun reset(at: Long) {
        if (at < preparedAt) return
        clear()
        preparedAt = maxOf(preparedAt, at)
    }

    @Synchronized fun begin() {
        clear(); preparedAt = Long.MIN_VALUE; latestFrame = Long.MIN_VALUE; size = null
    }

    private fun clear() {
        generation++
        impactAt = null; cameraSeen = false; pendingUntil = Long.MIN_VALUE
        firstPairAt = null; until = Long.MIN_VALUE; anchors.clear()
    }

    companion object {
        const val WINDOW_NANOS = 3_000_000_000L
        private const val MAX_PHASE_NANOS = 15_000_000_000L
        private const val IMPACT_GUARD_NANOS = 750_000_000L
        private const val MIN_PAIR_INTERVAL_NANOS = 80_000_000L
        private const val MAX_PAIR_GAP_NANOS = 500_000_000L
    }
}
