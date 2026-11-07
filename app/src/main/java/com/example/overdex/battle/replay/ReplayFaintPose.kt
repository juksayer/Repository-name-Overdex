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
            // A quick replacement needs its limited time for the fall and the
            // lying pose. Scaling the normal one-second hold proportionally
            // left only ~108 ms of rotation in a 550 ms ending interval.
            // Keep the ordinary sequence intact; condensed intervals reserve
            // most of their budget for rotation before draining the colour.
            val fallDuration = if (duration == DURATION_NANOS) FALL_END_NANOS - FALL_START_NANOS else
                minOf(FALL_END_NANOS - FALL_START_NANOS, duration * 7 / 10).coerceAtLeast(1L)
            val fadeDuration = if (duration == DURATION_NANOS) DURATION_NANOS - FALL_END_NANOS else
                minOf(DURATION_NANOS - FALL_END_NANOS, duration / 5)
            val fallStart = duration - fallDuration - fadeDuration
            val fallEnd = fallStart + fallDuration
            val shakeProgress = (elapsed.toFloat() / SHAKE_NANOS).coerceIn(0f, 1f)
            val fallProgress = ((elapsed - fallStart).toFloat() / fallDuration)
                .coerceIn(0f, 1f)
            val fall = fallProgress * fallProgress * (3f - 2f * fallProgress)
            val fade = if (fadeDuration == 0L) {
                if (elapsed >= duration) 1f else 0f
            } else ((elapsed - fallEnd).toFloat() / fadeDuration).coerceIn(0f, 1f)
            return ReplayFaintPose(
                shakePixels = if (elapsed < minOf(SHAKE_NANOS, fallStart)) {
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
