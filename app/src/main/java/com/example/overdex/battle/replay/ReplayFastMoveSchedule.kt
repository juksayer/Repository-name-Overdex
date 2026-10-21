package com.example.overdex.battle.replay

import com.example.overdex.model.PokemonType
import kotlin.math.abs

/** Input times are impact witnesses, not animation starts. Never rewrite their Articles. */
internal data class ReplayFastMoveWitness(
    val side: String,
    val observedAtNanos: Long,
    val moveName: String?,
    val type: PokemonType?,
    val durationNanos: Long?,
    val windowStartNanos: Long,
    val windowEndNanos: Long,
    val evidenceKinds: Set<String>,
    val articleIds: Set<String>,
    val sourceTimesNanos: Set<Long>,
    val damageTicks: List<ReplayFastMoveDamage> = emptyList(),
)

/** Settled damage on the recipient, linked by the use's source Article IDs. */
internal data class ReplayFastMoveDamage(
    val observedAtNanos: Long,
    val before: Float,
    val after: Float,
)

internal data class ReplayFastMoveCycle(
    val witness: ReplayFastMoveWitness,
    val startedAtNanos: Long,
    val impactAtNanos: Long,
) {
    val durationNanos get() = impactAtNanos - startedAtNanos
    val hasReferenceDuration get() = witness.durationNanos != null
    val visibleUntilNanos get() = minOf(witness.windowEndNanos,
        impactAtNanos + if (hasReferenceDuration) IMPACT_HOLD_NANOS else 0L)

    fun actionAt(cursor: Long): ReplayFastMoveAction? {
        if (cursor !in startedAtNanos until visibleUntilNanos) return null
        return ReplayFastMoveAction(
            side = witness.side, moveName = witness.moveName, type = witness.type,
            startedAtNanos = startedAtNanos,
            progress = ((cursor - startedAtNanos).toFloat() / durationNanos).coerceIn(0f, 1f),
            evidenceKinds = witness.evidenceKinds,
            durationNanos = durationNanos, impactAtNanos = impactAtNanos,
            sourceArticleIds = witness.articleIds, observedAtNanos = witness.sourceTimesNanos,
            timingReconstructed = hasReferenceDuration,
        )
    }

    /** Associate the bar's short draining tail with the same hit, not a second attack. */
    fun coversHpSample(at: Long): Boolean = hasReferenceDuration &&
        at >= startedAtNanos && at < witness.windowEndNanos &&
        at >= (witness.sourceTimesNanos.minOrNull() ?: witness.observedAtNanos) - HP_SETTLE_NANOS &&
        at <= (witness.sourceTimesNanos.maxOrNull() ?: witness.observedAtNanos) + HP_SETTLE_NANOS

    companion object {
        private const val IMPACT_HOLD_NANOS = 120_000_000L
        private const val HP_SETTLE_NANOS = 350_000_000L
    }
}

/** A bounded replay reconstruction. Missing observations never create extra move cycles. */
internal object ReplayFastMoveSchedule {
    fun build(witnesses: List<ReplayFastMoveWitness>): List<ReplayFastMoveCycle> =
        witnesses.groupBy { it.side }.values.flatMap { sideWitnesses ->
            val cycles = mutableListOf<ReplayFastMoveCycle>()
            for (witness in sideWitnesses.sortedBy { it.observedAtNanos }) {
                val duration = witness.durationNanos
                if (duration == null) {
                    // Old/undetermined moves retain their short neutral presentation.
                    cycles += ReplayFastMoveCycle(witness, witness.observedAtNanos,
                        witness.observedAtNanos + UNKNOWN_MOVE_VISUAL_NANOS)
                    continue
                }
                val previous = cycles.lastOrNull()?.takeIf {
                    it.witness.windowStartNanos == witness.windowStartNanos &&
                        it.witness.windowEndNanos == witness.windowEndNanos &&
                        it.witness.moveName == witness.moveName && it.durationNanos == duration
                }
                // Allow capture jitter around one cycle, but never queue a burst of
                // impossible uses to play later during an unobserved/idle interval.
                val tolerance = minOf(400_000_000L, duration / 4)
                val expected = previous?.impactAtNanos?.plus(duration)
                // Confirmation can lag behind the actual hit by different amounts.
                // In the Oct 13 recording, two settled Incinerate losses (99->74,
                // 74->47 percent) were confirmed only 1.63 seconds apart. Cadence
                // alone must not collapse those independent losses into one hit.
                val separateDamage = previous != null && expected != null &&
                    // Separate settled losses can correct confirmation jitter,
                    // but cannot queue intermediate draining levels several
                    // moves into the future. Bound reconstruction to half a cycle.
                    expected - witness.observedAtNanos <= duration / 2 &&
                    continuesDamageSequence(previous.witness, witness, duration)
                if (expected != null && witness.observedAtNanos < expected - tolerance && !separateDamage) {
                    cycles[cycles.lastIndex] = previous.copy(witness = previous.witness.copy(
                        evidenceKinds = previous.witness.evidenceKinds + witness.evidenceKinds,
                        articleIds = previous.witness.articleIds + witness.articleIds,
                        sourceTimesNanos = previous.witness.sourceTimesNanos + witness.sourceTimesNanos,
                        damageTicks = (previous.witness.damageTicks + witness.damageTicks).distinct(),
                    ))
                    continue
                }
                val impact = if (expected != null &&
                    (abs(witness.observedAtNanos - expected) <= tolerance ||
                        (separateDamage && witness.observedAtNanos < expected))) expected
                    else maxOf(witness.observedAtNanos, witness.windowStartNanos + duration)
                // Both combatants must remain on the field for this cycle. Charge
                // sequences, switches, faints and match boundaries split windows.
                if (impact > witness.windowEndNanos) continue
                cycles += ReplayFastMoveCycle(witness, impact - duration, impact)
            }
            cycles
        }.sortedBy { it.startedAtNanos }

    private fun continuesDamageSequence(
        previous: ReplayFastMoveWitness,
        current: ReplayFastMoveWitness,
        duration: Long,
    ): Boolean {
        val before = previous.damageTicks.maxByOrNull { it.observedAtNanos } ?: return false
        return current.damageTicks.any { after ->
            // A bar's short draining tail is still one hit. Separate settled
            // losses must join at the intervening HP level, not repeat it.
            after.observedAtNanos - before.observedAtNanos >= minOf(350_000_000L, duration / 2) &&
                abs(after.before - before.after) <= .015f &&
                after.after < before.after - .006f
        }
    }

    private const val UNKNOWN_MOVE_VISUAL_NANOS = 450_000_000L
}
