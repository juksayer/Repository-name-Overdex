package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import kotlin.math.abs

/**
 * Scheduling only: a missing bar requests independent counting, never a faint or recall.
 * Charge-camera geometry cannot seed a normal-field reference. An immediate post-charge
 * KO with no ordinary nonzero bar return intentionally requires another real departure
 * witness (or the independent zero-HP reader); this policy cannot establish it.
 */
internal class HpBarDepartureCheckPolicy(private val now: () -> Long = System::nanoTime) {
    private data class Seen(val bar: ActiveHpBarMeasurement, val at: Long)
    private data class Missing(val from: Long, var consumed: Boolean = false)
    private val seen = mutableMapOf<ActivePokemonSide, Seen>()
    private val missing = mutableMapOf<ActivePokemonSide, Missing>()
    private val checkedAt = mutableMapOf<ActivePokemonSide, Long>()
    private val boundaries = mutableMapOf<ActivePokemonSide, Long>()
    private var dimensions: Pair<Int, Int>? = null
    private var latestFrameAt = Long.MIN_VALUE
    private var latestPhaseAt = Long.MIN_VALUE
    private var phaseUntil = Long.MIN_VALUE
    private var impactAt: Long? = null
    private var stopped = false

    @Synchronized fun observe(
        width: Int, height: Int, at: Long, located: HpBarFrameDiagnostics,
        canObserve: (BattleCropBounds) -> Boolean,
    ): Set<ActivePokemonSide> {
        val current = now()
        if (stopped || width <= 0 || height <= 0 || at <= latestFrameAt || at > current ||
            current - at > MAX_AGE_NANOS) return emptySet()
        latestFrameAt = at
        val size = width to height
        if (dimensions != null && dimensions != size) { seen.clear(); missing.clear(); checkedAt.clear() }
        dimensions = size
        fun normal(side: ActivePokemonSide): ActiveHpBarMeasurement? = located.measurements[side]?.takeIf { bar ->
            located.outcomes[side] == HpBarLocatorOutcome.FOUND && bar.filledFraction in 0f..1f &&
                bar.left >= 0 && bar.right <= width && bar.top >= 0 && bar.bottom <= height &&
                bar.right > bar.left && bar.bottom > bar.top &&
                (if (side == ActivePokemonSide.PLAYER) bar.centerX < width * .40f else bar.centerX > width * .60f) &&
                canObserve(bar.bounds())
        }
        val normal = ActivePokemonSide.entries.mapNotNull { side -> normal(side)?.let { side to it } }.toMap()
        if (at < phaseUntil) {
            // A used announcement alone does not prove normal camera return.
            if (impactAt?.let { at >= it } != true || normal.size != ActivePokemonSide.entries.size) return emptySet()
            phaseUntil = Long.MIN_VALUE
            impactAt = null
        }
        val requested = linkedSetOf<ActivePokemonSide>()
        ActivePokemonSide.entries.forEach { side ->
            if (at <= (boundaries[side] ?: Long.MIN_VALUE)) return@forEach
            val bar = normal[side]
            if (bar != null) {
                missing.remove(side)
                if (bar.filledFraction > 0f) seen[side] = Seen(bar, at) else seen.remove(side)
                return@forEach
            }
            val previous = seen[side]
            val other = if (side == ActivePokemonSide.PLAYER) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER
            val absent = when (located.outcomes[side]) {
                HpBarLocatorOutcome.NO_CANDIDATE -> true
                HpBarLocatorOutcome.UNASSIGNED_CANDIDATES -> previous != null && located.candidates?.none { candidate ->
                    compatible(previous.bar, candidate)
                } == true
                else -> false
            }
            if (previous == null || at - previous.at !in 0..MAX_AGE_NANOS || normal[other] == null ||
                !absent || !canObserve(previous.bar.bounds())) {
                missing.remove(side)
                return@forEach
            }
            val absence = missing.getOrPut(side) { Missing(at) }
            if (!absence.consumed && at - absence.from >= MIN_MISSING_NANOS &&
                checkedAt[side]?.let { at - it < COOLDOWN_NANOS } != true) {
                absence.consumed = true
                checkedAt[side] = at
                requested += side
            }
        }
        return requested
    }

    @Synchronized fun prepare(at: Long) {
        if (!phaseCueAllowed(at)) return
        latestPhaseAt = at
        seen.clear(); missing.clear()
        phaseUntil = at + MAX_PHASE_NANOS
        impactAt = null
    }
    @Synchronized fun impact(at: Long) {
        if (!phaseCueAllowed(at)) return
        latestPhaseAt = at
        seen.clear(); missing.clear()
        phaseUntil = minOf(if (phaseUntil > at) phaseUntil else at + IMPACT_TAIL_NANOS, at + IMPACT_TAIL_NANOS)
        impactAt = at
    }
    private fun phaseCueAllowed(at: Long): Boolean = !stopped && at > latestPhaseAt && at <= now() &&
        now() - at < MAX_PHASE_NANOS && boundaries.values.none { at <= it }

    @Synchronized fun reset(side: ActivePokemonSide, at: Long) {
        if (at < (boundaries[side] ?: Long.MIN_VALUE)) return
        boundaries[side] = at
        seen.remove(side); missing.remove(side)
    }
    @Synchronized fun resetAll(at: Long) { ActivePokemonSide.entries.forEach { reset(it, at) } }
    @Synchronized fun stop(at: Long) { resetAll(at); stopped = true; phaseUntil = Long.MIN_VALUE; impactAt = null }
    @Synchronized fun begin(at: Long = Long.MIN_VALUE) {
        seen.clear(); missing.clear(); checkedAt.clear(); boundaries.clear()
        dimensions = null; latestFrameAt = Long.MIN_VALUE; latestPhaseAt = Long.MIN_VALUE
        phaseUntil = Long.MIN_VALUE; impactAt = null; stopped = false
        resetAll(at)
    }
    private fun compatible(previous: ActiveHpBarMeasurement, candidate: ActiveHpBarMeasurement): Boolean =
        ActiveHpBarGeometry.matchesSize(candidate.width, candidate.bottom - candidate.top,
            previous.width, previous.bottom - previous.top) &&
            abs(candidate.centerX - previous.centerX) <= previous.width * .5f &&
            abs(candidate.top - previous.top) <= previous.width * .5f + (previous.bottom - previous.top)

    private fun ActiveHpBarMeasurement.bounds() = BattleCropBounds(left, top, right, bottom)
    companion object {
        const val MAX_AGE_NANOS = 500_000_000L
        const val MIN_MISSING_NANOS = 80_000_000L
        const val COOLDOWN_NANOS = 3_000_000_000L
        private const val MAX_PHASE_NANOS = 15_000_000_000L
        private const val IMPACT_TAIL_NANOS = 3_000_000_000L
    }
}
