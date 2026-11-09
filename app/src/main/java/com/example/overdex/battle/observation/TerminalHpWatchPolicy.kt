package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import kotlin.math.abs
import kotlin.math.max

/** One source-frame reading. Its token expires when combatant/geometry ownership resets. */
internal data class TerminalHpWatchResult(
    val side: ActivePokemonSide,
    val sample: TrackedTerminalHpBarSample,
    val generation: Long,
)

/**
 * Bounds terminal follow-up to a fresh, ordinarily located, side-owned bar.
 *
 * Only an actual RED sample at a small fraction arms the watch; a visibly EMPTY
 * sample can then be accepted once. Missing pixels never become zero. Checked
 * frame progress is separate from accepted measurement progress: a slow ordinary
 * locator may seed a still-fresh bar while quick checks continue seeing nothing.
 * This class owns no images, clocks, battle inference, or frame queues.
 */
internal class TerminalHpWatchPolicy {
    private data class State(
        var generation: Long = 0L,
        var anchor: ActiveHpBarMeasurement? = null,
        var anchorAt: Long? = null,
        var ordinarySeedAt: Long? = null,
        var acceptedAt: Long? = null,
        var boundaryAt: Long? = null,
        var burstStartedAt: Long? = null,
        var reseedAfter: Long? = null,
    )

    private val states = ActivePokemonSide.entries.associateWith { State() }
    private var dimensions: Pair<Int, Int>? = null
    private var dimensionStartedAt: Long? = null
    private var latestDomainSourceAt: Long? = null
    private var lastCheckedAt: Long? = null

    @Synchronized
    fun snapshotGeneration(side: ActivePokemonSide): Long = states.getValue(side).generation

    @Synchronized
    fun isCurrentGeneration(side: ActivePokemonSide, generation: Long): Boolean =
        states.getValue(side).generation == generation

    /**
     * Seed freshness bounds future fast sampling, not whether an ordinary
     * measurement truthfully occurred. The caller separately checks generation
     * and its last published source time before publishing this older evidence.
     */
    @Synchronized
    fun canPublishOrdinary(side: ActivePokemonSide, sourceAt: Long): Boolean {
        val state = states.getValue(side)
        return state.boundaryAt?.let { sourceAt <= it } != true &&
            state.reseedAfter?.let { sourceAt <= it } != true
    }

    /** Check the ordinary locator's pre-location generation before calling this method. */
    @Synchronized
    fun seed(
        side: ActivePokemonSide,
        measurement: ActiveHpBarMeasurement,
        width: Int,
        height: Int,
        sourceAt: Long,
    ): Boolean {
        if (!validPositive(measurement, width, height) || !admitDimensions(width, height, sourceAt)) return false
        val state = states.getValue(side)
        if (state.acceptedAt?.let { sourceAt < it } == true ||
            state.ordinarySeedAt?.let { sourceAt <= it } == true ||
            state.boundaryAt?.let { sourceAt <= it } == true ||
            state.reseedAfter?.let { sourceAt <= it } == true ||
            lastCheckedAt?.let { it - sourceAt > MAX_ANCHOR_AGE_NANOS } == true
        ) return false
        val previous = state.anchor
        if (previous != null && !near(previous, measurement)) {
            state.generation++
            clearWatch(state)
        }
        state.anchor = measurement
        state.anchorAt = sourceAt
        state.ordinarySeedAt = sourceAt
        state.acceptedAt = sourceAt
        state.reseedAfter = null
        if (measurement.filledFraction > MAX_RED_FRACTION) state.burstStartedAt = null
        return true
    }

    /** Evaluates at most two tiny rectangles once per increasing source frame. */
    @Synchronized
    fun checkFrame(
        width: Int,
        height: Int,
        sourceAt: Long,
        measure: (ActivePokemonSide, ActiveHpBarMeasurement) -> TrackedTerminalHpBarSample?,
    ): List<TerminalHpWatchResult> {
        if (lastCheckedAt?.let { sourceAt <= it } == true || !admitDimensions(width, height, sourceAt)) return emptyList()
        lastCheckedAt = sourceAt
        val results = mutableListOf<TerminalHpWatchResult>()
        states.forEach { (side, state) ->
            if (state.acceptedAt?.let { sourceAt < it } == true || state.boundaryAt?.let { sourceAt <= it } == true) return@forEach
            val anchor = freshAnchor(state, sourceAt) ?: return@forEach
            val sample = measure(side, anchor) ?: return@forEach
            if (!validMeasurement(sample.measurement, width, height) || !near(anchor, sample.measurement)) {
                clearWatch(state)
                return@forEach
            }
            when (sample.fillColor) {
                HpBarFillColor.RED -> {
                    if (sample.measurement.filledFraction <= 0f || sample.measurement.filledFraction > MAX_RED_FRACTION) {
                        state.burstStartedAt = null
                        return@forEach
                    }
                    if (state.burstStartedAt == null) state.burstStartedAt = sourceAt
                    state.anchor = sample.measurement
                    state.anchorAt = sourceAt
                    state.acceptedAt = sourceAt
                    results += TerminalHpWatchResult(side, sample, state.generation)
                }
                HpBarFillColor.EMPTY -> {
                    if (state.burstStartedAt != null && sample.measurement.filledFraction == 0f) {
                        // Consume the arm without invalidating this result's token.
                        results += TerminalHpWatchResult(side, sample, state.generation)
                        clearWatch(state)
                        state.acceptedAt = sourceAt
                        state.reseedAfter = sourceAt
                    }
                }
                // These readings disarm but do not invalidate an ordinary locator
                // already in flight. Its accepted geometry remains a valid seed.
                HpBarFillColor.GREEN, HpBarFillColor.YELLOW -> state.burstStartedAt = null
            }
        }
        return results
    }

