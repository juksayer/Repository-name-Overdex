package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
import com.example.overdex.battle.archive.ArchivedCompleteHpBarObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedHpBarBorderPulse
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedChargeMoveUsedAnnounced
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedAttackIncoming
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedSpeciesCheckMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.observation.ActiveHpBarGeometry
import com.example.overdex.battle.observation.ChargedImpactHpSource

/** Reject a pulse when its own captured frame proves the tracker left the active bar. */
internal class ReplayHpEvidence(
    articles: List<ArchivedRealityArticle>,
    private val contradictedEmptyIds: Set<String> = emptySet(),
) {
    private val completeFrames = articles.mapNotNull { a ->
        val p = a.payload as? ArchivedCompleteHpBarObserved ?: return@mapNotNull null
        a.monotonicTimeNanos?.let { (p.side to it) to p }
    }.groupBy({ it.first }, { it.second })

    fun hasCompleteProfile(side: String, at: Long): Boolean = (side to at) in completeFrames

    fun hasSettledProfile(side: String, at: Long): Boolean =
        completeFrames[side to at]?.all { it.status == "LIVE_COLOR" && it.damageColumns == 0 &&
            it.unmaskedUncertainColumns == 0 } == true

    /** A fresh ordinary-side outline and a separate same-frame fill measurement can
     * agree even when the camera's original size anchor belonged to a different bar. */
    fun corroboratedLiveProfile(article: ArchivedRealityArticle): Boolean {
        val p = article.payload as? ArchivedCompleteHpBarObserved ?: return false
        val at = article.monotonicTimeNanos ?: return false
        if (article.sourceId != "COMPLETE_${p.side}_HP_BAR_FRAME_WITNESS" ||
            (article.confidence ?: 0f) !in .80f..1f || !ReplaySliverExchange.whole(p, p.side) ||
            !ReplaySliverExchange.inBattlePosition(p) || p.status != "LIVE_COLOR" ||
            p.liveColumns <= 0 || p.damageColumns != 0 || p.unmaskedUncertainColumns != 0 ||
            completeFrames[p.side to at]?.distinct()?.singleOrNull() != p) return false
        return ordinaryReadings[p.side to at]?.all { sample ->
            val bar = sample.payload as ArchivedActiveHpBarMeasured
            (sample.confidence ?: 0f) in .80f..1f && bar.sourceWidth == p.sourceWidth && bar.sourceHeight == p.sourceHeight &&
                bar.barLeft == p.barLeft && bar.barRight == p.barRight &&
                kotlin.math.abs(bar.barTop - p.barTop) <= 1 && kotlin.math.abs(bar.barBottom - p.barBottom) <= 1 &&
                bar.filledFraction in 0f..1f && bar.filledFraction > 0 &&
                kotlin.math.abs(bar.filledFraction * p.interiorColumns - p.liveColumns) <= 2f &&
                bar.filledFraction !in contradictedFills[p.side to at].orEmpty()
        } == true
    }

    private val ordinaryReadings = articles.filter {
        val bar = it.payload as? ArchivedActiveHpBarMeasured
        bar != null && it.sourceId == "${bar.side}_ACTIVE_HP_BAR_WITNESS"
    }.groupBy { (it.payload as ArchivedActiveHpBarMeasured).side to it.monotonicTimeNanos }

    /** Legacy numeric readings can corroborate ownership at their own capture time.
     * They cannot establish current terminal survival without classified live fill.
     */
    fun supportsSurvivalAtCapture(article: ArchivedRealityArticle): Boolean {
        val hp = article.payload as? ArchivedActiveHpBarMeasured ?: return false
        val at = article.monotonicTimeNanos ?: return false
        return article.confidence?.let { it.isFinite() && it in .80f..1f } == true &&
            hp.filledFraction.isFinite() && hp.filledFraction > 0f && hp.filledFraction <= 1f &&
            hp.hasBarShape() && accepts(article) &&
            (hp.fillColor == null || hp.fillColor in setOf("GREEN", "YELLOW", "RED")) &&
            completeFrames[hp.side to at].orEmpty().all { it.status == "LIVE_COLOR" && it.liveColumns > 0 }
    }

    /** Source-time survival evidence, not an exact-HP confidence probability.
     * A last-known display value must never be carried forward across a later hit.
     * The caller also owns the appearance/side interval.
     */
    fun confirmsLiveHp(article: ArchivedRealityArticle, at: Long = article.monotonicTimeNanos ?: Long.MIN_VALUE): Boolean =
        confirmsLiveHp(article, at, requireMatchAnchor = true)

    /** A native received tick and a repeatedly settled bar can verify survival
     * locally when the first bar of the match had different geometry. */
    fun corroboratedPostHitSurvivalAt(
        receipt: ArchivedRealityArticle, by: Long, sameAppearance: (Long) -> Boolean,
    ): Long? {
        val tick = receipt.payload as? ArchivedActiveHpBarDamageTickMeasured ?: return null
        val hit = receipt.monotonicTimeNanos ?: return null
        if (receipt.sourceId != "${tick.damagedSide}_ACTIVE_HP_BAR_DAMAGE_TICK_WITNESS" ||
            tick.beforeFraction !in 0f..1f || tick.afterFraction !in 0f..tick.beforeFraction ||
            tick.afterFraction == tick.beforeFraction || !sameAppearance(hit)) return null
        val baseline = ordinaryReadings[tick.damagedSide to hit]?.singleOrNull() ?: return null
        if (!confirmsLiveHp(baseline, hit, requireMatchAnchor = false)) return null
        val bar = baseline.payload as ArchivedActiveHpBarMeasured
        if (bar.filledFraction != tick.afterFraction) return null
        val width = bar.barRight - bar.barLeft
        val height = bar.barBottom - bar.barTop
        return ordinaryReadings.asSequence().mapNotNull { (key, reads) ->
            val (side, time) = key
            val at = time ?: return@mapNotNull null
            if (side != tick.damagedSide || at <= hit || at > by || !sameAppearance(at)) return@mapNotNull null
            at.takeIf { reads.all { sample ->
                val live = sample.payload as ArchivedActiveHpBarMeasured
                live.filledFraction == bar.filledFraction && live.fillColor == bar.fillColor &&
                    live.sourceWidth == bar.sourceWidth && live.sourceHeight == bar.sourceHeight &&
                    kotlin.math.abs(live.barLeft - bar.barLeft) <= width / 4 &&
                    kotlin.math.abs(live.barTop - bar.barTop) <= height * 3 &&
                    ActiveHpBarGeometry.matchesSize(live.barRight - live.barLeft, live.barBottom - live.barTop, width, height) &&
                    confirmsLiveHp(sample, at, requireMatchAnchor = false)
            } }
        }.sortedDescending().take(2).toList().takeIf { it.size == 2 }?.first()
    }

    private fun confirmsLiveHp(article: ArchivedRealityArticle, at: Long, requireMatchAnchor: Boolean): Boolean {
        val hp = article.payload as? ArchivedActiveHpBarMeasured ?: return false
        val capturedAt = article.monotonicTimeNanos ?: return false
        if (at < capturedAt || at - capturedAt > FRESH_HP_NANOS ||
            article.confidence?.let { it.isFinite() && it in .80f..1f } != true ||
            !hp.filledFraction.isFinite() || hp.filledFraction <= 0f || hp.filledFraction > 1f ||
            hp.fillColor !in setOf("GREEN", "YELLOW", "RED") || !accepts(article, requireMatchAnchor)) return false
        val width = hp.sourceWidth ?: return false
        val height = hp.sourceHeight ?: return false
        if (hp.barLeft < 0 || hp.barTop < 0 || hp.barRight > width || hp.barBottom >= height ||
            width <= 0 || height <= 0) return false
        // A complete same-frame color profile can contradict the numeric fraction.
        // Missing color support is unknown; orange is not live HP and not measured zero.
        val newestProfileAt = completeFrames.keys.filter { it.first == hp.side && it.second in capturedAt..at }
            .maxOfOrNull { it.second } ?: capturedAt
        return completeFrames[hp.side to newestProfileAt].orEmpty().all {
            it.status == "LIVE_COLOR" && it.liveColumns > 0 &&
                it.barLeft == hp.barLeft && it.barRight == hp.barRight &&
                it.barTop == hp.barTop && it.barBottom == hp.barBottom
        }
    }

    companion object {
        // Conservative presentation freshness bound; not a KO timeout.
        const val FRESH_HP_NANOS = 500_000_000L
    }
    private val contradictedEmptyFrames = articles.mapNotNull { article ->
        val hp = article.payload as? ArchivedActiveHpBarMeasured ?: return@mapNotNull null
        val at = article.monotonicTimeNanos ?: return@mapNotNull null
        (hp.side to at).takeIf { article.articleId in contradictedEmptyIds }
    }.toSet()
    private val measured = articles.sortedBy { it.monotonicTimeNanos }.mapNotNull { article ->
        val bar = article.payload as? ArchivedActiveHpBarMeasured ?: return@mapNotNull null
        val at = article.monotonicTimeNanos ?: return@mapNotNull null
        Triple(bar.side, at, bar)
    }
    private val go = articles.filter { it.payload is ArchivedMatchStarted }.minOfOrNull { it.monotonicTimeNanos ?: Long.MAX_VALUE }
    private val anchors = measured.filter { it.second >= (go ?: Long.MIN_VALUE) && it.third.hasBarShape() }
        .groupBy { it.first }.mapValues { (_, bars) -> bars.first().third }
    private val byFrame = measured.associate { (side, at, bar) -> (side to at) to bar }
    // A single effect-obscured fill can have perfectly valid outline geometry.
    // Do not let that reading permanently pin this actor's bar to a false low.
    // Reconcile only against several nearby independent measurements of the
    // same actor; never manufacture HP from a move's expected damage.
    private val weakFillFrames = articles.mapNotNull { a ->
        val hp = a.payload as? ArchivedActiveHpBarMeasured ?: return@mapNotNull null
        (hp.side to a.monotonicTimeNanos).takeIf {
            a.confidence?.let { !it.isFinite() || it !in .80f..1f } == true
        }
    }.toSet()
    private val contradictedFills = buildMap<Pair<String, Long>, MutableSet<Float>> {
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        for ((side, bars) in measured.filter { (it.first to it.second) !in weakFillFrames }.groupBy { it.first }) {
            val identities = java.util.TreeMap<Long, String?>()
            val boundaries = java.util.TreeSet<Long>()
            var previousIdentity: String? = null
            var countBaseline: Int? = null
            ordered.forEach { article ->
                val time = article.monotonicTimeNanos!!
                when (val payload = article.payload) {
                    is ArchivedActivePokemonSpeciesWitnessed -> if (payload.side == side) {
                        val identity = TeamLossResolver.speciesKey(payload.speciesName)
                        if (previousIdentity != null && identity != previousIdentity) boundaries += time
                        identities[time] = if (identities.containsKey(time) && identities[time] != identity) null else identity
                        previousIdentity = identity
                    }
                    is ArchivedActivePokemonFaintedWitnessed -> if (payload.side == side) boundaries += time
                    is ArchivedSpeciesCheckMeasured -> if (payload.side == side && payload.status == "OPENED" &&
                        payload.reason in setOf("ENTRY", "SWITCH", "FAINT", "EMPTY_HP")) boundaries += payload.triggerMonotonicNanos
                    is ArchivedChargeMoveUsedAnnounced, is ArchivedGetReadyWitnessed,
                    is ArchivedAttackIncoming, is ArchivedMatchEnded -> boundaries += time
                    else -> Unit
                }
                val count = when (val payload = article.payload) {
                    is ArchivedPlayerPokeBallCountMeasured -> payload.visibleCount.takeIf { side == "PLAYER" }
                    is ArchivedOpponentBattleResourceCountMeasured -> payload.visibleCount.takeIf {
                        side == "OPPONENT" && payload.resource == "POKE_BALLS" }
                    else -> null
                }
                if (count != null && count in 0..3) {
                    if (countBaseline != null && count < countBaseline!!) boundaries += time
                    if (countBaseline == null || count <= countBaseline!!) countBaseline = count
                }
            }
            fun identityAt(at: Long): String? = identities.floorEntry(at)?.value
            val frames = java.util.TreeMap(bars.groupBy { it.second })
            bars.forEach { (_, at, candidate) ->
                if (!candidate.hasBarShape() || candidate.filledFraction <= 0f) return@forEach
                val identity = identityAt(at) ?: return@forEach
                val nextFrames = frames.tailMap(at, false).values.asSequence()
                    .takeWhile { it.first().second - at <= 1_500_000_000L }.take(3).toList()
                if (nextFrames.size != 3 ||
                    nextFrames.last().first().second - nextFrames.first().first().second < 90_000_000L) return@forEach
                // Multiple readers of the same image are one frame. Every
                // co-timed reading must agree, independent of archive order.
                val next = nextFrames.flatten()
                if (next.any { (_, time, bar) ->
                        identityAt(time) != identity || !bar.hasBarShape() ||
                            bar.filledFraction < candidate.filledFraction + .12f ||
                            !ActiveHpBarGeometry.matchesSize(bar.barRight - bar.barLeft, bar.barBottom - bar.barTop,
                                candidate.barRight - candidate.barLeft, candidate.barBottom - candidate.barTop) ||
                            kotlin.math.abs(bar.barLeft - candidate.barLeft) > (candidate.barRight - candidate.barLeft) / 4 ||
                            kotlin.math.abs(bar.barTop - candidate.barTop) > (candidate.barBottom - candidate.barTop) * 3
                    } || next.maxOf { it.third.filledFraction } - next.minOf { it.third.filledFraction } > .025f
                ) return@forEach
                // A transition captured at the candidate's own timestamp has
                // ambiguous ownership too. Initial identification alone is not
                // a transition and does not prevent reconciling that frame.
                val crossedBoundary = boundaries.ceiling(at)?.let { it <= next.last().second } == true
                if (!crossedBoundary) getOrPut(side to at) { mutableSetOf() } += candidate.filledFraction
            }
        }
    }

    fun accepts(article: ArchivedRealityArticle): Boolean = accepts(article, requireMatchAnchor = true)

    private fun accepts(article: ArchivedRealityArticle, requireMatchAnchor: Boolean): Boolean {
        if (article.payload is ArchivedActiveHpBarMeasured) {
            // Legacy missing confidence can retain historical display data, but cannot
            // pass confirmsLiveHp. Explicitly weak/invalid values cannot create damage.
            if (article.confidence?.let { !it.isFinite() || it !in .80f..1f } == true) return false
        }
        if (article.articleId in contradictedEmptyIds && article.payload is ArchivedActiveHpBarMeasured) return false
        val tick = article.payload as? ArchivedActiveHpBarDamageTickMeasured
        if (tick?.afterFraction == 0f && (tick.damagedSide to article.monotonicTimeNanos) in contradictedEmptyFrames) return false
        val side = when (val payload = article.payload) {
            is ArchivedActiveHpBarMeasured -> payload.side
            is ArchivedActiveHpBarDamageTickMeasured -> payload.damagedSide
            is ArchivedActiveHpBarBorderPulseObserved -> payload.damagedBarSide
            is ArchivedHpBarBorderPulse -> payload.barSide
            is ArchivedFastMoveUseObserved -> payload.damagedSide
            else -> return true
        }
        val at = article.monotonicTimeNanos ?: return true
        // A bad fill does not disprove an independently visible border pulse
        // or supported attack in the same frame. Reject only its HP value.
        val fraction = when (val payload = article.payload) {
            is ArchivedActiveHpBarMeasured -> payload.filledFraction
            is ArchivedActiveHpBarDamageTickMeasured -> payload.afterFraction
            else -> null
        }
        if (fraction != null && fraction > 0f && fraction in (contradictedFills[side to at] ?: emptySet())) return false
        // Older compact archives may lack the co-timed geometry. That absence
        // alone does not contradict a preserved independent hit witness.
        val bar = byFrame[side to at] ?: return true
        // The Oct 11 player samples locked onto a short, thick shape on the
        // sprite (134×26), while the real bars were about 193×17. Such a shape
        // must not establish a near-empty HP baseline for the whole appearance.
        if (!bar.hasBarShape()) return false
        val anchor = anchors[side]?.takeIf { requireMatchAnchor } ?: return true
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
    private val allowLastSeen: (ArchivedRealityArticle) -> Boolean = { true },
    private val sameAppearance: (Long, Long) -> Boolean = { _, _ -> false },
    uninterrupted: (Long, Long) -> Boolean = { _, _ -> true },
) {
    private val hpObservations = articles.filter { (it.payload as? ArchivedActiveHpBarMeasured)?.side == side }
        .sortedBy { it.monotonicTimeNanos }
    private val hpEvidence = evidence
    private val lastSeenObservations = hpObservations.filter { observation ->
        val at = observation.monotonicTimeNanos
        at != null && !ChargedImpactHpSource.matches(observation.sourceId) &&
            allowLastSeen(observation) && evidence.confirmsLiveHp(observation, at)
    }
    private val reconciliationBaselines = lastSeenObservations.filter { row ->
        val at = requireNotNull(row.monotonicTimeNanos)
        evidence.hasSettledProfile(side, at)
    }

    private data class Sample(
        val at: Long, val fraction: Float, val before: Float? = null, val aligned: Boolean = false,
        val projectedUntil: Long? = null,
    )
    private data class Damage(val at: Long, val before: Float, val aligned: Boolean)
    // The displayed projectile and its HP feedback use the same landing clock.
    private val incoming = fastMoveCycles.filter { it.witness.side != side }.sortedBy { it.impactAtNanos }
    private val measuredIncoming = incoming.filter {
        it.hasReferenceDuration && (it.witness.articleIds.isNotEmpty() ||
            ReplayFastMoveRhythm.HP_RECEPTION in it.witness.evidenceKinds)
    }

    // A centered charge-camera bar can briefly belong to the other Pokémon.
    // Check its ownership against ordinary frames on either side before using
    // a low value for the rest of this appearance.
    private val contradictedCameraSamples = buildSet {
        val ordinary = hpObservations.filter {
            !ChargedImpactHpSource.matches(it.sourceId) && evidence.supportsSurvivalAtCapture(it)
        }.groupBy { it.monotonicTimeNanos!! }.toSortedMap()
        val frames = java.util.TreeMap(ordinary)
        for (article in hpObservations) {
            if (!ChargedImpactHpSource.matches(article.sourceId)) continue
            val at = article.monotonicTimeNanos ?: continue
            val camera = article.payload as ArchivedActiveHpBarMeasured
            val before = frames.lowerEntry(at) ?: continue
            val after = frames.higherEntry(at) ?: continue
            val settled = frames.higherEntry(after.key) ?: continue
            if (at - before.key > 1_500_000_000L || settled.key - at > 2_500_000_000L ||
                !sameAppearance(before.key, settled.key)) continue
            val bars = (before.value + after.value + settled.value).map { it.payload as ArchivedActiveHpBarMeasured }
            val first = bars.first()
            val tolerance = 3f / (first.barRight - first.barLeft).coerceAtLeast(1)
            if (bars.all { it.sourceWidth == first.sourceWidth && it.sourceHeight == first.sourceHeight &&
                    kotlin.math.abs(it.barLeft - first.barLeft) <= 3 &&
                    kotlin.math.abs(it.barRight - first.barRight) <= 3 &&
                    kotlin.math.abs(it.filledFraction - first.filledFraction) <= tolerance &&
                    it.filledFraction - camera.filledFraction > .12f }) add(article.articleId)
        }
    }

    // A settled damage tick can be published for the last frame of a small
    // oscillation. Present that measured step once, on its attack's landing.
    private fun earlyStepSamples(tickTimes: java.util.NavigableSet<Long>,
                                 uninterrupted: (Long, Long) -> Boolean): Set<String> {
        val frames = java.util.TreeMap(hpObservations.filter {
            !ChargedImpactHpSource.matches(it.sourceId) && hpEvidence.supportsSurvivalAtCapture(it)
        }.groupBy { it.monotonicTimeNanos!! })
        return buildSet {
            for (cycle in measuredIncoming) {
                val tick = cycle.witness.damageTicks.singleOrNull() ?: continue
                val at = tick.observedAtNanos
                val start = maxOf(cycle.startedAtNanos, at - 750_000_000L)
                if (start >= at || cycle.impactAtNanos < at) continue
                val confirmation = frames[at]?.singleOrNull() ?: continue
                val bar = confirmation.payload as ArchivedActiveHpBarMeasured
                val pixel = 1f / (bar.barRight - bar.barLeft - 4).coerceAtLeast(1)
                fun agrees(reading: ArchivedRealityArticle, fraction: Float): Boolean {
                    val hp = reading.payload as ArchivedActiveHpBarMeasured
                    return replayHpBaselineContinues(bar, hp) && kotlin.math.abs(hp.filledFraction - fraction) <= pixel
                }
                if (!agrees(confirmation, tick.after) || tick.before - tick.after <= 2 * pixel) continue
                val first = frames.subMap(start, true, at, false).entries.firstOrNull { (_, reads) ->
                    reads.all { agrees(it, tick.after) }
                } ?: continue
                val previous = frames.lowerEntry(first.key) ?: continue
                if (first.key - previous.key > 750_000_000L || !previous.value.all { agrees(it, tick.before) }) continue
                val next = frames.higherEntry(at) ?: continue
                val settled = frames.higherEntry(next.key) ?: continue
                if (settled.key - at > 750_000_000L || !uninterrupted(previous.key, settled.key) ||
                    !sameAppearance(previous.key, settled.key) || tickTimes.higher(previous.key) != at ||
                    incoming.between(previous.key, settled.key).any { it !== cycle } ||
                    !(next.value + settled.value).all { agrees(it, tick.after) }) continue
                val early = frames.subMap(first.key, true, at, false).values.flatten()
                val divergent = early.filterNot { agrees(it, tick.after) }
                // Protect terminal slivers, classified fill, and repeat observations.
                // Only a single uncorroborated dip between agreeing frames can be jitter.
                if (divergent.size > 1 || divergent.any { sample ->
                        val hp = sample.payload as ArchivedActiveHpBarMeasured
                        hp.filledFraction <= 3 * pixel || !replayHpBaselineContinues(bar, hp) ||
                            hp.filledFraction >= tick.after || sample.confidence == null ||
                            sample.confidence >= (confirmation.confidence ?: 0f) ||
                            hpEvidence.hasCompleteProfile(side = hp.side, at = sample.monotonicTimeNanos!!)
                    }) continue
                early.forEach { add(it.articleId) }
            }
        }
    }

    private fun impactFor(at: Long, fraction: Float? = null): Long? {
        val candidates = if (fraction == null) measuredIncoming else measuredIncoming.filter { cycle ->
            // A separate lower measurement, especially a tiny live sliver, need
            // not belong to the preceding attack just because it was nearby.
            cycle.witness.damageTicks.isEmpty() || cycle.witness.damageTicks.any {
                fraction >= it.after - .006f && fraction <= it.before + .006f
            }
        }
        return candidates.firstOrNull { cycle ->
            cycle.witness.damageTicks.any { it.observedAtNanos == at }
        }?.impactAtNanos ?: candidates.firstOrNull {
            ReplayFastMoveRhythm.HP_RECEPTION in it.witness.evidenceKinds && it.impactAtNanos == at
        }?.impactAtNanos ?: candidates.filter {
            it.witness.sourceTimesNanos.isNotEmpty() && it.coversHpSample(at)
        }.minByOrNull { cycle -> cycle.witness.sourceTimesNanos.minOf { kotlin.math.abs(it - at) } }?.impactAtNanos
    }

    private val samples = buildList {
        // Preserve an individually witnessed damage tick. When several supported
        // hits share a single measured loss, show steps through that loss only
        // between two trustworthy source-time endpoints on the same pair.
        val ticks = articles.filter { a ->
            val tick = a.payload as? ArchivedActiveHpBarDamageTickMeasured
            tick?.damagedSide == side && tick.validDamage() && evidence.accepts(a)
        }.groupBy { it.monotonicTimeNanos }
        val tickTimes = java.util.TreeSet(ticks.keys.filterNotNull())
        val earlySteps = earlyStepSamples(tickTimes, uninterrupted)
        val captured = hpObservations.filter { evidence.accepts(it) && it.monotonicTimeNanos != null }
            .groupBy { it.monotonicTimeNanos!! }.toSortedMap()
        val redistributed = mutableSetOf<Long>()
        for ((before, after) in captured.values.zipWithNext()) {
            val first = before.singleOrNull() ?: continue
            val last = after.singleOrNull() ?: continue
            val from = first.monotonicTimeNanos!!
            val until = last.monotonicTimeNanos!!
            val a = first.payload as ArchivedActiveHpBarMeasured
            val b = last.payload as ArchivedActiveHpBarMeasured
            if (until - from > MAX_DISTRIBUTION_GAP_NANOS ||
                !allowLastSeen(first) || !allowLastSeen(last) ||
                ChargedImpactHpSource.matches(first.sourceId) || ChargedImpactHpSource.matches(last.sourceId) ||
                !evidence.confirmsLiveHp(first) || !evidence.confirmsLiveHp(last) ||
                !replayHpBaselineContinues(a, b) || !uninterrupted(from, until) ||
                tickTimes.higher(from)?.let { it < until } == true ||
                ticks[until].orEmpty().any { evidence.acceptsSingleHit(it) }) continue
            val hits = incoming.between(from, until)
            val exemplar = hits.firstOrNull()?.witness ?: continue
            if (hits.size < 2 || exemplar.moveName == null || exemplar.durationNanos == null ||
                hits.any {
                    it.witness.articleIds.isEmpty() || it.witness.moveName != exemplar.moveName ||
                        it.witness.durationNanos != exemplar.durationNanos || it.witness.damageTicks.isNotEmpty() ||
                        it.witness.windowStartNanos != exemplar.windowStartNanos ||
                        it.witness.windowEndNanos != exemplar.windowEndNanos
                } || from < exemplar.windowStartNanos || until >= exemplar.windowEndNanos) continue
            val drop = (a.filledFraction - b.filledFraction) / hits.size
            val pixel = 1f / (b.barRight - b.barLeft - 4).coerceAtLeast(1)
            if (drop < maxOf(MIN_VISIBLE_DAMAGE, pixel)) continue
            redistributed += until
            hits.forEachIndexed { index, hit ->
                val fraction = if (index == hits.lastIndex) b.filledFraction
                    else (a.filledFraction - drop * (index + 1)).coerceAtLeast(b.filledFraction)
                add(Sample(hit.impactAtNanos, fraction, aligned = true, projectedUntil = until))
            }
        }
        articles.forEach { article ->
            if (article.articleId in earlySteps || article.articleId in contradictedCameraSamples || !evidence.accepts(article)) return@forEach
            val at = article.monotonicTimeNanos ?: return@forEach
            if (at in redistributed) return@forEach
            val fraction = when (val payload = article.payload) {
                is ArchivedActiveHpBarMeasured -> payload.filledFraction.takeIf { payload.side == side }
                is ArchivedActiveHpBarDamageTickMeasured -> payload.afterFraction.takeIf {
                    payload.damagedSide == side && payload.validDamage()
                }
                else -> null
            }
            if (fraction != null && fraction.validFraction()) {
                val owner = if (article.payload is ArchivedActiveHpBarMeasured && !ChargedImpactHpSource.matches(article.sourceId))
                    measuredIncoming.singleOrNull { cycle ->
                        at < cycle.impactAtNanos && cycle.witness.damageTicks.any { tick ->
                            at >= tick.observedAtNanos && fraction == tick.after
                        }
                    } else null
                val impact = owner?.impactAtNanos ?: impactFor(at, fraction)
                add(Sample(impact ?: at, fraction,
                    (article.payload as? ArchivedActiveHpBarDamageTickMeasured)?.beforeFraction,
                    aligned = impact != null))
            }
        }
    }.groupBy { it.at }.map { (at, samples) ->
        // A draining bar can produce several readings for the same hit. Use the
        // measured final fraction and retain the whole orange damage chunk.
        Sample(at, samples.minOf { it.fraction }, samples.mapNotNull { it.before }.maxOrNull(),
            samples.any { it.aligned }, samples.mapNotNull { it.projectedUntil }.maxOrNull())
    }.sortedBy { it.at }
    private val pulses = (articles.mapNotNull { article ->
        if (!evidence.accepts(article)) return@mapNotNull null
        val present = when (val payload = article.payload) {
            is ArchivedActiveHpBarBorderPulseObserved -> payload.damagedBarSide == side
            is ArchivedHpBarBorderPulse -> payload.barSide == side && payload.status == "PRESENT"
            else -> false
        }
        article.monotonicTimeNanos.takeIf { present }?.let { impactFor(it) ?: it }
    } + incoming.filter { it.hasReferenceDuration }.map { it.impactAtNanos }).distinct().sorted()

    private fun List<ReplayFastMoveCycle>.between(from: Long, until: Long): List<ReplayFastMoveCycle> {
        val index = binarySearchBy(from + 1) { it.impactAtNanos }.let { if (it < 0) -it - 1 else it }
        return listIterator(index).asSequence().takeWhile { it.impactAtNanos <= until }.toList()
    }

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
        var projectedUntil: Long? = null
        for ((index, appearance) in appearances.withIndex()) {
            if (appearance.speciesKey != current.speciesKey) continue
            val end = appearances.getOrNull(index + 1)?.start
            // A fresh matching badge lets a subsequent settled whole bar repair
            // a contradicted baseline. Historical readings keep their source time.
            val baseline = if (appearance.reconfirmed) reconciliationBaselines.firstOrNull {
                val at = it.monotonicTimeNanos!!
                at in appearance.start..cursor && (end == null || at < end)
            } else null
            if (baseline != null) {
                fraction = (baseline.payload as ArchivedActiveHpBarMeasured).filledFraction
                measured = true
                projectedUntil = null
                damage = null
            }
            for (sample in samples) {
                if (baseline != null && sample.at <= baseline.monotonicTimeNanos!!) continue
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
                    projectedUntil = sample.projectedUntil
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
            assumedInitialFull = !measured && hpObservations.none {
                it.monotonicTimeNanos?.let { time -> time in maxOf(current.start, matchStart ?: current.start)..cursor } == true
            },
            damageTimingReconstructed = recentDamage?.aligned == true,
            estimatedBetweenMeasurements = projectedUntil?.let { cursor < it } == true,
            currentHpConfirmed = projectedUntil?.let { cursor >= it } != false && hpObservations.lastOrNull {
                it.monotonicTimeNanos?.let { time -> time in maxOf(current.start, matchStart ?: current.start)..cursor } == true
            }?.let { sample ->
                hpEvidence.confirmsLiveHp(sample, cursor) && measuredIncoming.none {
                    it.impactAtNanos > sample.monotonicTimeNanos!! && it.impactAtNanos <= cursor
                }
            } == true,
            lastSeenFraction = lastSeenObservations.lastOrNull { observation ->
                val at = observation.monotonicTimeNanos
                at != null && at in maxOf(current.start, matchStart ?: current.start)..cursor
            }?.let { (it.payload as ArchivedActiveHpBarMeasured).filledFraction },
        )
    }

    private fun Float.validFraction() = isFinite() && this in 0f..1f

    private fun ArchivedActiveHpBarDamageTickMeasured.validDamage() =
        beforeFraction.validFraction() && afterFraction.validFraction() && afterFraction <= beforeFraction

    companion object {
        private const val MAX_DISTRIBUTION_GAP_NANOS = 6_000_000_000L
        // Presentation durations only.
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
    /** The display timing is aligned to a supported or measured reception. */
    val damageTimingReconstructed: Boolean = false,
    /** The fraction can be historical; only this flag permits displaying it as current live HP. */
    val currentHpConfirmed: Boolean = false,
    /** Historical display only; never evidence of survival at the replay cursor. */
    val lastSeenFraction: Float? = null,
    val estimatedBetweenMeasurements: Boolean = false,
)

internal data class ReplayHpAppearance(val speciesKey: String, val start: Long, val reconfirmed: Boolean = false)
