package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.EnumMap
import kotlin.math.max

data class FastMoveUseDerivation(
    val payload: FastMoveUseObserved,
    val predecessorIds: List<ArticleId>,
    val confidence: Float,
    val observedArticle: RealityArticle
)

/**
 * Coalesces independent, already-recorded hit measurements into one observed
 * Fast Move use. Move name, type, damage, and energy remain downstream claims.
 */
class FastMoveUseInference {
    private data class Appearance(val id: String, val speciesName: String?)
    private data class UseEvidence(val article: RealityArticle, val kind: String, val defaultConfidence: Float)
    private data class PendingUse(
        val attackingSide: ActivePokemonSide,
        val damagedSide: ActivePokemonSide,
        val appearance: Appearance?,
        val evidence: MutableList<UseEvidence>
    ) {
        val anchor: UseEvidence get() = evidence.minBy { it.article.monotonicTimeNanos ?: Long.MAX_VALUE }
        val anchorNanos: Long get() = anchor.article.monotonicTimeNanos ?: Long.MAX_VALUE
    }

    private val appearances = EnumMap<ActivePokemonSide, Appearance>(ActivePokemonSide::class.java)
    private val pending = EnumMap<ActivePokemonSide, PendingUse>(ActivePokemonSide::class.java)
    private var watermarkNanos = Long.MIN_VALUE
    private var suppressUntilNanos = Long.MIN_VALUE

    fun accept(article: RealityArticle): List<FastMoveUseDerivation> {
        val atNanos = article.monotonicTimeNanos ?: return emptyList()
        watermarkNanos = max(watermarkNanos, atNanos)
        val output = flushExpired(watermarkNanos).toMutableList()

        when (val payload = article.payload) {
            AttackIncoming -> {
                suppressUntilNanos = atNanos + ATTACK_INCOMING_MAX_PHASE_NANOS
                pending.values.sortedBy { it.anchorNanos }.forEach { output += finalize(it) }
                pending.clear()
                return output
            }
            GetReadyWitnessed -> {
                suppressUntilNanos = atNanos + GET_READY_MAX_PHASE_NANOS
                pending.values.sortedBy { it.anchorNanos }.forEach { output += finalize(it) }
                pending.clear()
                return output
            }
            ChargeMoveUsedAnnounced -> {
                // This later cue replaces the broad decision/QTE guard. The short
                // tail covers the charged impact without hiding resumed combat.
                suppressUntilNanos = atNanos + CHARGED_IMPACT_TAIL_NANOS
                pending.clear()
                return output
            }
            is MatchEnded -> {
                pending.values.sortedBy { it.anchorNanos }.forEach { output += finalize(it) }
                pending.clear()
                return output
            }
            is ActivePokemonFaintedWitnessed -> {
                pending.remove(payload.side)?.let { output += finalize(it) }
                appearances.remove(payload.side)
                return output
            }
            is ActivePokemonSpeciesWitnessed -> {
                val earlier = appearances[payload.side]
                if (earlier?.speciesName != payload.speciesName) {
                    pending.remove(payload.side)?.let { output += finalize(it) }
                    appearances[payload.side] = Appearance(article.id.value, payload.speciesName)
                }
                return output
            }
            is SpeciesCheckMeasured -> if (
                payload.status == "OPENED" && payload.reason in setOf("ENTRY", "EMPTY_HP", "FAINT", "SWITCH")
            ) {
                pending.remove(payload.side)?.let { output += finalize(it) }
                appearances[payload.side] = Appearance(article.id.value, null)
                return output
            }
        }

        if (atNanos <= suppressUntilNanos) return output
        val evidence = evidenceFrom(article) ?: return output
        val attackingSide = evidence.first
        val measured = evidence.second
        val existing = pending[attackingSide]
        if (existing == null) {
            pending[attackingSide] = newPending(attackingSide, measured)
            return output
        }

        val distance = atNanos - existing.anchorNanos
        when {
            distance in 0L..USE_CLUSTER_WINDOW_NANOS -> {
                if (existing.evidence.none { it.article.id == article.id }) existing.evidence += measured
            }
            distance > USE_CLUSTER_WINDOW_NANOS -> {
                output += finalize(existing)
                pending[attackingSide] = newPending(attackingSide, measured)
            }
            -distance <= USE_CLUSTER_WINDOW_NANOS -> {
                if (existing.evidence.none { it.article.id == article.id }) existing.evidence += measured
            }
            // A substantially late article remains on the Timeline but cannot
            // safely be folded into a newer battlefield moment.
        }
        return output
    }

    fun flush(): List<FastMoveUseDerivation> = pending.values
        .sortedBy { it.anchorNanos }
        .map(::finalize)
        .also { pending.clear() }

