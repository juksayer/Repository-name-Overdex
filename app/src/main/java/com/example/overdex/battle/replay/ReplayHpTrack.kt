package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedHpBarBorderPulse
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.observation.ActiveHpBarGeometry

/** Reject a pulse when its own captured frame proves the tracker left the active bar. */
internal class ReplayHpEvidence(articles: List<ArchivedRealityArticle>) {
    private val measured = articles.sortedBy { it.monotonicTimeNanos }.mapNotNull { article ->
        val bar = article.payload as? ArchivedActiveHpBarMeasured ?: return@mapNotNull null
        val at = article.monotonicTimeNanos ?: return@mapNotNull null
        Triple(bar.side, at, bar)
    }
    private val anchors = measured.filter { it.third.hasBarShape() }
        .groupBy { it.first }.mapValues { (_, bars) -> bars.first().third }
    private val byFrame = measured.associate { (side, at, bar) -> (side to at) to bar }

    fun accepts(article: ArchivedRealityArticle): Boolean {
        val side = when (val payload = article.payload) {
            is ArchivedActiveHpBarMeasured -> payload.side
            is ArchivedActiveHpBarDamageTickMeasured -> payload.damagedSide
            is ArchivedActiveHpBarBorderPulseObserved -> payload.damagedBarSide
            is ArchivedHpBarBorderPulse -> payload.barSide
            is ArchivedFastMoveUseObserved -> payload.damagedSide
            else -> return true
        }
        val at = article.monotonicTimeNanos ?: return true
        // Older compact archives may lack the co-timed geometry. That absence
        // alone does not contradict a preserved independent hit witness.
        val bar = byFrame[side to at] ?: return true
        // The Oct 11 player samples locked onto a short, thick shape on the
        // sprite (134×26), while the real bars were about 193×17. Such a shape
        // must not establish a near-empty HP baseline for the whole appearance.
        if (!bar.hasBarShape()) return false
        val anchor = anchors[side] ?: return true
        return ActiveHpBarGeometry.matchesSize(
            bar.barRight - bar.barLeft, bar.barBottom - bar.barTop,
            anchor.barRight - anchor.barLeft, anchor.barBottom - anchor.barTop
        )
    }

    fun acceptsSingleHit(article: ArchivedRealityArticle): Boolean = accepts(article) &&
        ((article.payload as? ArchivedActiveHpBarDamageTickMeasured)?.let { !isAccumulatedLoss(article, it) } ?: true)

    private fun isAccumulatedLoss(article: ArchivedRealityArticle, damage: ArchivedActiveHpBarDamageTickMeasured): Boolean {
        val at = article.monotonicTimeNanos ?: return false
        // Older detectors could wait through multiple HP levels, then report
        // their sum as a single late hit. A preceding, lower plateau disproves
        // that before-value. Keep the Article, but do not animate a fresh attack.
        val baselineAt = measured.lastOrNull { (side, time, bar) ->
            side == damage.damagedSide && time < at &&
                kotlin.math.abs(bar.filledFraction - damage.beforeFraction) <= .012f
        }?.second ?: return false
        val preceding = measured.filter { (side, time, bar) ->
            side == damage.damagedSide && time in baselineAt..(at - 350_000_000L) &&
                bar.hasBarShape() && bar.filledFraction < damage.beforeFraction - .012f
        }
        return preceding.zipWithNext().any { (a, b) ->
            b.second - a.second >= 90_000_000L &&
                b.third.filledFraction <= a.third.filledFraction + .006f
        }
    }

    private fun ArchivedActiveHpBarMeasured.hasBarShape(): Boolean {
        val height = barBottom - barTop
        return height > 0 && barRight - barLeft >= height * 6
    }
}

