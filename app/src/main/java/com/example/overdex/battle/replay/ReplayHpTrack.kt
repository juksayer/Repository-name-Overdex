package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedHpBarBorderPulse
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedChargeMoveUsedAnnounced
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedAttackIncoming
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedSpeciesCheckMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.observation.ActiveHpBarGeometry

/** Reject a pulse when its own captured frame proves the tracker left the active bar. */
internal class ReplayHpEvidence(
    articles: List<ArchivedRealityArticle>,
    private val contradictedEmptyIds: Set<String> = emptySet(),
) {
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
    private val anchors = measured.filter { it.third.hasBarShape() }
        .groupBy { it.first }.mapValues { (_, bars) -> bars.first().third }
    private val byFrame = measured.associate { (side, at, bar) -> (side to at) to bar }
    // A single effect-obscured fill can have perfectly valid outline geometry.
    // Do not let that reading permanently pin this actor's bar to a false low.
    // Reconcile only against several nearby independent measurements of the
    // same actor; never manufacture HP from a move's expected damage.
    private val contradictedFills = buildMap<Pair<String, Long>, MutableSet<Float>> {
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        for ((side, bars) in measured.groupBy { it.first }) {
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

    fun accepts(article: ArchivedRealityArticle): Boolean {
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
