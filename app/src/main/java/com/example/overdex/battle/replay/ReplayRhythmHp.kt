package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedCompleteHpBarObserved
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedStatChangeObserved
import com.example.overdex.battle.observation.ChargedImpactHpSource
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Vetoes a decorative beat only when that move previously dealt measurable damage in this exchange. */
internal class ReplayRhythmHp(
    articles: List<ArchivedRealityArticle>,
    evidence: ReplayHpEvidence,
    supported: List<ReplayFastMoveCycle> = emptyList(),
) {
    private val samples = mutableMapOf<String, TreeMap<Long, ArchivedActiveHpBarMeasured>>()
    private val profiles = mutableMapOf<String, TreeMap<Long, ArchivedCompleteHpBarObserved>>()
    private val unusableProfiles = mutableMapOf<String, MutableSet<Long>>()
    private val uncertainFrames = mutableMapOf<String, java.util.TreeSet<Long>>()
    private val damageTrails = mutableMapOf<String, java.util.TreeSet<Long>>()
    private val statChanges = java.util.TreeSet<Long>()
    private val measuredUses = supported.filter { it.witness.damageTicks.isNotEmpty() }
        .groupBy { it.witness.side to it.witness.moveName }

    init {
        for (article in articles) {
            val time = article.monotonicTimeNanos ?: continue
            when (val hp = article.payload) {
                is ArchivedActiveHpBarMeasured -> {
                    val bars = samples.getOrPut(hp.side) { TreeMap() }
                    val width = hp.sourceWidth ?: 0
                    val height = hp.sourceHeight ?: 0
                    val conflicting = bars[time]?.let { it != hp } == true
                    if (conflicting || ChargedImpactHpSource.matches(article.sourceId) || hp.barLeft < 0 ||
                        hp.barTop < 0 || hp.barRight >= width || hp.barBottom >= height ||
                        !evidence.supportsSurvivalAtCapture(article)) {
                        uncertainFrames.getOrPut(hp.side) { java.util.TreeSet() }.add(time)
                    } else bars[time] = hp
                }
                is ArchivedCompleteHpBarObserved -> {
                    val previous = profiles.getOrPut(hp.side) { TreeMap() }.put(time, hp)
                    if ((previous != null && previous != hp) || article.confidence?.let { it in .8f..1f } != true ||
                        ChargedImpactHpSource.matches(article.sourceId)) {
                        unusableProfiles.getOrPut(hp.side) { mutableSetOf() } += time
                    }
                    if (hp.status != "LIVE_COLOR" || hp.liveColumns < 1) {
                        uncertainFrames.getOrPut(hp.side) { java.util.TreeSet() }.add(time)
                    }
                    if (hp.damageColumns > 0) damageTrails.getOrPut(hp.side) { java.util.TreeSet() }.add(time)
                }
                is ArchivedStatChangeObserved -> statChanges.add(time)
                else -> Unit
            }
        }
        // Some background columns are unclassifiable even on a clear sliver. Use the classified
        // live extent to cross-check the numeric endpoint, rather than treating background as HP.
        for (article in articles) {
            val hp = article.payload as? ArchivedCompleteHpBarObserved ?: continue
            val time = article.monotonicTimeNanos ?: continue
            val sample = samples[hp.side]?.get(time) ?: continue
            // The independent full-profile and fill locators can choose adjacent pixels
            // along the same anti-aliased outline. Compare counts in whole source columns.
            if (hp.interiorColumns <= 0 || abs(hp.barLeft - sample.barLeft) > 1 ||
                abs(hp.barRight - sample.barRight) > 1 || abs(hp.barTop - sample.barTop) > 1 ||
                abs(hp.barBottom - sample.barBottom) > 1 ||
                abs((sample.filledFraction * hp.interiorColumns).roundToInt() - hp.liveColumns) > 2) {
                uncertainFrames.getOrPut(hp.side) { java.util.TreeSet() }.add(time)
            }
        }
    }

    fun contradicts(anchor: ReplayFastMoveCycle, landingAtNanos: Long, windowStartNanos: Long, windowEndNanos: Long): Boolean {
        val move = anchor.witness
        val duration = move.durationNanos ?: return false
        val bars = samples[if (move.side == "PLAYER") "OPPONENT" else "PLAYER"] ?: return false
        // Allow existing schedule jitter and the HP reader's 350 ms settle period.
        val tolerance = minOf(400_000_000L, duration / 4)
        val beforeAt = landingAtNanos - tolerance
        val afterAt = landingAtNanos + tolerance + 350_000_000L
        val before = bars.floorEntry(beforeAt) ?: return false
        val after = bars.ceilingEntry(afterAt) ?: return false
        // A pair of actual samples can bracket one missed beat even on slower archived
        // captures. Use at most the ordinary damage reader's 1.25 s continuity interval.
        val bracketGap = (duration / 2).coerceIn(ReplayHpEvidence.FRESH_HP_NANOS, 1_250_000_000L)
        if (before.key < windowStartNanos || after.key >= windowEndNanos ||
            beforeAt - before.key > bracketGap || after.key - afterAt > bracketGap ||
            !replayHpBaselineContinues(before.value, after.value)) return false
        val invalid = uncertainFrames[before.value.side]
        if (invalid?.ceiling(before.key)?.let { it <= after.key } == true) return false
        if (damageTrails[before.value.side]?.ceiling(before.key)?.let { it <= after.key } == true) return false
        val plateau = bars.subMap(before.key, true, after.key, true).values
        // Allow for one column's rounding at each endpoint. A larger swing or relocated
        // bar is evidence of change or ambiguity; neither establishes an idle interval.
        val pixelError = pixelWidth(before.value) + pixelWidth(after.value)
        if (plateau.any { !replayHpBaselineContinues(before.value, it) } ||
            plateau.maxOf { it.filledFraction } - plateau.minOf { it.filledFraction } > pixelError) return false

        val from = maxOf(windowStartNanos, statChanges.floor(after.key) ?: windowStartNanos)
        val minLoss = minMeasuredLoss(anchor, bars, from, before.key) ?: return false
        return minLoss > max(0f, before.value.filledFraction - after.value.filledFraction) + pixelError
    }

    /** One positive, source-timed orange edge replaces two decorations only if two ordinary hits could not fit. */
    fun oneReception(anchor: ReplayFastMoveCycle, first: Long, second: Long, from: Long, until: Long): Long? {
        val duration = anchor.witness.durationNanos ?: return null
        if (second - first != duration || duration <= 0) return null
        val recipient = if (anchor.witness.side == "PLAYER") "OPPONENT" else "PLAYER"
        val bars = samples[recipient] ?: return null
        val full = profiles[recipient] ?: return null
        val tolerance = minOf(400_000_000L, duration / 4)
        val before = full.floorEntry(first - tolerance) ?: return null
        val after = full.ceilingEntry(second + tolerance + 350_000_000L) ?: return null
        if (before.key < from || before.key < anchor.impactAtNanos || first - before.key > duration ||
            after.key >= until || after.key - second > duration || after.value.damageColumns != 0 ||
            statChanges.ceiling(before.key)?.let { it <= after.key } == true) return null
        val firstBar = bars[before.key] ?: return null
        val lastBar = bars[after.key] ?: return null
        val width = before.value.interiorColumns
        val lossColumns = before.value.liveColumns - after.value.liveColumns
        if (lossColumns <= 2 || width <= 0 || after.value.liveColumns < 1) return null
        val series = full.subMap(before.key, true, after.key, true)
        // A low-confidence standalone numeric read can be ignored between these independently
        // qualified full profiles. A conflicting or obscured complete profile disqualifies the comparison.
        if (series.any { (t, p) ->
                val sample = bars[t]
                abs(p.interiorColumns - width) > 1 || p.status != "LIVE_COLOR" || p.liveColumns <= 0 ||
                    unusableProfiles[recipient]?.contains(t) == true || uncertainFrames[recipient]?.contains(t) == true ||
                    sample == null || !replayHpBaselineContinues(firstBar, sample)
            }) return null
        val edge = series.entries.firstOrNull { it.value.liveColumns != before.value.liveColumns } ?: return null
        if (edge.key !in first..second || edge.value.liveColumns != after.value.liveColumns ||
            abs(edge.value.damageColumns - lossColumns) > 1 ||
            series.tailMap(edge.key, true).values.any { it.liveColumns != after.value.liveColumns }) return null
        val error = pixelWidth(firstBar) + pixelWidth(lastBar)
        // All reliable numeric observations must also be consistent with this one drop.
        if (bars.subMap(before.key, true, after.key, true).any { (t, hp) ->
                val expected = if (t < edge.key) firstBar else lastBar
                !replayHpBaselineContinues(firstBar, hp) || abs(hp.filledFraction - expected.filledFraction) > error
            }) return null
        val measuredFrom = maxOf(from, before.key - 2 * duration, statChanges.floor(before.key) ?: from)
        val minLoss = minMeasuredLoss(anchor, bars, measuredFrom, before.key) ?: return null
        val observed = lossColumns.toFloat() / width
        if (minLoss > observed + error || 2 * minLoss <= observed + error) return null
        return edge.key
    }

    private fun minMeasuredLoss(anchor: ReplayFastMoveCycle, bars: TreeMap<Long, ArchivedActiveHpBarMeasured>,
                                from: Long, through: Long): Float? {
        val move = anchor.witness
        val invalid = uncertainFrames[if (move.side == "PLAYER") "OPPONENT" else "PLAYER"]

        val uses = measuredUses[move.side to move.moveName].orEmpty()
        val ticks = (sequenceOf(anchor) + uses.asSequence()).filter {
            it.impactAtNanos in from..through
        }.flatMap { it.witness.damageTicks.asSequence() }
        // A referenced tick must agree with actual source pixels on both sides of its
        // change. A multi-hit gap or an unmeasurably small tick is no damage baseline.
        return ticks.filter { it.observedAtNanos in from..through }.mapNotNull { tick ->
            val prior = bars.lowerEntry(tick.observedAtNanos) ?: return@mapNotNull null
            val settled = bars.ceilingEntry(tick.observedAtNanos) ?: return@mapNotNull null
            if (prior.key < from || tick.observedAtNanos - prior.key > ReplayHpEvidence.FRESH_HP_NANOS ||
                settled.key - tick.observedAtNanos > ReplayHpEvidence.FRESH_HP_NANOS ||
                invalid?.ceiling(prior.key)?.let { it <= settled.key } == true ||
                !replayHpBaselineContinues(prior.value, settled.value) ||
                abs(prior.value.filledFraction - tick.before) > pixelWidth(prior.value) ||
                abs(settled.value.filledFraction - tick.after) > pixelWidth(settled.value)) null
            else tick.before - tick.after - pixelWidth(prior.value) - pixelWidth(settled.value)
        }.minOrNull()
    }

    private fun pixelWidth(hp: ArchivedActiveHpBarMeasured): Float {
        val width = hp.barRight - hp.barLeft
        return 1f / (width - 2 * max(2, width / 80)).coerceAtLeast(1)
    }
}
