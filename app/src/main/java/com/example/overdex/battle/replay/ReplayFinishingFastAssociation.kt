package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import kotlin.math.abs

/** Projection only; the move, clip and faint keep their original evidence and clocks. */
data class ReplayFinishingFastAssociation(
    override val attackingSide: String,
    override val attackerSpeciesName: String,
    override val recipientSide: String,
    override val recipientSpeciesName: String,
    override val recipientAppearanceAtNanos: Long,
    override val moveName: String?,
    val impactAtNanos: Long,
    override val moveSourceAtNanos: Long,
    override val faintSourceAtNanos: Long,
    override val moveArticleIds: List<String>,
    val audioArticleIds: List<String>,
    override val faintArticleIds: List<String>,
    override val attackerAppearanceAtNanos: Long = Long.MIN_VALUE,
    override val reception: ReplayFinishingReception? = null,
    override val basis: String = "INFERRED_FAST_MOVE_WITH_CONFIRMED_LOSS",
    override val departedBy: ReplayTimeBound? = null,
) : ReplayFinishingAttribution {
    override val landingAtNanos get() = impactAtNanos
    override val corroboratingArticleIds get() = audioArticleIds
    internal fun faintPoseAt(): Long = when (basis) {
        ReplaySliverExchange.BASIS, ReplayMissingTickFinish.BASIS, ReplayLastOpponentFastFinish.MIXED_NEAR_DEPARTURE ->
            departedBy?.atNanos ?: impactAtNanos
        else -> impactAtNanos
    }
}

/** Links independently supported uses and faints; candidate sound does not create either. */
internal object ReplayFinishingFastAssociator {
    private const val SECOND = 1_000_000_000L
    fun resolve(articles: List<ArchivedRealityArticle>, candidates: List<ReplayMixedAudioCandidate>,
                cycles: List<ReplayFastMoveCycle>, knockouts: List<ReplayKnockout>,
                actorAt: (String, Long) -> ReplayCombatant?, appearanceAt: (String, Long) -> Long,
                acceptsHp: (ArchivedRealityArticle) -> Boolean): List<ReplayFinishingFastAssociation> {
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        fun key(s: String) = s.lowercase().filter(Char::isLetterOrDigit)
        return knockouts.mapNotNull { ko ->
            if (ko.sourceArticleIds.isEmpty() || ko.side !in setOf("PLAYER", "OPPONENT")) return@mapNotNull null
            val side = if (ko.side == "PLAYER") "OPPONENT" else "PLAYER"
            val relevant = cycles.filter { cycle ->
                cycle.witness.side == side && cycle.witness.moveName != null &&
                    cycle.witness.articleIds.isNotEmpty() && FastMoveUseEvidence.establishesUse(cycle.witness.evidenceKinds) &&
                    cycle.impactAtNanos <= ko.recordedAtNanos &&
                    actorAt(ko.side, cycle.impactAtNanos)?.let { key(it.speciesName) == key(ko.speciesName) } == true &&
                    appearanceAt(ko.side, cycle.impactAtNanos) == ko.appearanceAtNanos
            }
            val possible = relevant.mapNotNull { cycle ->
                val target = actorAt(ko.side, cycle.impactAtNanos) ?: return@mapNotNull null
                val attacker = actorAt(side, cycle.impactAtNanos) ?: return@mapNotNull null
                // A same-species cry cannot independently distinguish sides in a mirror.
                if (target.speciesId == null || attacker.speciesId == null || target.speciesId == attacker.speciesId)
                    return@mapNotNull null
                val audio = candidates.filter { key(it.moveName) == key(cycle.witness.moveName!!) &&
                    it.fragments.isNotEmpty() && abs(it.estimatedOnsetNanos - cycle.startedAtNanos) <= 2 * SECOND }
                    .singleOrNull() ?: return@mapNotNull null
                if (appearanceAt(side, cycle.startedAtNanos) != appearanceAt(side, cycle.impactAtNanos) ||
                    appearanceAt(ko.side, cycle.startedAtNanos) != ko.appearanceAtNanos ||
                    appearanceAt(side, audio.estimatedOnsetNanos) != appearanceAt(side, cycle.impactAtNanos) ||
                    appearanceAt(ko.side, audio.estimatedOnsetNanos) != ko.appearanceAtNanos) return@mapNotNull null
                // One clip cannot decide which of two nearby independent uses it describes.
                if (cycles.any { it !== cycle && it.witness.side == side &&
                        abs(audio.estimatedOnsetNanos - it.startedAtNanos) <= 2 * SECOND }) return@mapNotNull null
                val cry = ordered.lastOrNull { a ->
                    val p = a.payload as? ArchivedBattleCryLocalized ?: return@lastOrNull false
                    p.speciesId == target.speciesId && a.monotonicTimeNanos!! in cycle.impactAtNanos..(cycle.impactAtNanos + 3 * SECOND) &&
                        a.monotonicTimeNanos!! <= ko.recordedAtNanos && appearanceAt(ko.side, a.monotonicTimeNanos!!) == ko.appearanceAtNanos
                }
                val anchor = if (ko.presentationAtNanos - cycle.impactAtNanos in 0..(3 * SECOND)) ko.presentationAtNanos
                    else cry?.monotonicTimeNanos ?: return@mapNotNull null
                val sourceAt = cycle.witness.observedAtNanos
                if (sourceAt > anchor || anchor - sourceAt > 3 * SECOND ||
                    appearanceAt(side, sourceAt) != appearanceAt(side, cycle.impactAtNanos) ||
                    appearanceAt(ko.side, sourceAt) != ko.appearanceAtNanos) return@mapNotNull null
                if (appearanceAt(side, anchor) != appearanceAt(side, cycle.impactAtNanos) ||
                    appearanceAt(ko.side, anchor) != ko.appearanceAtNanos ||
                    cycles.any { it !== cycle && it.witness.side == side && it.impactAtNanos > cycle.impactAtNanos && it.impactAtNanos <= anchor }) return@mapNotNull null
                if (ordered.any { a ->
                    val at = a.monotonicTimeNanos!!
                    at > cycle.impactAtNanos && at <= anchor && when (val p = a.payload) {
                        is ArchivedFastMoveUseObserved -> p.attackingSide == side && a.articleId !in cycle.witness.articleIds
                        is ArchivedChargedMoveEnergySpent -> p.side == side
                        // An unassigned charge announcement can describe either side.
                        is ArchivedChargeMoveUsedAnnounced -> true
                        is ArchivedActiveHpBarMeasured -> p.side == ko.side && acceptsHp(a)
                        else -> false
                    }
                }) return@mapNotNull null
                ReplayFinishingFastAssociation(side, attacker.speciesName, ko.side, ko.speciesName,
                    ko.appearanceAtNanos, cycle.witness.moveName!!, cycle.impactAtNanos, sourceAt,
                    ko.recordedAtNanos, cycle.witness.articleIds.sorted(), audio.fragments.map { it.articleId }.distinct().sorted(),
                    (ko.sourceArticleIds + listOfNotNull(cry?.articleId)).distinct().sorted(),
                    attackerAppearanceAtNanos = appearanceAt(side, sourceAt))
            }
            possible.singleOrNull()
        }
    }
}
