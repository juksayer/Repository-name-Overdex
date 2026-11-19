package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence

/** A bounded presentation estimate, never a replacement for a witnessed phase cue. */
data class ReplayChargeTransition(
    val side: String,
    val lastVisibleAtNanos: Long,
    val firstUnavailableAtNanos: Long,
    val confirmedUnavailableAtNanos: Long,
    val cueAtNanos: Long,
    val sourceArticleIds: Set<String>,
    val geometry: ReplayChargeControlGeometry,
    val basis: String = "CONTROL_RIM_LOSS_CORROBORATED_BY_PHASE_CUE",
) {
    /** The first sampled loss bounds presentation; the precise disappearance is unknown. */
    val presentationAtNanos get() = firstUnavailableAtNanos
}

data class ReplayChargeControlGeometry(
    val cropLeft: Int, val cropTop: Int, val cropWidth: Int, val cropHeight: Int,
    val sourceWidth: Int, val sourceHeight: Int,
)

/**
 * Reuses archived measurements without another image reader. UNKNOWN_RIM alone
 * means only that the circle could not be measured. A recent stable positive
 * baseline, sustained loss, continuous coverage and an explicit phase cue are
 * all required before using that interval to start a replay preparation earlier.
 */
internal class ReplayChargeTransitions(
    articles: List<ArchivedRealityArticle>,
    private val playerAt: (Long) -> ReplayCombatant?,
) {
    private val ordered = articles.filter { it.monotonicTimeNanos != null }
        .sortedWith(compareBy({ it.monotonicTimeNanos }, { it.sequenceNumber ?: 0L }))
    private val configuration = ArchivedPlayerTeamConfiguration(ordered)
    private val availability = ordered.filter { it.sourceId == SOURCE && it.payload is ArchivedWitnessOperating }
    private data class Frame(val at: Long, val readings: List<ArchivedRealityArticle>) {
        val values get() = readings.map { it.payload as ArchivedPlayerChargeButtonFillHeightMeasured }
        val geometry get() = values.firstOrNull()?.geometry()
        val complete get() = values.size == 2 && values.map { it.slot }.toSet() == setOf(1, 2) &&
            values.all { it.geometry() == geometry && it.validGeometry() }
        fun visible(slots: Set<Int>) = complete && values.all {
            if (it.slot in slots) it.rimPresent() else it.rimUnavailable()
        }
        fun unavailable() = complete && values.all { it.rimUnavailable() }
    }
    private val frames = ordered.filter { it.sourceId == SOURCE && it.payload is ArchivedPlayerChargeButtonFillHeightMeasured }
        .groupBy { it.monotonicTimeNanos!! }.map { (at, samples) -> Frame(at, samples) }.sortedBy { it.at }
    /** Positive readings of the configured battlefield controls can reopen ordinary Replay cycles. */
    val visibleControlTimes: List<Long> by lazy {
        frames.filter { frame ->
            frame.visible(setOf(1, 2)) || (frame.visible(setOf(1)) &&
                playerAt(frame.at)?.let { singleControlConfiguration(it, frame.at) } != null)
        }.map { it.at }
    }
    private val cues = ordered.filter { isReplayGetReadyCue(it) || isReplayAttackIncomingCue(it) }
    // A gap is reported on capture recovery, potentially after the phase cue.
    // Its complete interval matters, not only the time its article was emitted.
    private val captureGaps = ordered.mapNotNull { article ->
        val gap = article.payload as? ArchivedVisualCaptureGapObserved ?: return@mapNotNull null
        val end = article.monotonicTimeNanos!!
        (end - gap.durationNanos.coerceAtLeast(0L)).coerceAtLeast(0L) to end
    }
    private val barriers = buildList {
        val species = mutableMapOf<String, String>()
        ordered.forEach { article ->
            val barrier = when (val p = article.payload) {
                is ArchivedPlayerActiveIdentityResolved -> {
                    // A later resolution can distinguish two Raichu slots even
                    // when their raw badge text never changed. The appearance,
                    // not publication of the resolution, is the boundary.
                    if (p.appearanceStartedAtNanos in 0..article.monotonicTimeNanos!!) {
                        add(p.appearanceStartedAtNanos)
                    }
                    false
                }
                is ArchivedActivePokemonSpeciesWitnessed -> {
                    val name = key(p.speciesName)
                    val previous = species.put(p.side, name)
                    previous != null && previous != name
                }
                is ArchivedActivePokemonFaintedWitnessed, is ArchivedMatchEnded,
                ArchivedObservationRestartedByUser, ArchivedObservationSessionStoppedByUser,
                ArchivedScreenIgnoredByUser, ArchivedTeamSelectScanStartedByUser,
                ArchivedOutOfBattleMenuWitnessed, is ArchivedPlayerTeamSlotConfigured,
                is ArchivedPlayerTeamRosterConfirmation,
                ArchivedMatchStarted -> true
                else -> false
            }
            if (barrier) add(article.monotonicTimeNanos!!)
        }
    }
    private val byCue = cues.mapNotNull { cue -> refine(cue)?.let { cue.articleId to it } }.toMap()

    fun forCue(article: ArchivedRealityArticle): ReplayChargeTransition? = byCue[article.articleId]

    /**
     * A grade proves an ongoing QTE even if the opening text was missed. Between a
     * positive battlefield rim and the sampled loss of both controls, the start is
     * uncertain. Only speculative fast animations are withheld in this window.
     */
    fun speculativeQteWindows(supported: List<ReplayFastMoveCycle>): List<LongRange> {
        val grades = ordered.filter { replayQteProgressGrade(it) != null }.map { it.monotonicTimeNanos!! }.distinct()
        if (grades.isEmpty()) return emptyList()
        val visible = visibleControlTimes.toSet()
        val actions = actionTimes(supported)
        return grades.mapNotNull { grade ->
            val nearby = frames.filter { it.at in (grade - REPLAY_QTE_WINDOW_NANOS)..grade }
            val index = nearby.indexOfLast { it.at in visible }
            if (index < 0 || index == nearby.lastIndex) return@mapNotNull null
            val baseline = nearby[index]
            val losses = nearby.subList(index + 1, nearby.size)
            if (losses.any { !it.unavailable() || it.geometry != baseline.geometry } ||
                phaseBoundaries.higher(baseline.at)?.let { it <= grade } == true ||
                grades.any { it >= baseline.at && it < grade } ||
                captureGaps.any { (start, end) -> start < grade && end > baseline.at } ||
                availability.any { it.monotonicTimeNanos!! in baseline.at..grade &&
                    !(it.payload as ArchivedWitnessOperating).operating }) return@mapNotNull null
            val latestAction = actions.floor(grade)
            if (latestAction != null && latestAction >= losses.first().at) return@mapNotNull null
            // An already witnessed landing can follow the last visible reading.
            // It remains at its source time and narrows the left side of the interval.
            (maxOf(baseline.at, latestAction ?: baseline.at) + 1)..grade
        }
    }

    private val phaseBoundaries by lazy {
        (barriers + cues.map { it.monotonicTimeNanos!! } + ordered.mapNotNull { article ->
            article.monotonicTimeNanos.takeIf { article.payload is ArchivedChargedMoveEnergySpent ||
                article.payload is ArchivedChargeMoveUsedAnnounced || isReplayConnectionInterruption(article) }
        }).toCollection(java.util.TreeSet())
    }

    private fun actionTimes(supported: List<ReplayFastMoveCycle>) =
        (supported.flatMap { it.witness.sourceTimesNanos + it.impactAtNanos } +
            ordered.mapNotNull { article ->
                val use = article.payload as? ArchivedFastMoveUseObserved
                article.monotonicTimeNanos.takeIf { use != null && FastMoveUseEvidence.establishesUse(use.evidenceKinds) }
            }).toCollection(java.util.TreeSet())

    /** The result and a final sliver-to-damage transition corroborate loss of ordinary
     * battlefield controls. The first sampled loss can stop speculation without
     * assigning a faint, changing the result clock or suppressing a supported attack.
     */
    fun speculativeTerminalWindow(supported: List<ReplayFastMoveCycle>, hp: ReplayHpEvidence): LongRange? {
        val result = ordered.firstOrNull { it.payload is ArchivedMatchEnded } ?: return null
        val side = when ((result.payload as ArchivedMatchEnded).result) {
            "WIN" -> "OPPONENT"
            "LOSS" -> "PLAYER"
            else -> return null
        }
        val end = result.monotonicTimeNanos!!
        val recent = ordered.filter { it.monotonicTimeNanos!! in (end - TERMINAL_LOOKBACK)..end }
        val controls = frames.filter { it.at in (end - TERMINAL_LOOKBACK)..end }
        val visible = controls.indexOfLast { it.at in visibleControlTimes }
        if (visible < 0 || controls.lastIndex - visible < 2) return null
        val baseline = controls[visible]
        val firstLoss = controls[visible + 1].at
        if (controls.drop(visible + 1).any { !it.unavailable() || it.geometry != baseline.geometry } ||
            controls.drop(visible).zipWithNext().any { (a, b) -> b.at - a.at > MAX_RESULT_SAMPLE_GAP } ||
            end - controls.last().at > MAX_RESULT_SAMPLE_GAP) return null
        val change = ReplaySliverExchange.changes(recent, side).lastOrNull() ?: return null
        if (firstLoss - change.at !in 0..MAX_RISK_TO_CONTROL_LOSS ||
            !hp.supportsSurvivalAtCapture(change.liveMeasurement) ||
            controls.any { it.at in change.at until baseline.at && it.unavailable() }) return null
        val from = change.live.monotonicTimeNanos!!
        val counts = ordered.mapNotNull { a ->
            val count = when (val p = a.payload) {
                is ArchivedPlayerPokeBallCountMeasured -> p.visibleCount.takeIf { side == "PLAYER" && p.maximumCount == 3 }
                is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf { side == "OPPONENT" &&
                    p.resource == "POKE_BALLS" && p.maximumCount == 3 }
                else -> null
            }
            count?.let { a.monotonicTimeNanos!! to it }
        }
        val lastCountAt = counts.lastOrNull { it.first <= from }?.first ?: return null
        if (counts.filter { it.first == lastCountAt }.any { it.second != 1 } ||
            counts.any { (at, count) -> at in from..end && count !in 0..1 }) return null
        if (phaseBoundaries.ceiling(from)?.let { it < end } == true ||
            captureGaps.any { (start, finish) -> start < end && finish > from } ||
            availability.any { it.monotonicTimeNanos!! in from..end && !(it.payload as ArchivedWitnessOperating).operating } ||
            actionTimes(supported).ceiling(firstLoss)?.let { it <= end } == true) return null
        val conflicting = recent.any { a ->
            val at = a.monotonicTimeNanos!!
            at in from..end && (replayQteProgressGrade(a) != null ||
                (a.sourceId == "ANNOUNCEMENT_WITNESS" && (a.payload as? ArchivedRawText)?.value?.trim()?.startsWith("Go,", true) == true) ||
                (at >= change.at && when (val p = a.payload) {
                    is ArchivedActiveHpBarMeasured -> p.side == side && hp.supportsSurvivalAtCapture(a)
                    is ArchivedCompleteHpBarObserved -> p.side == side && p.status == "LIVE_COLOR" && p.liveColumns > 0
                    else -> false
                }))
        }
        return (firstLoss..end).takeUnless { conflicting }
    }

    private fun refine(cue: ArchivedRealityArticle): ReplayChargeTransition? {
        val at = cue.monotonicTimeNanos!!
        val nearby = frames.filter { it.at in (at - LOOKBACK_NANOS)..at }
        if (nearby.size < 4 || at - nearby.last().at > MAX_SAMPLE_GAP_NANOS) return null
        // A control that reappeared before the cue invalidates the proposed loss.
        val losses = nearby.asReversed().takeWhile { it.unavailable() }.asReversed()
        if (losses.size < 2) return null
        val firstMissing = losses.first()
        if (at - firstMissing.at > CUE_ASSOCIATION_NANOS) return null
        val baseline = nearby.filter { it.at < firstMissing.at }.takeLast(2)
        if (baseline.size != 2) return null
        val first = baseline.first()
        val combatant = playerAt(first.at) ?: return null
        // Also cover forms/slots reconstructed from old archive evidence rather
        // than a recorded resolution article. Repeated same-name OCR timestamps
        // do not split an appearance, but a different known actor does.
        if ((baseline + losses).map { it.at }.plus(at).any { sampleAt ->
                val current = playerAt(sampleAt)
                current == null || key(current.speciesName) != key(combatant.speciesName) ||
                    current.playerTeamSlot != combatant.playerTeamSlot ||
                    (current.speciesId != null && combatant.speciesId != null && current.speciesId != combatant.speciesId)
            }) return null
        val single = singleControlConfiguration(combatant, first.at)
        val slots = if (single != null) setOf(1) else setOf(1, 2)
        if (baseline.any { !it.visible(slots) }) return null
        val samples = baseline + losses
        if (samples.any { it.geometry != first.geometry } ||
            samples.zipWithNext().any { (a, b) -> b.at - a.at !in 1..MAX_SAMPLE_GAP_NANOS }) return null
        // The positive circles themselves must be stable; menu rings or a moving
        // aperture cannot establish the battlefield baseline.
        if (slots.any { slot ->
                val a = baseline[0].values.single { it.slot == slot }
                val b = baseline[1].values.single { it.slot == slot }
                val tolerance = maxOf(a.pixelTolerance, b.pixelTolerance, 2f)
                kotlin.math.abs(a.centerX!! - b.centerX!!) > tolerance ||
                    kotlin.math.abs(a.centerY!! - b.centerY!!) > tolerance ||
                    kotlin.math.abs(a.radius!! - b.radius!!) > tolerance
            }) return null
        val operating = availability.lastOrNull { it.monotonicTimeNanos!! <= first.at } ?: return null
        if (!(operating.payload as ArchivedWitnessOperating).operating || availability.any {
                it.monotonicTimeNanos!! in (first.at + 1)..at && !(it.payload as ArchivedWitnessOperating).operating
            }) return null
        if (captureGaps.any { (start, end) -> start < at && end > first.at }) return null
        if (barriers.any { it in (first.at + 1)..at } || cues.any {
                it.articleId != cue.articleId && it.monotonicTimeNanos!! in first.at..at &&
                    (it.monotonicTimeNanos < at || isReplayGetReadyCue(it) != isReplayGetReadyCue(cue))
            }) return null
        // A recorded action after the proposed boundary contradicts it. Do not
        // erase that action simply to make a longer charge animation fit.
        if (ordered.any { it.monotonicTimeNanos!! in firstMissing.at..at && when (it.payload) {
                    is ArchivedFastMoveUseObserved, is ArchivedChargedMoveEnergySpent,
                    ArchivedChargeMoveUsedAnnounced -> true
                    else -> false
                } }) return null
        return ReplayChargeTransition(
            side = if (isReplayGetReadyCue(cue)) "PLAYER" else "OPPONENT",
            lastVisibleAtNanos = baseline.last().at,
            firstUnavailableAtNanos = firstMissing.at,
            confirmedUnavailableAtNanos = losses[1].at,
            cueAtNanos = at,
            sourceArticleIds = (samples.flatMap { it.readings }.map { it.articleId } +
                listOfNotNull(operating.articleId, cue.articleId, single?.articleId)).toSet(),
            geometry = requireNotNull(first.geometry),
        )
    }

    /** One known move is different from only managing to read one of two controls. */
    private fun singleControlConfiguration(combatant: ReplayCombatant, at: Long): ArchivedRealityArticle? {
        val candidates = configuration.recordsAt(at).filter { article ->
            val p = article.payload as ArchivedPlayerTeamSlotConfigured
            key(p.speciesName) == key(combatant.speciesName) &&
                (combatant.playerTeamSlot == null || p.slot == combatant.playerTeamSlot)
        }
        return candidates.singleOrNull()?.takeIf {
            (it.payload as ArchivedPlayerTeamSlotConfigured).chargedMoveNames.filter(String::isNotBlank).size == 1
        }
    }

    private companion object {
        // Terminal controls in the 12:12 recording were sampled 2–3 seconds apart.
        const val MAX_RESULT_SAMPLE_GAP = 3_000_000_000L
        const val TERMINAL_LOOKBACK = 15_000_000_000L
        const val MAX_RISK_TO_CONTROL_LOSS = 5_000_000_000L
        const val SOURCE = "PLAYER_CHARGE_BUTTON_FILL_HEIGHT_WITNESS"
        const val MAX_SAMPLE_GAP_NANOS = 750_000_000L
        const val CUE_ASSOCIATION_NANOS = 2_000_000_000L
        const val LOOKBACK_NANOS = 3_500_000_000L
        fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
        fun ArchivedPlayerChargeButtonFillHeightMeasured.geometry() = ReplayChargeControlGeometry(
            cropLeft, cropTop, cropWidth, cropHeight, sourceWidth, sourceHeight)
        fun ArchivedPlayerChargeButtonFillHeightMeasured.validGeometry() =
            cropWidth > 0 && cropHeight > 0 && sourceWidth > 0 && sourceHeight > 0 &&
                cropLeft >= 0 && cropTop >= 0 && cropLeft.toLong() + cropWidth <= sourceWidth &&
                cropTop.toLong() + cropHeight <= sourceHeight && pixelTolerance.isFinite() && pixelTolerance >= 0f
        fun ArchivedPlayerChargeButtonFillHeightMeasured.rimPresent() =
            status in setOf("UNKNOWN_SEPARATOR", "EXPERIMENTAL_SEPARATOR") &&
                centerX != null && centerY != null && radius != null && radius in 8..(minOf(cropWidth, cropHeight) / 2) &&
                centerX in radius until (cropWidth - radius) && centerY in radius until (cropHeight - radius)
        fun ArchivedPlayerChargeButtonFillHeightMeasured.rimUnavailable() =
            status == "UNKNOWN_RIM" && centerX == null && centerY == null && radius == null
    }
}