    /** A null sampled frame may keep a fresh arm alive, never past either bound. */
    @Synchronized
    fun isArmed(side: ActivePokemonSide, sourceAt: Long): Boolean {
        val state = states.getValue(side)
        val start = state.burstStartedAt ?: return false
        val at = state.anchorAt ?: return false
        return sourceAt - at in 0..MAX_ANCHOR_AGE_NANOS && sourceAt - start in 0 until MAX_BURST_NANOS
    }

    @Synchronized
    fun armedSides(sourceAt: Long): Set<ActivePokemonSide> =
        ActivePokemonSide.entries.filterTo(linkedSetOf()) { isArmed(it, sourceAt) }

    @Synchronized
    fun reset(side: ActivePokemonSide, boundaryAt: Long? = null) {
        val state = states.getValue(side)
        val cutoff = listOfNotNull(state.acceptedAt, state.boundaryAt, boundaryAt ?: lastCheckedAt).maxOrNull()
        state.generation++
        clearWatch(state)
        state.ordinarySeedAt = null
        state.boundaryAt = cutoff
        state.reseedAfter = null
        cutoff?.let { latestDomainSourceAt = maxOf(latestDomainSourceAt ?: it, it) }
    }

    @Synchronized
    fun resetAll(boundaryAt: Long? = null) {
        ActivePokemonSide.entries.forEach { reset(it, boundaryAt) }
    }

    private fun admitDimensions(width: Int, height: Int, sourceAt: Long): Boolean {
        if (width <= 0 || height <= 0) return false
        val size = width to height
        if (dimensions != null && dimensions != size) {
            // An old asynchronous ordinary result cannot switch the active domain back.
            if (latestDomainSourceAt?.let { sourceAt < it } == true) return false
            states.values.forEach { state ->
                state.generation++
                clearWatch(state)
                state.ordinarySeedAt = null
                state.acceptedAt = null
                state.reseedAfter = null
            }
            dimensionStartedAt = sourceAt
        }
        if (dimensionStartedAt?.let { sourceAt < it } == true) return false
        dimensions = size
        latestDomainSourceAt = maxOf(latestDomainSourceAt ?: sourceAt, sourceAt)
        return true
    }

    private fun freshAnchor(state: State, sourceAt: Long): ActiveHpBarMeasurement? {
        val anchor = state.anchor ?: return null
        val at = state.anchorAt ?: return null
        val burstAt = state.burstStartedAt
        if (sourceAt - at !in 0..MAX_ANCHOR_AGE_NANOS ||
            (burstAt != null && sourceAt - burstAt !in 0 until MAX_BURST_NANOS)
        ) {
            // Sampling expiry is not an ownership reset. Preserve the generation
            // of an ordinary locator that may still finish with fresh geometry.
            clearWatch(state)
            return null
        }
        return anchor
    }

    private fun clearWatch(state: State) {
        state.anchor = null
        state.anchorAt = null
        state.burstStartedAt = null
    }

    private fun near(a: ActiveHpBarMeasurement, b: ActiveHpBarMeasurement): Boolean =
        ActiveHpBarGeometry.matchesSize(b.width, b.bottom - b.top, a.width, a.bottom - a.top) &&
            abs(a.left - b.left) <= max(4, a.width / 25) &&
            abs(a.right - b.right) <= max(4, a.width / 25) &&
            abs(a.top - b.top) <= max(4, a.bottom - a.top)

    private fun validPositive(bar: ActiveHpBarMeasurement, width: Int, height: Int): Boolean =
        validMeasurement(bar, width, height) && bar.filledFraction > 0f

    private fun validMeasurement(bar: ActiveHpBarMeasurement, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && bar.left >= 0 && bar.top >= 0 && bar.right <= width && bar.bottom < height &&
            bar.width >= 32 && bar.bottom - bar.top >= 6 &&
            bar.filledFraction.isFinite() && bar.filledFraction in 0f..1f &&
            bar.confidence.isFinite() && bar.confidence in 0f..1f

    companion object {
        const val MAX_ANCHOR_AGE_NANOS = 500_000_000L
        const val MAX_BURST_NANOS = 3_000_000_000L
        const val MAX_RED_FRACTION = .25f
    }
}
