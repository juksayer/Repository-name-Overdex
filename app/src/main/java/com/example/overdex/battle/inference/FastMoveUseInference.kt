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
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
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
        var appearance: Appearance?,
        val evidence: MutableList<UseEvidence>
    ) {
        // Supporting motion can precede impact. Its timestamp must neither move
        // the use earlier nor expire the impact's corroboration window early.
        val anchorNanos: Long get() = (evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }
            .ifEmpty { evidence }).minOf { it.article.monotonicTimeNanos ?: Long.MAX_VALUE }
        val firstImpactReceivedAt: Long get() = (evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }
            .ifEmpty { evidence }).minOf { it.article.recordedAt }
    }

    private val appearances = EnumMap<ActivePokemonSide, Appearance>(ActivePokemonSide::class.java)
    private val appearanceHistory = EnumMap<ActivePokemonSide, MutableList<Appearance>>(ActivePokemonSide::class.java)
    private var fainted = FaintedCombatants()
    private val pending = mutableListOf<PendingUse>()
    private val knownDurations = mutableMapOf<Pair<ActivePokemonSide, String>, Long>()
    private var watermarkNanos = Long.MIN_VALUE
    private var receiptWatermarkMillis = Long.MIN_VALUE
    private data class HeldEvidence(
        val side: ActivePokemonSide, var appearance: Appearance?, val evidence: UseEvidence
    )
    private data class ChargedPause(
        val start: Long, var end: Long, var confirmedEnd: Boolean = false,
        val held: MutableList<HeldEvidence> = mutableListOf()
    )
    private val chargedPauses = mutableListOf<ChargedPause>()
    // Accounting can begin from real combat even if every countdown glyph was
    // missed. This does not create MatchStarted or claim to know when GO occurred.
    private var combatConfirmed = false
    private var accountingFromNanos = Long.MAX_VALUE
    private var matchEnded = false
    private var attemptFromNanos = Long.MIN_VALUE
    private var screenIgnored = false
    private data class DecisionPause(val start: Long, var end: Long)
    private val decisionPauses = mutableListOf<DecisionPause>()
    private var decisionObservedAtNanos = Long.MIN_VALUE

    /** A known move changes the allowed phase offset, never creates a use. */
    fun observeIdentity(identity: FastMoveIdentified) {
        val key = identity.side to identity.speciesName.lowercase()
        // Equal-duration names can remain tied while the duration itself is
        // measured. Use that timing to join evidence, not to award assumed energy.
        val measuredImpactDuration = identity.basis.contains("_CONSISTENT_IMPACT_STREAM") &&
            identity.cadenceSampleCount >= 2
        val trusted = measuredImpactDuration || (!identity.basis.contains("HIGHER_ENERGY_TIE_BREAK_ASSUMPTION") &&
            (identity.basis.startsWith("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES") ||
                identity.basis.startsWith("ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE") ||
                identity.basis.startsWith("AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE") ||
                identity.basis.contains("EFFECTIVENESS_TEXT") || identity.basis.contains("_CORROBORATED_BY_") ||
                (identity.basis.startsWith("BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE") &&
                    identity.cadenceSampleCount >= 2)))
        if (trusted && identity.moveDurationNanos > 0) knownDurations[key] = identity.moveDurationNanos
        else knownDurations.remove(key)
    }

    fun accept(article: RealityArticle): List<FastMoveUseDerivation> {
        val atNanos = article.monotonicTimeNanos ?: return emptyList()
        if (article.payload == ObservationRestartedByUser || article.payload == ScreenIgnoredByUser) {
            if (atNanos < attemptFromNanos || matchEnded) return emptyList()
            pending.clear()
            appearances.clear()
            appearanceHistory.clear()
            knownDurations.clear()
            fainted = FaintedCombatants()
            decisionPauses.clear()
            decisionObservedAtNanos = Long.MIN_VALUE
            chargedPauses.clear()
            watermarkNanos = atNanos
            receiptWatermarkMillis = article.recordedAt
            combatConfirmed = false
            accountingFromNanos = Long.MAX_VALUE
            attemptFromNanos = atNanos
            screenIgnored = article.payload == ScreenIgnoredByUser
            return emptyList()
        }
        if (screenIgnored || atNanos < attemptFromNanos) return emptyList()
        fainted.observe(article)
        watermarkNanos = max(watermarkNanos, atNanos)
        receiptWatermarkMillis = max(receiptWatermarkMillis, article.recordedAt)
        if (article.payload is MatchStarted) {
            // A delayed GO must not discard hits or charged-phase pauses that
            // were already established by independently witnessed combat.
            if (!combatConfirmed) {
                pending.clear()
                chargedPauses.clear()
                decisionPauses.clear()
                decisionObservedAtNanos = Long.MIN_VALUE
            }
            if (!matchEnded) {
                combatConfirmed = true
                accountingFromNanos = atNanos
            }
            return emptyList()
        }
        if (matchEnded) return emptyList()
        if (!combatConfirmed && confirmsCombat(article)) {
            combatConfirmed = true
            accountingFromNanos = atNanos
        }
        val output = mutableListOf<FastMoveUseDerivation>()
        when (val payload = article.payload) {
            is com.example.overdex.battle.custody.BattleDecisionTimerObserved -> {
                if (atNanos >= decisionObservedAtNanos) {
                    decisionObservedAtNanos = atNanos
                    val seconds = payload.secondsRemaining
                    val active = decisionPauses.lastOrNull()?.takeIf { atNanos < it.end }
                    if (seconds == null) active?.end = atNanos
                    else if (seconds in 0..15) {
                        val end = atNanos + (seconds + 1L) * 1_000_000_000L
                        if (active != null) active.end = end
                        else decisionPauses += DecisionPause(atNanos, end)
                    }
                    pending.removeAll { isDecisionPaused(it.anchorNanos) }
                }
                return flushExpired(watermarkNanos)
            }
            AttackIncoming, GetReadyWitnessed -> {
                val active = chargedPauses.lastOrNull { atNanos in it.start..it.end }
                if (active == null) chargedPauses += ChargedPause(atNanos, atNanos + CHARGED_MAX_PHASE_NANOS)
                // A late phase cue can arrive while impact evidence is still
                // being fused. Never flush those in-phase hits as Fast Moves.
                pending.removeAll { use -> chargedPauses.any { use.anchorNanos in it.start..it.end } }
                return flush()
            }
            ChargeMoveUsedAnnounced -> {
                decisionPauses.lastOrNull()?.let { if (atNanos in it.start until it.end) it.end = atNanos }
                val phase = chargedPauses.lastOrNull { atNanos in it.start..it.end }
                    ?: ChargedPause(atNanos, atNanos + CHARGED_IMPACT_TAIL_NANOS).also { chargedPauses += it }
                phase.end = atNanos + CHARGED_IMPACT_TAIL_NANOS
                phase.confirmedEnd = true
                pending.removeAll { it.anchorNanos in phase.start..phase.end }
                // The announcement can take seconds to reach custody. Preserve
                // real impacts already captured AFTER its end, with their own
                // appearance and timestamps, rather than clearing all pending hits.
                val resumed = phase.held.filter { (it.evidence.article.monotonicTimeNanos ?: 0L) > phase.end }
                phase.held.clear()
                resumed.forEach { held ->
                    val time = held.evidence.article.monotonicTimeNanos ?: return@forEach
                    if (!isDecisionPaused(time)) enqueueEvidence(held.side, held.appearance, held.evidence)
                }
                return flushExpired(watermarkNanos)
            }
            is MatchEnded -> {
                val final = if (combatConfirmed) flush() else emptyList()
                chargedPauses.forEach { it.held.clear() }
                combatConfirmed = false
                matchEnded = true
                return final
            }
            is ActivePokemonFaintedWitnessed -> {
                val current = appearances[payload.side]
                if (atNanos >= (current?.atNanos ?: Long.MIN_VALUE) &&
                    (payload.speciesName == null || current?.speciesName == null ||
                        payload.speciesName.equals(current.speciesName, ignoreCase = true))) {
                    output += flushSide(payload.side)
                    appearances.remove(payload.side)
                    recordAppearanceBoundary(payload.side, Appearance(article.id.value, null, atNanos))
                }
            }
            is ActivePokemonSpeciesWitnessed -> {
                if (fainted.contains(payload.side, payload.speciesName)) return flushExpired(watermarkNanos)
                val earlier = appearances[payload.side]
                if (atNanos >= (earlier?.atNanos ?: Long.MIN_VALUE) &&
                    !payload.speciesName.equals(earlier?.speciesName, ignoreCase = true)) {
                    val appearance = Appearance(article.id.value, payload.speciesName, atNanos)
                    appearances[payload.side] = appearance
                    recordAppearanceBoundary(payload.side, appearance)
                    // OCR can arrive after the replacement has already attacked.
                    // Close only the uses captured before the observed switch;
                    // later pending hits belong to the replacement's interval.
                    output += flushSide(payload.side, beforeNanos = atNanos)
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
                    val appearance = Appearance(article.id.value, null, payload.triggerMonotonicNanos)
                    appearances[payload.side] = appearance
                    recordAppearanceBoundary(payload.side, appearance)
                }
            }
            is FastMoveIdentified -> observeIdentity(payload)
            else -> if (combatConfirmed && atNanos >= accountingFromNanos && !isDecisionPaused(atNanos)) {
                evidenceFrom(article)?.let { (side, measured) ->
                    val phase = chargedPauses.lastOrNull { atNanos in it.start..it.end }
                    when {
                        phase == null -> enqueueEvidence(side, appearanceAt(side, atNanos), measured)
                        !phase.confirmedEnd && watermarkNanos <= phase.end + DELIVERY_GRACE_NANOS -> {
                            if (phase.held.none { it.evidence.article.id == article.id })
                                phase.held += HeldEvidence(side, appearanceAt(side, atNanos), measured)
                            if (phase.held.size > MAX_HELD_CHARGE_EVIDENCE) phase.held.removeAt(0)
                        }
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

    private fun enqueueEvidence(side: ActivePokemonSide, appearance: Appearance?, measured: UseEvidence) {
        val article = measured.article
        val atNanos = article.monotonicTimeNanos ?: return
        val matching = pending.filter { use ->
            use.attackingSide == side && use.appearance == appearance &&
                abs(atNanos - use.anchorNanos) <= mergeWindow(use) &&
                // Two persistent HP steps or two pulse onsets are two hits.
                (!FastMoveUseEvidence.isImpact(measured.kind) || use.evidence.none {
                    it.kind == measured.kind && it.article.monotonicTimeNanos != atNanos
                })
        }.minByOrNull { abs(atNanos - it.anchorNanos) }
        if (FastMoveUseEvidence.isImpact(measured.kind) &&
            matching?.evidence?.any { FastMoveUseEvidence.isImpact(it.kind) } != true) onFirstImpact(article)
        if (matching != null) {
            if (matching.evidence.none { it.article.id == article.id }) matching.evidence += measured
        } else {
            pending += PendingUse(side, side.opposite(), appearance, mutableListOf(measured))
        }
    }

    private fun isDecisionPaused(atNanos: Long) = decisionPauses.any { atNanos in it.start until it.end }

    private fun appearanceAt(side: ActivePokemonSide, atNanos: Long): Appearance? =
        appearanceHistory[side]?.lastOrNull { it.atNanos <= atNanos }

    private fun recordAppearanceBoundary(side: ActivePokemonSide, appearance: Appearance) {
        appearanceHistory.getOrPut(side) { mutableListOf() } += appearance
        // Only unpublished evidence is reassigned. Already emitted Articles stay
        // immutable; replay can reconcile their identity from the full history.
        pending.filter { it.attackingSide == side && it.anchorNanos >= appearance.atNanos }
            .forEach { it.appearance = appearanceAt(side, it.anchorNanos) }
        chargedPauses.forEach { phase ->
            phase.held.filter { it.side == side }.forEach heldLoop@{ held ->
                val at = held.evidence.article.monotonicTimeNanos ?: return@heldLoop
                if (at >= appearance.atNanos) held.appearance = appearanceAt(side, at)
            }
        }
    }

    private fun confirmsCombat(article: RealityArticle): Boolean = when (val payload = article.payload) {
        // These are measured, persistent losses from the HP witness, not the
        // charge-button shading that also animates before GO. Require a valid
        // decrease; names, motion and isolated flashes cannot open accounting.
        is ActiveHpBarDamageTickMeasured -> payload.beforeFraction in 0f..1f &&
            payload.afterFraction in 0f..1f && payload.lostFraction > 0f &&
            payload.beforeFraction > payload.afterFraction &&
            abs(payload.beforeFraction - payload.afterFraction - payload.lostFraction) < .001f
        AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced -> true
        else -> false
    }

    fun flush(): List<FastMoveUseDerivation> = pending.sortedBy { it.anchorNanos }
        .flatMap(::finalize).also { pending.clear() }

    private fun flushSide(side: ActivePokemonSide, beforeNanos: Long = Long.MAX_VALUE): List<FastMoveUseDerivation> {
        val ready = pending.filter { it.attackingSide == side && it.anchorNanos < beforeNanos }
            .sortedBy { it.anchorNanos }
        pending.removeAll(ready.toSet())
        return ready.flatMap(::finalize)
    }

    private fun mergeWindow(use: PendingUse): Long {
        val duration = use.appearance?.speciesName?.let { knownDurations[use.attackingSide to it.lowercase()] }
        // Recorded Water Shuriken losses precede their border flashes by up to
        // 1.002 s in the October 20 paired match's monotonic source times.
        // Allow this measured phase gap plus a small capture tolerance, still
        // below a full cycle and never from an energy-preference hypothesis.
        // Distinct losses and distinct pulse onsets remain distinct uses.
        return duration?.let { (it * 70 / 100).coerceIn(USE_CLUSTER_WINDOW_NANOS, 1_050_000_000L) } ?: USE_CLUSTER_WINDOW_NANOS
    }

    private fun flushExpired(atNanos: Long): List<FastMoveUseDerivation> {
        chargedPauses.filter { atNanos > it.end + DELIVERY_GRACE_NANOS }.forEach { it.held.clear() }
        val ready = pending.filter {
            atNanos - it.anchorNanos > mergeWindow(it) + DELIVERY_GRACE_NANOS &&
                // A slow HP witness may publish an older capture after another
                // lane has advanced the source watermark. Give that newly
                // received impact the same bounded corroboration opportunity;
                // otherwise it expires immediately and its flash is counted again.
                receiptWatermarkMillis - it.firstImpactReceivedAt >= DELIVERY_GRACE_NANOS / 1_000_000L
        }
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
        const val CHARGED_MAX_PHASE_NANOS = 12_000_000_000L
        const val MAX_HELD_CHARGE_EVIDENCE = 128
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
