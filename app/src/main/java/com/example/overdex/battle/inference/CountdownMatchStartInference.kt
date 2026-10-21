package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleWitnessContracts
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.UUID

/**
 * Fallback when GO was missed. A countdown number predicts its time, but only
 * later combat confirms that the countdown actually led into a battle. Names,
 * full HP bars and charged-button countdown shading cannot confirm this boundary.
 * The derived article cites both witnesses and never invents a GO observation.
 */
class CountdownMatchStartInference {
    private var countdown: RealityArticle? = null
    private var combat: RealityArticle? = null

    @Synchronized fun reset() { countdown = null; combat = null }

    @Synchronized fun accept(article: RealityArticle): RealityArticle? {
        val at = article.monotonicTimeNanos ?: return null
        when (val payload = article.payload) {
            ObservationRestartedByUser, ScreenIgnoredByUser, is MatchEnded -> { reset(); return null }
            is CountdownGlyphWitnessed -> {
                if (article.sourceId.id != BattleWitnessContracts.countdownGlyph.witnessId ||
                    payload.glyph !in setOf("3", "2", "1")
                ) return null
                if (at >= (countdown?.monotonicTimeNanos ?: Long.MIN_VALUE)) countdown = article
            }
            is ActiveHpBarDamageTickMeasured, AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced -> {
                combat = article
            }
            is HpBarBorderPulse -> if (payload.status == ApertureStatus.PRESENT) combat = article else return null
            else -> return null
        }
        val number = countdown ?: return null
        val confirmation = combat ?: return null
        if (number.matchId != confirmation.matchId) return null
        val remainingSeconds = (number.payload as CountdownGlyphWitnessed).glyph.toLong()
        val predictedNanos = number.monotonicTimeNanos!! + remainingSeconds * 1_000_000_000L
        val confirmationNanos = confirmation.monotonicTimeNanos!!
        if (confirmationNanos - predictedNanos !in 0L..30_000_000_000L) return null
        return RealityArticle(
            id = ArticleId(UUID.randomUUID().toString()),
            perceivedAt = number.perceivedAt + remainingSeconds * 1_000L,
            recordedAt = System.currentTimeMillis(),
            sourceId = SourceId("COUNTDOWN_AND_COMBAT_START_INFERENCE"),
            payload = MatchStarted,
            predecessorIds = listOf(number.id, confirmation.id),
            confidence = 0.75f,
            matchId = number.matchId,
            monotonicTimeNanos = predictedNanos
        ).also { reset() }
    }
}