    private fun flushExpired(atNanos: Long): List<FastMoveUseDerivation> {
        val ready = pending.values
            .filter { atNanos - it.anchorNanos > USE_CLUSTER_WINDOW_NANOS }
            .sortedBy { it.anchorNanos }
        ready.forEach { pending.remove(it.attackingSide) }
        return ready.map(::finalize)
    }

    private fun newPending(attackingSide: ActivePokemonSide, evidence: UseEvidence) = PendingUse(
        attackingSide = attackingSide,
        damagedSide = attackingSide.opposite(),
        appearance = appearances[attackingSide],
        evidence = mutableListOf(evidence)
    )

    private fun evidenceFrom(article: RealityArticle): Pair<ActivePokemonSide, UseEvidence>? = when (val payload = article.payload) {
        is HpBarBorderPulse -> payload.takeIf { it.status == ApertureStatus.PRESENT }
            ?.let { it.barSide.opposite() to UseEvidence(article, "HP_BORDER_PULSE", 0.88f) }
        is ActiveHpBarBorderPulseObserved -> payload.damagedBarSide.opposite() to UseEvidence(
            article, "HP_BORDER_PULSE", 0.88f
        )
        is ActiveHpBarDamageTickMeasured -> payload.damagedSide.opposite() to UseEvidence(
            article, "HP_DAMAGE_TICK", 0.92f
        )
        is FastMoveRecipientVisualArtifactMeasured -> payload.damagedSide.opposite() to UseEvidence(
            article, "RECIPIENT_VISUAL_ARTIFACT", 0.72f
        )
        is ActiveHpBarBorderCadenceMeasured -> payload.damagedBarSide.opposite() to UseEvidence(
            article, "HP_BORDER_CADENCE", 0.78f
        )
        is ActiveHpBarMotionCadenceMeasured -> payload.movingSide to UseEvidence(
            article, "HP_BAR_MOTION_COMPLETION", 0.74f
        )
        is FastMoveRecipientVisualCadenceMeasured -> payload.damagedSide.opposite() to UseEvidence(
            article, "RECIPIENT_VISUAL_CADENCE", 0.72f
        )
        is PlayerChargeMoveEnergyFillIncreased -> ActivePokemonSide.PLAYER to UseEvidence(
            article, "PLAYER_CHARGE_FILL_INCREASE", 0.94f
        )
        else -> null
    }

    private fun finalize(use: PendingUse): FastMoveUseDerivation {
        val ordered = use.evidence.sortedWith(compareBy(
            { it.article.monotonicTimeNanos ?: Long.MAX_VALUE },
            { it.article.id.value }
        ))
        val anchor = ordered.first()
        val evidenceKinds = ordered.map { it.kind }.distinct().sortedBy { EVIDENCE_ORDER.indexOf(it) }
        val confidence = (1.0 - ordered.fold(1.0) { remaining, item ->
            remaining * (1.0 - (item.article.confidence ?: item.defaultConfidence).coerceIn(0f, 0.99f))
        }).toFloat().coerceIn(0f, 0.99f)
        return FastMoveUseDerivation(
            payload = FastMoveUseObserved(
                useId = "fast-move-use:${anchor.article.id.value}",
                attackingSide = use.attackingSide,
                damagedSide = use.damagedSide,
                appearanceId = use.appearance?.id,
                attackerSpeciesName = use.appearance?.speciesName,
                evidenceKinds = evidenceKinds
            ),
            predecessorIds = ordered.map { it.article.id },
            confidence = confidence,
            observedArticle = anchor.article
        )
    }

    private fun ActivePokemonSide.opposite(): ActivePokemonSide = when (this) {
        ActivePokemonSide.PLAYER -> ActivePokemonSide.OPPONENT
        ActivePokemonSide.OPPONENT -> ActivePokemonSide.PLAYER
    }

    private companion object {
        // Fastest moves are 500 ms. This leaves 50 ms between adjacent one-turn
        // moves while allowing slower witnesses from one impact to corroborate it.
        const val USE_CLUSTER_WINDOW_NANOS = 450_000_000L
        const val ATTACK_INCOMING_MAX_PHASE_NANOS = 12_000_000_000L
        const val GET_READY_MAX_PHASE_NANOS = 8_000_000_000L
        const val CHARGED_IMPACT_TAIL_NANOS = 750_000_000L
        val EVIDENCE_ORDER = listOf(
            "HP_BORDER_PULSE",
            "HP_DAMAGE_TICK",
            "RECIPIENT_VISUAL_ARTIFACT",
            "HP_BORDER_CADENCE",
            "HP_BAR_MOTION_COMPLETION",
            "RECIPIENT_VISUAL_CADENCE",
            "PLAYER_CHARGE_FILL_INCREASE"
        )
    }
}