/** A bar belongs to its recipient. Never invert sides as Fast Move attribution does. */
internal class ReplayHpTrack(
    articles: List<ArchivedRealityArticle>,
    side: String,
    evidence: ReplayHpEvidence,
    fastMoveCycles: List<ReplayFastMoveCycle> = emptyList(),
) {
    private data class Sample(val at: Long, val fraction: Float, val before: Float? = null, val aligned: Boolean = false)
    private data class Damage(val at: Long, val before: Float, val aligned: Boolean)
    // Damage on our bar belongs to their move, and vice versa. Each bounded
    // cycle ends before either combatant changes, so HP cannot cross a switch.
    private val incoming = fastMoveCycles.filter { it.witness.side != side && it.hasReferenceDuration }

    private fun impactFor(at: Long): Long? = incoming.firstOrNull { cycle ->
        cycle.witness.damageTicks.any { it.observedAtNanos == at }
    }?.impactAtNanos ?: incoming.filter { it.coversHpSample(at) }
        .minByOrNull { cycle -> cycle.witness.sourceTimesNanos.minOf { kotlin.math.abs(it - at) } }
        ?.impactAtNanos

    private val samples = buildList {
        articles.forEach { article ->
            if (!evidence.accepts(article)) return@forEach
            val at = article.monotonicTimeNanos ?: return@forEach
            val fraction = when (val payload = article.payload) {
                is ArchivedActiveHpBarMeasured -> payload.filledFraction.takeIf { payload.side == side }
                is ArchivedActiveHpBarDamageTickMeasured -> payload.afterFraction.takeIf {
                    payload.damagedSide == side && payload.validDamage()
                }
                else -> null
            }
            if (fraction != null && fraction.validFraction()) {
                val impact = impactFor(at)
                add(Sample(impact ?: at, fraction,
                    (article.payload as? ArchivedActiveHpBarDamageTickMeasured)?.beforeFraction,
                    aligned = impact != null))
            }
        }
    }.groupBy { it.at }.map { (at, samples) ->
        // A draining bar can produce several readings for the same hit. Use the
        // measured final fraction and retain the whole orange damage chunk.
        Sample(at, samples.minOf { it.fraction }, samples.mapNotNull { it.before }.maxOrNull(),
            samples.any { it.aligned })
    }.sortedBy { it.at }
    private val pulses = (articles.mapNotNull { article ->
        if (!evidence.accepts(article)) return@mapNotNull null
        val present = when (val payload = article.payload) {
            is ArchivedActiveHpBarBorderPulseObserved -> payload.damagedBarSide == side
            is ArchivedHpBarBorderPulse -> payload.barSide == side && payload.status == "PRESENT"
            else -> false
        }
        article.monotonicTimeNanos.takeIf { present }?.let { impactFor(it) ?: it }
    } + incoming.map { it.impactAtNanos }).distinct().sorted()

    fun stateAt(cursor: Long, appearances: List<ReplayHpAppearance>, fainted: Boolean, matchStart: Long?): ReplayHpState {
        if (fainted) return ReplayHpState(filledFraction = 0f)
        // Repeated name reads do not reset the bar, but a new appearance does.
        // A late opening identity may use HP recorded before its name was resolved.
        // GO trainer battles do not heal an active combatant. Raw colour/edge
        // jitter (especially orange hit frames) must not animate regeneration.
        // Leave the archived measurements untouched and reset only on an entry.
        val current = appearances.lastOrNull()
            ?: return ReplayHpState(1f, assumedInitialFull = true)
        // Fresh entrants start full. This is a replay convention, never a new
        // HP witness. Returning combatants retain their own earlier measurements.
        var fraction = 1f
        var measured = false
        var damage: Damage? = null
        for ((index, appearance) in appearances.withIndex()) {
            if (appearance.speciesKey != current.speciesKey) continue
            val end = appearances.getOrNull(index + 1)?.start
            for (sample in samples) {
                if (sample.at > cursor) break
                if (sample.at < appearance.start || (end != null && sample.at >= end)) continue
                if (matchStart != null && sample.at < matchStart) continue
                // The orange trail starts at the fraction actually displayed,
                // including the full-health convention before the first hit.
                val before = fraction.takeIf { measured || sample.aligned } ?: sample.before
                if (!measured || sample.fraction < fraction) {
                    if (before != null && before - sample.fraction >= MIN_VISIBLE_DAMAGE) {
                        damage = Damage(sample.at, before, sample.aligned)
                    }
                    fraction = sample.fraction
                }
                measured = true
            }
        }
        val recentDamage = damage?.takeIf { it.at >= current.start && cursor - it.at < DAMAGE_TRAIL_NANOS }
        val pulse = pulses.lastOrNull { it in maxOf(current.start, matchStart ?: Long.MIN_VALUE)..cursor }
        return ReplayHpState(
            filledFraction = fraction,
            damageTrailFraction = recentDamage?.before?.takeIf { it > fraction },
            borderPulse = pulse != null && cursor - pulse < BORDER_PULSE_NANOS,
            assumedInitialFull = !measured,
            damageTimingReconstructed = recentDamage?.aligned == true,
        )
    }

    private fun Float.validFraction() = isFinite() && this in 0f..1f

    private fun ArchivedActiveHpBarDamageTickMeasured.validDamage() =
        beforeFraction.validFraction() && afterFraction.validFraction() && afterFraction <= beforeFraction

    companion object {
        // Presentation durations only; no extra hits or HP estimates are inferred.
        private const val DAMAGE_TRAIL_NANOS = 350_000_000L
        private const val BORDER_PULSE_NANOS = 180_000_000L
        private const val MIN_VISIBLE_DAMAGE = 0.006f
    }
}

data class ReplayHpState(
    /** First entries start full until measured; this convention is not archived testimony. */
    val filledFraction: Float?,
    /** Orange right-hand segment of a preserved damage tick, while briefly visible. */
    val damageTrailFraction: Float? = null,
    val borderPulse: Boolean = false,
    val assumedInitialFull: Boolean = false,
    /** Only the display timing is aligned; damage amounts still come from measurements. */
    val damageTimingReconstructed: Boolean = false,
)

internal data class ReplayHpAppearance(val speciesKey: String, val start: Long)
