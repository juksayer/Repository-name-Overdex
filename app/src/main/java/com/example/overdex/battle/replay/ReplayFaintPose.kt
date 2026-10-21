package com.example.overdex.battle.replay

import kotlin.math.PI
import kotlin.math.sin

/** A pose sampled from Match time: pause, reverse scrub and playback speed need no animation state. */
data class ReplayFaintPose(
    val shakePixels: Float,
    val rotationDegrees: Float,
    val dropPixels: Float,
    val opacity: Float,
    val saturation: Float,
) {
    companion object {
        private const val SHAKE_NANOS = 360_000_000L
        const val FALL_START_NANOS = SHAKE_NANOS + 1_000_000_000L
        const val FALL_END_NANOS = FALL_START_NANOS + 420_000_000L
        const val DURATION_NANOS = FALL_END_NANOS + 350_000_000L

        fun at(faintedAtNanos: Long?, cursorNanos: Long, player: Boolean, finishByNanos: Long? = null): ReplayFaintPose? {
            val elapsed = cursorNanos - (faintedAtNanos ?: return null)
            if (elapsed < 0) return null
            val duration = finishByNanos?.minus(faintedAtNanos)?.coerceIn(1L, DURATION_NANOS) ?: DURATION_NANOS
            // Quick replacements compress the whole pose, preserving fall-before-colour-drain.
            val age = if (duration == DURATION_NANOS) elapsed else
                (elapsed.coerceAtMost(duration).toDouble() / duration * DURATION_NANOS).toLong()
            val shakeProgress = (age.toFloat() / SHAKE_NANOS).coerceIn(0f, 1f)
            val fallProgress = ((age - FALL_START_NANOS).toFloat() / (FALL_END_NANOS - FALL_START_NANOS))
                .coerceIn(0f, 1f)
            val fall = fallProgress * fallProgress * (3f - 2f * fallProgress)
            val fade = ((age - FALL_END_NANOS).toFloat() / (DURATION_NANOS - FALL_END_NANOS))
                .coerceIn(0f, 1f)
            return ReplayFaintPose(
                shakePixels = if (age < SHAKE_NANOS) {
                    sin(shakeProgress * PI * 6).toFloat() * 7f * (1f - shakeProgress)
                } else 0f,
                // Player faces right; opponent faces left. Each tips with its face upwards.
                rotationDegrees = (if (player) -90f else 90f) * fall,
                dropPixels = 10f * fall,
                // Keep full colour through the stagger and fall. Drain it only
                // once the sprite is lying down; these remain presentation poses.
                opacity = 1f - 0.4f * fade,
                saturation = 1f - fade,
            )
        }
    }
}
