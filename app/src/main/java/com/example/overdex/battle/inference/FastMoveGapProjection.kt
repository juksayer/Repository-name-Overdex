package com.example.overdex.battle.inference

import com.example.overdex.model.Move
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Reference-mechanics projection across a timed interval without visual coverage.
 * It reports a range and never substitutes for an observed HP or energy measurement.
 */
data class FastMoveGapProjection(
    val moveName: String,
    val elapsedNanos: Long,
    val minimumCompletedUses: Int,
    val maximumCompletedUses: Int,
    val minimumEnergyGain: Int,
    val maximumEnergyGain: Int,
    val minimumBaseDamage: Int,
    val maximumBaseDamage: Int
)

object FastMoveGapProjector {
    private const val TURN_NANOS = 500_000_000L

    fun project(move: Move, elapsedNanos: Long): FastMoveGapProjection {
        require(move.isFast) { "Gap projection requires a fast move." }
        val durationNanos = requireNotNull(move.turns) { "Fast move duration is required." } * TURN_NANOS
        require(durationNanos > 0L)
        val elapsed = elapsedNanos.coerceAtLeast(0L)
        // The interval can begin and end at arbitrary points in the move cycle.
        val minimum = floor(elapsed.toDouble() / durationNanos).toInt()
        val maximum = ceil(elapsed.toDouble() / durationNanos).toInt()
        return FastMoveGapProjection(
            moveName = move.name,
            elapsedNanos = elapsed,
            minimumCompletedUses = minimum,
            maximumCompletedUses = maximum,
            minimumEnergyGain = minimum * move.energy,
            maximumEnergyGain = maximum * move.energy,
            minimumBaseDamage = minimum * move.damage,
            maximumBaseDamage = maximum * move.damage
        )
    }
}
