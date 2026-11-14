package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import kotlin.math.abs
import kotlin.math.max

/** Completed-Replay evidence qualification, not a new camera or attack testimony.
 * A diagonal excursion, continuation and return, plus independent charge text,
 * disqualifies only the border comparison at departure. HP and audio stay independent. */
internal class ReplayCameraTransitionPulse(
    articles: List<ArchivedRealityArticle>,
    sameAppearance: (Long, Long) -> Boolean,
    acceptsHp: (ArchivedRealityArticle) -> Boolean,
) {
    data class Decision(val pulseId: String, val evidenceArticleIds: Set<String>)
    val decisions: Map<String, Decision>

    init {
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        val hpBySide = ordered.filter { it.payload is ArchivedActiveHpBarMeasured && acceptsHp(it) }
            .groupBy { (it.payload as ArchivedActiveHpBarMeasured).side }
        val charges = ordered.filter { it.payload is ArchivedChargeMoveUsedAnnounced }
        decisions = buildMap {
            for (pulse in ordered) {
                val p = pulse.payload as? ArchivedHpBarBorderPulse ?: continue
                if (p.status != "PRESENT") continue
                val at = pulse.monotonicTimeNanos!!
                // Multiple readers cannot turn contradictory same-frame geometry into proof.
                val frames = hpBySide[p.barSide].orEmpty().filter {
                    it.monotonicTimeNanos!! in (at - BASELINE_WINDOW)..(at + RETURN_WINDOW)
                }.groupBy { it.monotonicTimeNanos!! }.toSortedMap()
                if (frames.values.any { frame -> frame.map { it.payload }.distinct().size != 1 }) continue
                val rows = frames.values.map { it.first() }
                val onset = rows.singleOrNull { it.monotonicTimeNanos == at } ?: continue
                val before = rows.filter { it.monotonicTimeNanos!! < at }.takeLast(3)
                if (before.size != 3 || at - before.last().monotonicTimeNanos!! > FRESH_BASELINE ||
                    before.last().monotonicTimeNanos!! - before.first().monotonicTimeNanos!! < 90_000_000L) continue
                fun bar(a: ArchivedRealityArticle) = a.payload as ArchivedActiveHpBarMeasured
                val anchor = bar(before.last())
                val moved = bar(onset)
                if (!valid(anchor) || !valid(moved) || before.any { !near(anchor, bar(it)) } ||
                    !compatible(anchor, moved)) continue
                val dx = moved.barLeft - anchor.barLeft
                val dy = moved.barTop - anchor.barTop
                if (abs(dx) < max(4.0, (anchor.barRight - anchor.barLeft) * .10) ||
                    abs(dy) < max(4, (anchor.barBottom - anchor.barTop) * 2)) continue
                val after = rows.filter { it.monotonicTimeNanos!! > at }
                val continuation = after.firstOrNull() ?: continue
                val next = bar(continuation)
                if (continuation.monotonicTimeNanos!! - at > CONTINUATION_WINDOW || !compatible(anchor, next) ||
                    (next.barLeft - moved.barLeft) * dx <= 0 || (next.barTop - moved.barTop) * dy <= 0) continue
                val returned = after.drop(1).firstOrNull { near(anchor, bar(it)) } ?: continue
                val sequence = rows.filter { it.monotonicTimeNanos!! in at..returned.monotonicTimeNanos!! }
                if (sequence.any { !compatible(anchor, bar(it)) }) continue
                // A changed HP plateau may contain a real hit. Allow at most one
                // interior column of measurement jitter; never convert that tolerance
                // into zero HP or assume it disproves a subpixel attack.
                val column = 1f / ((anchor.barRight - anchor.barLeft) - 4).coerceAtLeast(1)
                if ((before + sequence).any {
                        val fill = bar(it).filledFraction
                        !fill.isFinite() || fill !in 0f..1f || abs(fill - anchor.filledFraction) > column
                    }) continue
                val charge = charges.firstOrNull { it.monotonicTimeNanos!! in at..(at + CHARGE_WINDOW) } ?: continue
                val from = before.first().monotonicTimeNanos!!
                val through = maxOf(returned.monotonicTimeNanos!!, charge.monotonicTimeNanos!!)
                if (!sameAppearance(from, through) || ordered.any {
                        it.monotonicTimeNanos!! in from..through &&
                            (it.payload is ArchivedMatchEnded || it.payload is ArchivedActivePokemonFaintedWitnessed ||
                                it.payload is ArchivedObservationRestartedByUser ||
                                it.payload is ArchivedScreenIgnoredByUser)
                    }) continue
                put(pulse.articleId, Decision(pulse.articleId,
                    (before + sequence + charge).map { it.articleId }.toSet() + pulse.articleId))
            }
        }
    }

    fun rejectsPulse(articleId: String) = articleId in decisions

    private fun valid(b: ArchivedActiveHpBarMeasured): Boolean {
        val w = b.barRight - b.barLeft
        val h = b.barBottom - b.barTop
        return b.sourceWidth != null && b.sourceHeight != null && h > 0 && w.toDouble() / h in 6.0..30.0 &&
            b.barLeft >= 0 && b.barTop >= 0 && b.barRight <= b.sourceWidth && b.barBottom <= b.sourceHeight
    }
    private fun compatible(a: ArchivedActiveHpBarMeasured, b: ArchivedActiveHpBarMeasured) =
        valid(b) && a.sourceWidth == b.sourceWidth && a.sourceHeight == b.sourceHeight &&
            abs((a.barRight - a.barLeft) - (b.barRight - b.barLeft)) <= max(3.0, (a.barRight - a.barLeft) * .10) &&
            abs((a.barBottom - a.barTop) - (b.barBottom - b.barTop)) <= 3
    private fun near(a: ArchivedActiveHpBarMeasured, b: ArchivedActiveHpBarMeasured) = compatible(a, b) &&
        abs(a.barLeft - b.barLeft) <= 3 && abs(a.barRight - b.barRight) <= 3 &&
        abs(a.barTop - b.barTop) <= 3

    private companion object {
        const val BASELINE_WINDOW = 2_000_000_000L
        const val FRESH_BASELINE = 750_000_000L
        const val CONTINUATION_WINDOW = 500_000_000L
        const val RETURN_WINDOW = 3_000_000_000L
        const val CHARGE_WINDOW = 10_000_000_000L
    }
}
