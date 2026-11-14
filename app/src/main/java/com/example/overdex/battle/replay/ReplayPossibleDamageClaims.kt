package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** An inclusive conflict inventory, not a second attack counter. Unknown or rejected scheduling
 * claims remain available to causal attribution; legal moves alone are not uses. */
data class ReplayPossibleDamageClaim(val articleId: String, val atNanos: Long, val kind: String,
                                     val attackingSide: String?, val damagedSide: String?, val moveName: String?)
object ReplayPossibleDamageClaims {
    fun collect(articles: List<ArchivedRealityArticle>): List<ReplayPossibleDamageClaim> = articles.mapNotNull { a ->
        val at = a.monotonicTimeNanos ?: return@mapNotNull null
        fun claim(kind: String, actor: String? = null, target: String? = null, move: String? = null) =
            ReplayPossibleDamageClaim(a.articleId, at, kind, actor, target, move)
        when (val p = a.payload) {
            is ArchivedFastMoveUseObserved -> claim("FAST_USE", p.attackingSide, p.damagedSide)
            is ArchivedChargedMoveEnergySpent -> claim("CHARGE_USE", p.side, move = p.moveName)
            is ArchivedChargeMoveUsedAnnounced -> claim("CHARGE_ANNOUNCEMENT")
            is ArchivedActiveHpBarDamageTickMeasured -> claim("DAMAGE_TICK", target = p.damagedSide)
            is ArchivedActiveHpBarBorderPulseObserved -> claim("BORDER_PULSE", target = p.damagedBarSide)
            is ArchivedHpBarBorderPulse -> if (p.status == "PRESENT") claim("BORDER_PULSE", target = p.barSide) else null
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> claim("RECIPIENT_EFFECT", target = p.damagedSide)
            is ArchivedFastMoveSoundMeasured -> if (p.audible || p.referenceMatchAccepted) claim("FAST_SOUND", p.attackingSide ?: p.cueAttackingSide) else null
            else -> null
        }
    }.sortedWith(compareBy<ReplayPossibleDamageClaim> { it.atNanos }.thenBy { it.articleId })
}
