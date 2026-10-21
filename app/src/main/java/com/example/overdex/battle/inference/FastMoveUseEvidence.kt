package com.example.overdex.battle.inference

/**
 * Movement, an unclassified visual effect, and a charge-button boundary can
 * change while the player is idle. They corroborate a hit; they do not prove
 * that one occurred. Keep this rule identical in live inference and replay.
 */
object FastMoveUseEvidence {
    fun isImpact(kind: String): Boolean = kind == "HP_BORDER_PULSE" || kind == "HP_DAMAGE_TICK"

    fun establishesUse(kinds: Collection<String>): Boolean = kinds.any(::isImpact)
}
