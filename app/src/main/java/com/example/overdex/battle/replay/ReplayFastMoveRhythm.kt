package com.example.overdex.battle.replay

/** Extra animation beats for completed Replay. No projected cycle is evidence for a subsequent one. */
internal object ReplayFastMoveRhythm {
    const val INFERRED = "INFERRED_FAST_MOVE_RHYTHM"
    const val HP_RECEPTION = "INFERRED_RHYTHM_AT_HP_RECEPTION"
    private const val TURN = 500_000_000L
    // Imported archives may contain corrupt clocks. An implausible gap cannot allocate unbounded animations.
    private const val MAX_PREDICTIONS = 8_192

    fun present(
        supported: List<ReplayFastMoveCycle>,
        startNanos: Long,
        endNanos: Long,
        resetAtNanos: List<Long>,
        chargeWindows: List<LongRange>,
        canPredict: (side: String, start: Long, impact: Long) -> Boolean,
        contradicted: (anchor: ReplayFastMoveCycle, landing: Long, windowStart: Long, windowEnd: Long) -> Boolean,
        fixedImpact: (ReplayFastMoveCycle) -> Boolean = { false },
        oneReception: (ReplayFastMoveCycle, Long, Long, Long, Long) -> Long? = { _, _, _, _, _ -> null },
    ): List<ReplayFastMoveCycle> {
        if (endNanos <= startNanos || endNanos == Long.MAX_VALUE) return supported
        val resets = (resetAtNanos.filter { it in startNanos until endNanos } + startNanos + endNanos).distinct().sorted()
        val charges = chargeWindows.filter { !it.isEmpty() && it.last >= startNanos && it.first < endNanos }
        // Each interval is ordinary combat, [start, end). Only a charge gap can carry an
        // anchor across intervals. A hard cut splits even two otherwise adjoining intervals.
        val cuts = (resets + charges.flatMap {
            listOf(maxOf(startNanos, it.first), minOf(endNanos - 1, it.last) + 1)
        }).distinct().sorted()
        val ordinary = cuts.zipWithNext().filter { (from, _) -> charges.none { from in it } }
        val result = mutableListOf<ReplayFastMoveCycle>()
        var inferredCount = 0
        for ((side, uses) in supported.groupBy { it.witness.side }) {
            val ordered = uses.sortedWith(compareBy({ it.impactAtNanos }, { it.startedAtNanos })).toMutableList()
            // Short-gap timing is already resolved by ReplayFastMoveSchedule. For a
            // longer gap, align only an unmeasured use within an uninterrupted rhythm.
            var rephased = false
            for (i in 1 until ordered.size) {
                val previous = ordered[i - 1]
                val use = ordered[i]
                val window = ordinary.firstOrNull { (from, until) ->
                    previous.startedAtNanos >= from && use.impactAtNanos < until &&
                        previous.impactAtNanos < use.impactAtNanos
                }
                val aligned = if (window == null) use else alignUse(
                    previous, use, ordered.getOrNull(i + 1), window, rephased, canPredict, contradicted, fixedImpact)
                rephased = aligned !== use
                ordered[i] = aligned
            }
            result += ordered
            for ((index, anchor) in ordered.withIndex()) {
                val witness = anchor.witness
                val duration = witness.durationNanos ?: continue
                if (witness.moveName.isNullOrBlank() || duration !in TURN..10 * TURN ||
                    duration % TURN != 0L || witness.articleIds.isEmpty() || INFERRED in witness.evidenceKinds ||
                    anchor.impactAtNanos !in startNanos until endNanos) continue
                val hardEnd = resets.first { it > anchor.impactAtNanos }
                val previousReset = resets.last { it <= anchor.impactAtNanos }
                if (anchor.startedAtNanos < previousReset) continue // Outgoing hit at an appearance boundary.
                val next = ordered.getOrNull(index + 1)
                val occupiedFrom = next?.startedAtNanos ?: endNanos
                val tolerance = minOf(400_000_000L, duration / 4)
                interval@ for ((from, until) in ordinary) {
                    if (until <= anchor.impactAtNanos) continue
                    if (from >= hardEnd || from >= occupiedFrom) break
                    var start = maxOf(from, anchor.impactAtNanos)
                    var lastProjection: ReplayFastMoveCycle? = null
                    while (start < occupiedFrom && start <= until - duration) {
                        val expected = start + duration
                        if (expected >= until) break // Do not infer a hit on a switch, charge or final boundary.
                        // A real hit reserves its own flight. Nearby capture jitter may
                        // shorten only the preceding decorative flight, just as it does
                        // for the existing supported schedule. Off-beat uses reset phase.
                        if (expected > occupiedFrom && expected - occupiedFrom > tolerance) break@interval
                        if (next != null && next.impactAtNanos >= expected &&
                            next.impactAtNanos - expected <= tolerance) break@interval
                        val landing = minOf(expected, occupiedFrom)
                        if (!canPredict(side, start, landing)) break@interval
                        val previous = lastProjection
                        val edge = previous?.let { oneReception(anchor, it.impactAtNanos, landing, from, until) }
                        if (previous != null && edge != null && edge in previous.impactAtNanos..landing) {
                            // Two expected uses cannot fit the observed damage. Keep one
                            // decoration at the first measured new edge, then await a fresh use.
                            val resolved = previous.witness.copy(evidenceKinds = setOf(INFERRED, HP_RECEPTION))
                            result[result.lastIndex] = ReplayFastMoveCycle(resolved, edge - duration, edge, timingAdjusted = true)
                            break@interval
                        }
                        if (contradicted(anchor, landing, from, until)) break@interval
                        val presentation = witness.copy(
                            windowStartNanos = from, windowEndNanos = until,
                            evidenceKinds = setOf(INFERRED), articleIds = emptySet(), sourceTimesNanos = emptySet(),
                            damageTicks = emptyList(), archivedUseWithDamage = false, endsAtTerminalBoundary = false,
                        )
                        if (++inferredCount > MAX_PREDICTIONS) return supported
                        lastProjection = ReplayFastMoveCycle(presentation, start, landing, timingAdjusted = landing != expected)
                        result += lastProjection
                        if (landing == occupiedFrom) break@interval
                        start = landing
                    }
                }
            }
        }
        return result.sortedWith(compareBy({ it.impactAtNanos }, { it.witness.side }))
    }

