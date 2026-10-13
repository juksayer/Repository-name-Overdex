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
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.custody.FastMoveIdentified
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.abs

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
class FastMoveUseInference(private val onFirstImpact: (RealityArticle) -> Unit = {}) {
    private data class Appearance(val id: String, val speciesName: String?, val atNanos: Long)
    private data class UseEvidence(val article: RealityArticle, val kind: String, val defaultConfidence: Float)
    private data class PendingUse(
        val attackingSide: ActivePokemonSide,
        val damagedSide: ActivePokemonSide,
        val appearance: Appearance?,
        val evidence: MutableList<UseEvidence>
    ) {
        // Supporting motion can precede impact. Its timestamp must neither move
        // the use earlier nor expire the impact's corroboration window early.
        val anchorNanos: Long get() = (evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }
            .ifEmpty { evidence }).minOf { it.article.monotonicTimeNanos ?: Long.MAX_VALUE }
    }

    private val appearances = EnumMap<ActivePokemonSide, Appearance>(ActivePokemonSide::class.java)
    private val fainted = FaintedCombatants()
    private val pending = mutableListOf<PendingUse>()
    private val knownDurations = mutableMapOf<Pair<ActivePokemonSide, String>, Long>()
    private var watermarkNanos = Long.MIN_VALUE
    private var suppressUntilNanos = Long.MIN_VALUE
    private var matchStarted = false
    private var matchStartedAtNanos = Long.MAX_VALUE

    /** A known move changes the allowed phase offset, never creates a use. */
    fun observeIdentity(identity: FastMoveIdentified) {
        val key = identity.side to identity.speciesName.lowercase()
        val trusted = !identity.basis.contains("HIGHER_ENERGY_TIE_BREAK_ASSUMPTION") &&
            (identity.basis.startsWith("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES") ||
                identity.basis.startsWith("ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE") ||
                identity.basis.contains("EFFECTIVENESS_TEXT") || identity.basis.contains("_CORROBORATED_BY_"))
        if (trusted && identity.moveDurationNanos > 0) knownDurations[key] = identity.moveDurationNanos
        else knownDurations.remove(key)
    }

    fun accept(article: RealityArticle): List<FastMoveUseDerivation> {
        val atNanos = article.monotonicTimeNanos ?: return emptyList()
        fainted.observe(article)
        watermarkNanos = max(watermarkNanos, atNanos)
        if (article.payload is MatchStarted) {
            pending.clear()
            suppressUntilNanos = Long.MIN_VALUE
            matchStarted = true
            matchStartedAtNanos = atNanos
            return emptyList()
        }
        val output = mutableListOf<FastMoveUseDerivation>()
        when (val payload = article.payload) {
            AttackIncoming, GetReadyWitnessed -> {
                suppressUntilNanos = atNanos + if (payload == AttackIncoming)
                    ATTACK_INCOMING_MAX_PHASE_NANOS else GET_READY_MAX_PHASE_NANOS
                return flush()
            }
            ChargeMoveUsedAnnounced -> {
                suppressUntilNanos = atNanos + CHARGED_IMPACT_TAIL_NANOS
                pending.clear()
                return emptyList()
            }
            is MatchEnded -> {
                val final = if (matchStarted) flush() else emptyList()
                matchStarted = false
                return final
            }
            is ActivePokemonFaintedWitnessed -> {
                val current = appearances[payload.side]
                if (atNanos >= (current?.atNanos ?: Long.MIN_VALUE) &&
                    (payload.speciesName == null || current?.speciesName == null ||
                        payload.speciesName.equals(current.speciesName, ignoreCase = true))) {
                    output += flushSide(payload.side)
                    appearances.remove(payload.side)
                }
            }
            is ActivePokemonSpeciesWitnessed -> {
                if (fainted.contains(payload.side, payload.speciesName)) return flushExpired(watermarkNanos)
                val earlier = appearances[payload.side]
                if (atNanos >= (earlier?.atNanos ?: Long.MIN_VALUE) &&
                    !payload.speciesName.equals(earlier?.speciesName, ignoreCase = true)) {
                    output += flushSide(payload.side)
                    appearances[payload.side] = Appearance(article.id.value, payload.speciesName, atNanos)
                }
            }
            is SpeciesCheckMeasured -> {
                // A request to read a badge is not a switch. Announcement cues
                // request BOTH sides and may arrive after the accepted identity.
                // Only use a request to bound context that is still unidentified.
                if (payload.status == "OPENED" && payload.reason in setOf("ENTRY", "EMPTY_HP", "FAINT", "SWITCH") &&
                    appearances[payload.side]?.speciesName == null &&
                    payload.triggerMonotonicNanos > (appearances[payload.side]?.atNanos ?: Long.MIN_VALUE)) {
                    output += flushSide(payload.side)
                    appearances[payload.side] = Appearance(article.id.value, null, payload.triggerMonotonicNanos)
                }
            }
            is FastMoveIdentified -> observeIdentity(payload)
            else -> if (matchStarted && atNanos >= matchStartedAtNanos && atNanos > suppressUntilNanos) {
                evidenceFrom(article)?.let { (side, measured) ->
                    val appearance = appearances[side]
                    val matching = pending.filter { use ->
                        use.attackingSide == side && use.appearance == appearance &&
                            abs(atNanos - use.anchorNanos) <= mergeWindow(use) &&
                            // Two persistent HP steps or two pulse onsets are two
                            // hits even when frame jitter puts them <450 ms apart.
                            (!FastMoveUseEvidence.isImpact(measured.kind) || use.evidence.none {
                                it.kind == measured.kind && it.article.monotonicTimeNanos != atNanos
                            })
                    }.minByOrNull { abs(atNanos - it.anchorNanos) }
                    if (FastMoveUseEvidence.isImpact(measured.kind) &&
                        matching?.evidence?.any { FastMoveUseEvidence.isImpact(it.kind) } != true) {
                        onFirstImpact(article)
                    }
                    if (matching != null) {
                        if (matching.evidence.none { it.article.id == article.id }) matching.evidence += measured
                    } else {
                        pending += PendingUse(side, side.opposite(), appearance, mutableListOf(measured))
                    }
                }
            }
        }
        // Accept late corroboration before expiry. Custody delivery order is not
        // capture order; retained objects contain numbers/articles, no Bitmaps.
        output += flushExpired(watermarkNanos)
        while (pending.size > MAX_PENDING_USES) {
            val oldest = pending.minBy { it.anchorNanos }
            pending.remove(oldest)
            output += finalize(oldest)
        }
        return output
    }

    fun flush(): List<FastMoveUseDerivation> = pending.sortedBy { it.anchorNanos }
        .flatMap(::finalize).also { pending.clear() }

    private fun flushSide(side: ActivePokemonSide): List<FastMoveUseDerivation> {
        val ready = pending.filter { it.attackingSide == side }.sortedBy { it.anchorNanos }
        pending.removeAll(ready.toSet())
        return ready.flatMap(::finalize)
    }

    private fun mergeWindow(use: PendingUse): Long {
        val duration = use.appearance?.speciesName?.let { knownDurations[use.attackingSide to it.lowercase()] }
        // Incinerate's flash and settled HP loss can be farther apart than a
        // whole Water Gun cycle. Never widen a window from a cadence hypothesis.
        return duration?.let { (it * 45 / 100).coerceIn(USE_CLUSTER_WINDOW_NANOS, 1_000_000_000L) } ?: USE_CLUSTER_WINDOW_NANOS
    }

    private fun flushExpired(atNanos: Long): List<FastMoveUseDerivation> {
        val ready = pending.filter { atNanos - it.anchorNanos > mergeWindow(it) + DELIVERY_GRACE_NANOS }
            .sortedBy { it.anchorNanos }
        pending.removeAll(ready.toSet())
        return ready.flatMap(::finalize)
    }

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

    private fun finalize(use: PendingUse): List<FastMoveUseDerivation> {
        val ordered = use.evidence.sortedWith(compareBy(
            { it.article.monotonicTimeNanos ?: Long.MAX_VALUE },
            { it.article.id.value }
        ))
        val anchor = ordered.firstOrNull { FastMoveUseEvidence.isImpact(it.kind) } ?: return emptyList()
        val evidenceKinds = ordered.map { it.kind }.distinct().sortedBy { EVIDENCE_ORDER.indexOf(it) }
        val confidence = (1.0 - ordered.fold(1.0) { remaining, item ->
            remaining * (1.0 - (item.article.confidence ?: item.defaultConfidence).coerceIn(0f, 0.99f))
        }).toFloat().coerceIn(0f, 0.99f)
        return listOf(FastMoveUseDerivation(
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
        ))
    }

    private fun ActivePokemonSide.opposite(): ActivePokemonSide = when (this) {
        ActivePokemonSide.PLAYER -> ActivePokemonSide.OPPONENT
        ActivePokemonSide.OPPONENT -> ActivePokemonSide.PLAYER
    }

    private companion object {
        const val USE_CLUSTER_WINDOW_NANOS = 450_000_000L
        // New recordings show up to 1.12 s of publication delay. This bounded
        // wait preserves capture timestamps and prevents late evidence recounts.
        const val DELIVERY_GRACE_NANOS = 1_200_000_000L
        const val MAX_PENDING_USES = 24
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
