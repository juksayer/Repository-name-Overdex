package com.example.overdex.ui.components

import com.example.overdex.presentation.DroidballPose
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Finite tap responses; the fifth response rests with closed eyes after its last frame. */
internal object DroidballAcknowledgementMotion {
    const val DURATION_MILLIS = 2320L

    fun durationMillis(tapCount: Int): Long = when {
        tapCount <= 0 -> 0L
        tapCount <= 2 -> DURATION_MILLIS
        tapCount == 3 -> 420L
        tapCount == 4 -> 260L
        else -> 900L
    }

    fun pose(elapsedMillis: Long, tapCount: Int): DroidballPose {
        if (tapCount <= 0) return DroidballPose()
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        if (tapCount >= 5) {
            return DroidballPose(rollDegrees = springTilt(elapsed), blink = 1f)
        }
        if (elapsed >= durationMillis(tapCount)) return DroidballPose()

        val tilt = if (tapCount <= 2) {
            when {
                elapsed < 900L -> springTilt(elapsed)
                elapsed < 1720L -> 15f
                else -> {
                    val progress = (elapsed - 1720L) / 600f
                    val eased = progress * progress * (3f - 2f * progress)
                    15f * (1f - eased)
                }
            }
        } else 0f
        val closed = when (tapCount) {
            1, 2 -> elapsed in 1000L until 1060L || elapsed in 1160L until 1220L
            3 -> elapsed in 100L until 160L || elapsed in 260L until 320L
            4 -> elapsed in 100L until 160L
            else -> false
        }
        return DroidballPose(rollDegrees = tilt, blink = if (closed) 1f else 0f)
    }

    private fun springTilt(elapsed: Long): Float {
        if (elapsed >= 900L) return 15f
        // Preserve the original 15 degree spring: damping ratio 0.6,
        // with Compose's low stiffness of 200 and zero starting velocity.
        val dampingRatio = .6
        val naturalFrequency = sqrt(200.0)
        val dampingFactor = sqrt(1.0 - dampingRatio * dampingRatio)
        val seconds = elapsed / 1000.0
        val angle = naturalFrequency * dampingFactor * seconds
        val envelope = exp(-dampingRatio * naturalFrequency * seconds)
        return (15.0 * (1.0 - envelope *
            (cos(angle) + dampingRatio / dampingFactor * sin(angle)))).toFloat()
    }
}