    private fun alignUse(
        previous: ReplayFastMoveCycle,
        use: ReplayFastMoveCycle,
        following: ReplayFastMoveCycle?,
        window: Pair<Long, Long>,
        continuingCorrection: Boolean,
        eligible: (String, Long, Long) -> Boolean,
        contradicted: (ReplayFastMoveCycle, Long, Long, Long) -> Boolean,
        fixedImpact: (ReplayFastMoveCycle) -> Boolean,
    ): ReplayFastMoveCycle {
        val witness = use.witness
        val duration = witness.durationNanos ?: return use
        if (duration !in TURN..10 * TURN || duration % TURN != 0L ||
            duration != previous.witness.durationNanos || witness.moveName.isNullOrBlank() ||
            witness.moveName != previous.witness.moveName || witness.articleIds.isEmpty() ||
            previous.witness.articleIds.isEmpty() || witness.damageTicks.isNotEmpty() ||
            witness.archivedUseWithDamage || witness.endsAtTerminalBoundary || fixedImpact(use)) return use
        if (!continuingCorrection && (use.timingAdjusted || witness.observedAtNanos != use.impactAtNanos)) return use
        // Round the positive interval to the nearest whole cycle. Only a full,
        // uninterrupted span of eligible intervening beats can maintain phase.
        val gap = witness.observedAtNanos - previous.impactAtNanos
        if (gap <= 0 || gap > duration * MAX_PREDICTIONS) return use
        val beats = (gap + duration / 2) / duration
        if (beats < (if (continuingCorrection) 1 else 4)) return use
        val correction = beats * duration - gap
        if (kotlin.math.abs(correction) > minOf(400_000_000L, duration / 4)) return use
        val impact = witness.observedAtNanos + correction
        if (impact == use.impactAtNanos) return use
        val start = impact - duration
        val (from, until) = window
        if (impact !in from until until || start < witness.windowStartNanos || impact > witness.windowEndNanos ||
            (following != null && impact > following.startedAtNanos)) return use
        var beatStart = previous.impactAtNanos
        repeat(beats.toInt()) {
            val landing = beatStart + duration
            if (!eligible(witness.side, beatStart, landing) || contradicted(previous, landing, from, until)) return use
            beatStart = landing
        }
        return use.copy(startedAtNanos = start, impactAtNanos = impact, timingAdjusted = true)
    }
}
