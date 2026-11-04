package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle

/**
 * The shield-button fallback needs proof that battle is underway, not necessarily
 * a witnessed GO. Reuse the established source-time combat proof without its
 * prebattle audio-attention cues. Admission does not establish Match Start or
 * ordinary Fast Move play, and the Timeline performs no interpretation here.
 */
internal class BattleShieldDecisionTextEvidence {
    private val progress = BattleInProgressAdmission()
    private val text = ShieldDecisionTextEvidence()

    fun accept(article: RealityArticle): ShieldDecisionTextEvidence.Signal? {
        val payload = article.payload
        progress.observe(article)
        when (payload) {
            MatchStarted, is MatchEnded, ObservationRestartedByUser,
            ObservationSessionStoppedByUser, ScreenIgnoredByUser, ChargeMoveUsedAnnounced -> text.reset()
            else -> Unit
        }
        val label = payload as? PlayerChargeMoveLabelRead ?: return null
        val at = article.monotonicTimeNanos ?: return null
        if (!progress.allows(at)) return null
        return text.accept(label.slot, label.text, label.basis, at, article.perceivedAt, article.id.value)
            ?.let { signal -> signal.copy(ids = (progress.triggerIds() + signal.ids).distinct()) }
    }
}
