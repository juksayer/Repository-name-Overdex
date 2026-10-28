package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
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
    private val hpHistory = EnumMap<ActivePokemonSide, MutableList<RealityArticle>>(ActivePokemonSide::class.java)
    private val impactHistory = mutableListOf<UseEvidence>()
    private val chargedHpCamera = ChargedHpCameraGuard()
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
            hpHistory.clear()
            impactHistory.clear()
            chargedHpCamera.reset()
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
        chargedHpCamera.advanceTo(atNanos)
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
            is ActiveHpBarMeasured -> {
                chargedHpCamera.observeHp(article.id.value, atNanos, payload.side.name,
                    payload.barLeft, payload.barTop, payload.barRight, payload.barBottom,
                    payload.sourceWidth, payload.sourceHeight)
                val samples = hpHistory.getOrPut(payload.side) { mutableListOf() }
                if (samples.none { it.id == article.id }) samples += article
                samples.removeAll { (it.monotonicTimeNanos ?: 0L) < watermarkNanos - PHASE_HISTORY_NANOS }
                while (samples.size > MAX_HP_HISTORY) samples.removeAt(0)
            }
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
                chargedHpCamera.observeCharge(atNanos)
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
                val final = if (combatConfirmed) flush(includeAwaitingCharge = true) else emptyList()
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
                    recordAppearanceBoundary(payload.side, Appearance(article.id.value, null, atNanos), endsWithImpact = true)
                }
            }
            is ActivePokemonSpeciesWitnessed -> {
                if (fainted.contains(payload.side, payload.speciesName)) return flushExpired(watermarkNanos)
                val earlier = appearances[payload.side]
                if (atNanos >= (earlier?.atNanos ?: Long.MIN_VALUE) &&
                    (!payload.speciesName.equals(earlier?.speciesName, ignoreCase = true) ||
                        (payload.appearanceStartedAtNanos != null && payload.appearanceStartedAtNanos != earlier?.atNanos))) {
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
            val oldest = resolvePending(pending).first()
            pending.removeAll(oldest.members.toSet())
            output += finalize(oldest.use)
        }
        return output
    }

    private fun enqueueEvidence(side: ActivePokemonSide, appearance: Appearance?, measured: UseEvidence) {
        val article = measured.article
        val atNanos = article.monotonicTimeNanos ?: return
        if (FastMoveUseEvidence.isImpact(measured.kind) && impactHistory.none { it.article.id == article.id }) {
            impactHistory += measured
            impactHistory.removeAll { (it.article.monotonicTimeNanos ?: 0L) < watermarkNanos - PHASE_HISTORY_NANOS }
            while (impactHistory.size > MAX_IMPACT_HISTORY) impactHistory.removeAt(0)
        }
        // Keep complementary impact streams separate until publication, so a
        // late plateau sample or boundary can still change their association.
        val neighborhood = pending.filter { use ->
            use.attackingSide == side && use.appearance == appearance &&
                abs(atNanos - use.anchorNanos) <= mergeWindow(use) &&
                (!FastMoveUseEvidence.isImpact(measured.kind) || use.evidence.none {
                    it.kind == measured.kind && it.article.monotonicTimeNanos != atNanos
                })
        }.minByOrNull { abs(atNanos - it.anchorNanos) }
        val corroborates = neighborhood?.evidence?.any { FastMoveUseEvidence.isImpact(it.kind) } == true ||
            pending.any { it.attackingSide == side && it.appearance == appearance &&
                plateauSupport(it.evidence, listOf(measured)) != null }
        if (FastMoveUseEvidence.isImpact(measured.kind) && !corroborates) onFirstImpact(article)
        val matching = neighborhood?.takeUnless { use -> FastMoveUseEvidence.isImpact(measured.kind) &&
            use.evidence.any { FastMoveUseEvidence.isImpact(it.kind) && it.kind != measured.kind } }
        if (matching != null) {
            if (matching.evidence.none { it.article.id == article.id }) matching.evidence += measured
        } else {
            pending += PendingUse(side, side.opposite(), appearance, mutableListOf(measured))
        }
    }

    private data class ResolvedUse(val use: PendingUse, val members: List<PendingUse>)

    /** Recomputed from retained sources until emission; no irreversible early association. */
    private fun resolvePending(input: List<PendingUse>): List<ResolvedUse> {
        val remaining = input.sortedBy { it.anchorNanos }.toMutableList()
        val resolved = mutableListOf<ResolvedUse>()
        fun combine(a: PendingUse, b: PendingUse, support: List<UseEvidence>) {
            val evidence = (a.evidence + b.evidence + support).distinctBy { it.article.id }.toMutableList()
            resolved += ResolvedUse(PendingUse(a.attackingSide, a.damagedSide, a.appearance, evidence), listOf(a, b))
            remaining.remove(a)
            remaining.remove(b)
        }
        // The Halloween Cup recording contains a loss and onset from the exact
        // same frame, followed by another onset on the unchanged HP plateau.
        // Preserve the direct same-frame pair before trying delayed associations.
        for (first in remaining.toList()) {
            if (first !in remaining) continue
            val kind = first.evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }.singleOrNull()?.kind
                ?: continue
            val exact = remaining.firstOrNull { other ->
                val otherKind = other.evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }.singleOrNull()?.kind
                other !== first && sameContext(first, other) && first.anchorNanos == other.anchorNanos &&
                    otherKind != null && otherKind != kind
            } ?: continue
            combine(first, exact, emptyList())
        }
        // Positive plateau evidence takes precedence over symmetric proximity.
        for (loss in remaining.toList()) {
            if (loss !in remaining) continue
            val match = remaining.asSequence().filter { it !== loss && sameContext(loss, it) }
                .mapNotNull { pulse -> plateauSupport(loss.evidence, pulse.evidence)?.let { pulse to it } }
                .firstOrNull() ?: continue
            combine(loss, match.first, match.second)
        }
        // Preserve ordinary close-time fusion when no extended join was proved.
        for (first in remaining.toList()) {
            if (first !in remaining) continue
            val firstKind = first.evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }.singleOrNull()?.kind
            val second = if (firstKind == null) null else remaining.filter { other ->
                val kind = other.evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }.singleOrNull()?.kind
                other !== first && kind != null && kind != firstKind && sameContext(first, other) &&
                    !crossesBoundary(first.anchorNanos, other.anchorNanos) &&
                    abs(first.anchorNanos - other.anchorNanos) <= mergeWindow(first)
            }.minByOrNull { abs(first.anchorNanos - it.anchorNanos) }
            if (second != null) combine(first, second, emptyList())
            else {
                resolved += ResolvedUse(first, listOf(first))
                remaining.remove(first)
            }
        }
        return resolved.sortedBy { it.use.anchorNanos }
    }

    private fun sameContext(a: PendingUse, b: PendingUse) =
        a.attackingSide == b.attackingSide && a.appearance == b.appearance

    private fun crossesBoundary(a: Long, b: Long): Boolean {
        val from = minOf(a, b)
        val until = maxOf(a, b)
        return appearanceHistory.values.any { history -> history.any { it.atNanos > from && it.atNanos <= until } } ||
            chargedPauses.any { it.start <= until && it.end >= from } ||
            chargedHpCamera.windows().any { it.first <= until && it.last >= from } ||
            decisionPauses.any { it.start <= until && it.end >= from }
    }

    private fun plateauSupport(left: List<UseEvidence>, right: List<UseEvidence>): List<UseEvidence>? {
        if (left.count { FastMoveUseEvidence.isImpact(it.kind) } != 1 ||
            right.count { FastMoveUseEvidence.isImpact(it.kind) } != 1) return null
        val impacts = (left + right).filter { FastMoveUseEvidence.isImpact(it.kind) }
        val loss = impacts.singleOrNull { it.kind == "HP_DAMAGE_TICK" } ?: return null
        val pulse = impacts.singleOrNull { it.kind == "HP_BORDER_PULSE" } ?: return null
        if (impacts.size != 2) return null
        val damage = loss.article.payload as? ActiveHpBarDamageTickMeasured ?: return null
        val from = loss.article.monotonicTimeNanos ?: return null
        val until = pulse.article.monotonicTimeNanos ?: return null
        if (until <= from || until - from > MAX_DELAYED_PHASE_NANOS) return null
        if (crossesBoundary(from, until)) return null
        val attackingSide = damage.damagedSide.opposite()
        if (impactHistory.any { other -> other.article.id != loss.article.id && other.article.id != pulse.article.id &&
                evidenceFrom(other.article)?.first == attackingSide &&
                (other.article.monotonicTimeNanos ?: 0L).let { it >= from && it <= until } }) return null
        val samples = hpHistory[damage.damagedSide].orEmpty()
            .filter { (it.monotonicTimeNanos ?: 0L) in from..until }.sortedBy { it.monotonicTimeNanos }
        if (samples.map { it.monotonicTimeNanos }.distinct().size < 3 ||
            samples.first().monotonicTimeNanos!! - from > PLATEAU_ENDPOINT_NANOS ||
            until - samples.last().monotonicTimeNanos!! > PLATEAU_ENDPOINT_NANOS ||
            samples.zipWithNext().any { (a, b) -> b.monotonicTimeNanos!! - a.monotonicTimeNanos!! > MAX_PLATEAU_GAP_NANOS }) return null
        val first = samples.first().payload as ActiveHpBarMeasured
        if (first.sourceWidth == null) return null
        if (samples.any { sample ->
                val hp = sample.payload as ActiveHpBarMeasured
                val width = hp.barRight - hp.barLeft
                val inner = width - 2 * max(2, width / 80)
                inner <= 0 || width.toFloat() / (hp.barBottom - hp.barTop) !in 6f..30f ||
                    hp.sourceWidth != first.sourceWidth || hp.sourceHeight != first.sourceHeight ||
                    abs(hp.barLeft - first.barLeft) > 2 || abs(hp.barRight - first.barRight) > 2 ||
                    abs(hp.filledFraction - damage.afterFraction) * inner > 1.5f
            }) return null
        return samples.map { UseEvidence(it, "HP_PLATEAU_CONTINUITY", 0f) }
    }

    private fun isDecisionPaused(atNanos: Long) = decisionPauses.any { atNanos in it.start until it.end }

    private fun appearanceAt(side: ActivePokemonSide, atNanos: Long): Appearance? =
        appearanceHistory[side]?.lastOrNull { it.atNanos <= atNanos }

    private fun recordAppearanceBoundary(side: ActivePokemonSide, appearance: Appearance, endsWithImpact: Boolean = false) {
        // A replacement may naturally have a different HP height. Do not learn
        // that new combatant's normal bar as the old one's charged camera.
        chargedHpCamera.observeBoundary(appearance.atNanos, includeImpactAtBoundary = endsWithImpact)
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

    fun flush(includeAwaitingCharge: Boolean = false): List<FastMoveUseDerivation> {
        val ready = pending.filter { includeAwaitingCharge || !chargedHpCamera.isAwaitingCharge(it.anchorNanos) }
        pending.removeAll(ready.toSet())
        return resolvePending(ready).flatMap { finalize(it.use) }
    }

    private fun flushSide(side: ActivePokemonSide, beforeNanos: Long = Long.MAX_VALUE): List<FastMoveUseDerivation> {
        val ready = pending.filter { it.attackingSide == side && it.anchorNanos < beforeNanos &&
            !chargedHpCamera.isAwaitingCharge(it.anchorNanos) }
        val resolved = resolvePending(ready)
        pending.removeAll(ready.toSet())
        return resolved.flatMap { finalize(it.use) }
    }

    private fun mergeWindow(use: PendingUse): Long {
        val duration = use.appearance?.speciesName?.let { knownDurations[use.attackingSide to it.lowercase()] }
        // Recorded Water Shuriken losses precede their border flashes by up to
        // 1.002 s in the October 20 paired match's monotonic source times.
        // Keep that allowance for short moves. The October 23 Incinerate clip
        // shows one loss followed by its flash 1.093 s later: the fixed 1.05 s
        // ceiling split it into two energy awards. For longer known moves,
        // allow up to half a cycle (1.25 s for Incinerate) instead of clipping
        // every duration to the shorter move's measured phase gap.
        // This joins different witnesses, never two losses or two onsets.
        // Unknown/assumed durations still get only the default window.
        return duration?.let {
            (it * 70 / 100).coerceIn(USE_CLUSTER_WINDOW_NANOS, max(1_050_000_000L, it / 2))
        } ?: USE_CLUSTER_WINDOW_NANOS
    }

    private fun flushExpired(atNanos: Long): List<FastMoveUseDerivation> {
        chargedPauses.filter { atNanos > it.end + DELIVERY_GRACE_NANOS }.forEach { it.held.clear() }
        val ready = resolvePending(pending).filter { resolved ->
            val use = resolved.use
            val impacts = use.evidence.filter { FastMoveUseEvidence.isImpact(it.kind) }
            val lastReceivedAt = impacts.maxOfOrNull { it.article.recordedAt } ?: use.firstImpactReceivedAt
            atNanos - use.anchorNanos > mergeWindow(use) + DELIVERY_GRACE_NANOS &&
                !chargedHpCamera.isAwaitingCharge(use.anchorNanos) &&
                // Preserve the same bounded grace for every phase in a pair.
                // An unpublished association may still be contradicted by late
                // geometry or a phase/appearance boundary during this interval.
                receiptWatermarkMillis - lastReceivedAt >= DELIVERY_GRACE_NANOS / 1_000_000L
        }
        pending.removeAll(ready.flatMap { it.members }.toSet())
        return ready.flatMap { finalize(it.use) }
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
        // The banner may reach custody after the relocated HP flash. Keep the
        // original measurements, but do not publish a charged-camera impact as
        // a Fast Move merely because the ordinary announcement tail elapsed.
        if (chargedHpCamera.isChargeImpact(use.anchorNanos)) return emptyList()
        val ordered = use.evidence.sortedWith(compareBy(
            { it.article.monotonicTimeNanos ?: Long.MAX_VALUE },
            { it.article.id.value }
        ))
        val anchor = ordered.firstOrNull { FastMoveUseEvidence.isImpact(it.kind) } ?: return emptyList()
        val evidenceKinds = ordered.map { it.kind }.distinct().sortedBy { EVIDENCE_ORDER.indexOf(it) }
        // Plateau frames explain association; correlated samples do not multiply
        // the independent witnesses' confidence or establish an attack by themselves.
        val confidence = (1.0 - ordered.filter { it.kind != "HP_PLATEAU_CONTINUITY" }.fold(1.0) { remaining, item ->
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
        const val MAX_DELAYED_PHASE_NANOS = 1_250_000_000L
        const val PLATEAU_ENDPOINT_NANOS = 150_000_000L
        const val MAX_PLATEAU_GAP_NANOS = 650_000_000L
        const val PHASE_HISTORY_NANOS = 6_000_000_000L
        const val MAX_HP_HISTORY = 160
        const val MAX_IMPACT_HISTORY = 96
        const val CHARGED_MAX_PHASE_NANOS = 12_000_000_000L
        const val MAX_HELD_CHARGE_EVIDENCE = 128
        const val CHARGED_IMPACT_TAIL_NANOS = 750_000_000L
        val EVIDENCE_ORDER = listOf(
            "HP_BORDER_PULSE",
            "HP_DAMAGE_TICK",
            "HP_PLATEAU_CONTINUITY",
            "RECIPIENT_VISUAL_ARTIFACT",
            "HP_BORDER_CADENCE",
            "HP_BAR_MOTION_COMPLETION",
            "RECIPIENT_VISUAL_CADENCE",
            "PLAYER_CHARGE_FILL_INCREASE"
        )
    }
}
