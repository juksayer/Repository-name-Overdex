package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedRealityArticle

data class ReplayFinishingReception(val atNanos: Long, val articleIds: List<String>)

/** One conclusion about a supported occurrence. Landing is a Replay clock, independent of reception. */
sealed interface ReplayFinishingAttribution {
    val attackingSide: String
    val attackerSpeciesName: String
    val attackerAppearanceAtNanos: Long
    val recipientSide: String
    val recipientSpeciesName: String
    val recipientAppearanceAtNanos: Long
    val moveName: String?
    val moveSourceAtNanos: Long
    val landingAtNanos: Long
    val faintSourceAtNanos: Long
    val moveArticleIds: List<String>
    val faintArticleIds: List<String>
    val corroboratingArticleIds: List<String>
    val reception: ReplayFinishingReception?
    /** First source establishing departure by this time for a recovered faint. */
    val departedBy: ReplayTimeBound?
    val basis: String
    val evidenceArticleIds: List<String>
        get() = (moveArticleIds + reception?.articleIds.orEmpty() + faintArticleIds +
            corroboratingArticleIds).distinct().sorted()
}

internal object ReplayFinishingAttributions {
    fun resolve(
        candidates: List<ReplayFinishingAttribution>,
        permits: (ReplayFinishingAttribution) -> Boolean,
    ): List<ReplayFinishingAttribution> =
        evaluate(candidates) { if (permits(it)) null else "Credit withheld by the evidence." }.links

    data class Rejection(val candidate: ReplayFinishingAttribution, val reason: String)
    data class Selection(val links: List<ReplayFinishingAttribution>, val rejections: List<Rejection>)

    fun evaluate(candidates: List<ReplayFinishingAttribution>, reason: (ReplayFinishingAttribution) -> String?): Selection {
        val rejected = mutableListOf<Rejection>()
        val admitted = candidates.groupBy { it.recipientSide to it.recipientAppearanceAtNanos }.values.mapNotNull { links ->
            // A second path to the same occurrence corroborates it. A second occurrence competes.
            fun reject(reason: String): ReplayFinishingAttribution? {
                rejected += links.distinct().map { Rejection(it, reason) }
                return null
            }
            val first = links.first()
            if (links.any { it::class != first::class || it.attackingSide != first.attackingSide ||
                    it.attackerAppearanceAtNanos != first.attackerAppearanceAtNanos ||
                    it.moveSourceAtNanos != first.moveSourceAtNanos ||
                    it.moveArticleIds.intersect(first.moveArticleIds.toSet()).isEmpty() ||
                    (it.moveName != null && first.moveName != null && it.moveName != first.moveName) }) return@mapNotNull reject("Candidates disagree on the attack or its owner.")
            if (links.mapNotNull { it.moveName }.distinct().size > 1) return@mapNotNull reject("Candidates disagree on the move name.")
            val receptions = links.mapNotNull { it.reception }
            if (receptions.map { it.atNanos }.distinct().size > 1) return@mapNotNull reject("Candidates disagree on when the hit landed.")
            val preferred = links.firstOrNull { it.reception != null } ?: first
            val merged = merge(preferred, links, receptions)
            val veto = reason(merged)
            if (veto == null) merged else reject(veto)
        }.sortedWith(compareBy<ReplayFinishingAttribution> { it.landingAtNanos }.thenBy { it.recipientSide })

        return Selection(admitted, rejected)
    }

    private fun merge(
        preferred: ReplayFinishingAttribution,
        links: List<ReplayFinishingAttribution>,
        receptions: List<ReplayFinishingReception>,
    ): ReplayFinishingAttribution {
        val moveIds = links.flatMap { it.moveArticleIds }.distinct().sorted()
        val faintIds = links.flatMap { it.faintArticleIds }.distinct().sorted()
        val support = links.flatMap { it.corroboratingArticleIds }.distinct().sorted()
        val reception = receptions.firstOrNull()?.copy(articleIds = receptions.flatMap { it.articleIds }.distinct().sorted())
        val name = links.firstNotNullOfOrNull { it.moveName }
        val departedBy = links.mapNotNull { it.departedBy }
            .minWithOrNull(compareBy<ReplayTimeBound> { it.atNanos }.thenBy { it.articleId })
        return when (preferred) {
            is ReplayFinishingFastAssociation -> preferred.copy(moveName = name, moveArticleIds = moveIds,
                faintArticleIds = faintIds, audioArticleIds = support, reception = reception, departedBy = departedBy)
            is ReplayFinishingChargeAssociation -> preferred.copy(moveName = name, moveArticleIds = moveIds,
                faintArticleIds = faintIds, reception = reception, departedBy = departedBy,
                shieldArticleIds = links.filterIsInstance<ReplayFinishingChargeAssociation>().flatMap { it.shieldArticleIds }.distinct().sorted(),
                impactSupportArticleIds = links.filterIsInstance<ReplayFinishingChargeAssociation>().flatMap { it.impactSupportArticleIds }.distinct().sorted())
        }
    }
}

internal fun ArchivedRealityArticle.fastOccurrenceId(): String? =
    (payload as? ArchivedFastMoveUseObserved)?.useId
