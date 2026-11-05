package com.example.overdex.battle.replay

/** Cursor-based ending presentation keeps a result-only defeat distinct from a witnessed faint. */
data class ReplayCombatantEndPose(
    val opacity: Float,
    val shakePixels: Float = 0f,
    val rotationDegrees: Float = 0f,
    val dropPixels: Float = 0f,
    val saturation: Float = 1f,
    val withdrawalPixels: Float = 0f,
) {
    companion object {
        const val DEFEAT_DURATION_NANOS = 900_000_000L

        fun at(combatant: ReplayCombatant?, cursorNanos: Long, player: Boolean): ReplayCombatantEndPose? {
            combatant ?: return null
            if (combatant.isFainted) {
                val faint = ReplayFaintPose.at(
                    combatant.knockoutPresentationAtNanos ?: combatant.faintedAtNanos,
                    cursorNanos, player, combatant.knockoutFinishByNanos,
                ) ?: return null
                return ReplayCombatantEndPose(faint.opacity, faint.shakePixels,
                    faint.rotationDegrees, faint.dropPixels, faint.saturation)
            }
            val defeatedAt = combatant.defeatedAtNanos ?: return null
            // An already witnessed finishing projectile may still need to land.
            // Alignment changes the departure's presentation, never its cause.
            val elapsed = cursorNanos - (combatant.knockoutPresentationAtNanos ?: defeatedAt)
            if (elapsed < 0) return null
            val progress = (elapsed.toFloat() / DEFEAT_DURATION_NANOS).coerceIn(0f, 1f)
            val smooth = progress * progress * (3f - 2f * progress)
            return ReplayCombatantEndPose(opacity = 1f - smooth,
                withdrawalPixels = (if (player) -24f else 24f) * smooth)
        }
    }
}
