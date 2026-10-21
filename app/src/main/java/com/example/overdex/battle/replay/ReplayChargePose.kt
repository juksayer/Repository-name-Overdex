package com.example.overdex.battle.replay

import kotlin.math.pow

/** Cursor-driven charge-up and slam, independent of Android animation settings. */
data class ReplayChargePose(
    val risePixels: Float,
    val iconScale: Float,
    val projectileProgress: Float,
    val iconAlpha: Float,
) {
    companion object {
        fun at(action: ReplayChargedMoveAction, player: Boolean): ReplayChargePose {
            val amplitude = if (player) 48f else 24f
            val progress = action.progress.coerceIn(0f, 1f)
            val gradeProgress = action.qteLevelProgress.coerceIn(0f, 1f)
            val gradeGrowth = if (action.qteLevel > 0) action.qteLevel.coerceAtMost(3) - 1f +
                gradeProgress * gradeProgress * (3f - 2f * gradeProgress) else 0f
            if (action.phase == ReplayChargePhase.CHARGING) return ReplayChargePose(
                -amplitude * 0.75f * progress * (2f - progress),
                0.72f + 0.58f * progress + 0.12f * gradeGrowth,
                0f,
                1f,
            )
            val height = if (progress <= 0.78f) {
                val rise = progress / 0.78f
                // Continue from the preparation apex without snapping down.
                0.75f + 0.25f * rise * rise * (3f - 2f * rise)
            } else (1f - (progress - 0.78f) / 0.22f).pow(3)
            val flight = ((progress - 0.78f) / 0.22f).coerceIn(0f, 1f)
            return ReplayChargePose(-amplitude * height, 1.3f + 0.12f * gradeGrowth,
                flight, 1f)
        }
    }
}
