package com.example.overdex.battle.replay

/** Cursor-driven charge-up and throw, independent of Android animation settings. */
data class ReplayChargePose(
    val risePixels: Float,
    val iconScale: Float,
    val projectileProgress: Float,
    val iconAlpha: Float,
    /** Once thrown, the icon follows its own path from the apex, not the falling sprite. */
    val projectileRisePixels: Float = risePixels,
) {
    companion object {
        private const val FOCUS_RAMP_NANOS = 750_000_000.0
        private const val APEX_PROGRESS = .65f
        /** Framing outlives the projectile, but never constitutes another attack. */
        fun qteFocusAmount(focus: ReplayChargeFocus?, cursorNanos: Long): Float {
            if (focus == null || cursorNanos < focus.startedAtNanos || cursorNanos >= focus.finishByNanos) return 0f
            val rise = (cursorNanos - focus.startedAtNanos).toDouble() / FOCUS_RAMP_NANOS
            val fall = 1.0 - (cursorNanos - focus.settledAtNanos).coerceAtLeast(0L).toDouble() / FOCUS_RAMP_NANOS
            val ramp = minOf(rise, fall).coerceIn(0.0, 1.0).toFloat()
            return ramp * ramp * (3f - 2f * ramp)
        }

        fun qteSpriteScale(focus: ReplayChargeFocus?, cursorNanos: Long, player: Boolean): Float =
            1f + qteFocusAmount(focus, cursorNanos) * if (player) 0.20f else -0.15f

        /** Fine positioning is measured in physical pixels, independent of device density. */
        fun qteSpriteTranslationXPixels(focus: ReplayChargeFocus?, cursorNanos: Long, player: Boolean): Float =
            qteFocusAmount(focus, cursorNanos) * if (player) 20f else -10f

        fun at(action: ReplayChargedMoveAction, player: Boolean): ReplayChargePose {
            val amplitude = if (player) 48f else 24f
            val progress = action.progress.coerceIn(0f, 1f)
            val gradeProgress = action.qteLevelProgress.coerceIn(0f, 1f)
            val gradeGrowth = if (action.qteLevel > 0) action.qteLevel.coerceAtMost(3) - 1f +
                gradeProgress * gradeProgress * (3f - 2f * gradeProgress) else 0f
            val shieldWindUp = !player && "SHIELD_PROMPT" in action.evidenceKinds
            if (action.phase == ReplayChargePhase.CHARGING) return ReplayChargePose(
                -amplitude * when {
                    shieldWindUp -> smooth(progress)
                    player -> 0.75f * progress * (2f - progress)
                    else -> 0.75f * smooth(progress)
                },
                0.72f + 0.58f * progress + 0.12f * gradeGrowth,
                0f,
                1f,
            )
            // The visible shield phase already brought the opponent to the apex.
            // Spend the existing strike interval on the throw and descent.
            if (shieldWindUp) return ReplayChargePose(
                -amplitude * (1f - smooth(progress)), 1.3f, progress, 1f, -amplitude)
            val flight = ((progress - APEX_PROGRESS) / (1f - APEX_PROGRESS)).coerceIn(0f, 1f)
            val height = if (progress <= APEX_PROGRESS) {
                val rise = progress / APEX_PROGRESS
                // Continue from the preparation apex without snapping down.
                0.75f + 0.25f * rise * rise * (3f - 2f * rise)
            } else 1f - flight * flight * (3f - 2f * flight)
            // Release at the apex as descent begins. Keep the existing landing
            // endpoint so the slower pose cannot drift away from HP or a faint.
            return ReplayChargePose(-amplitude * height, 1.3f + 0.12f * gradeGrowth,
                flight, 1f, if (progress >= APEX_PROGRESS) -amplitude else -amplitude * height)
        }

        private fun smooth(progress: Float) = progress * progress * (3f - 2f * progress)
    }
}

/** Display-only interval: settling is impact for completed uses, bounded expiry for open preparation. */
data class ReplayChargeFocus(
    val startedAtNanos: Long,
    val settledAtNanos: Long,
    val finishByNanos: Long = settledAtNanos + 750_000_000L,
)
