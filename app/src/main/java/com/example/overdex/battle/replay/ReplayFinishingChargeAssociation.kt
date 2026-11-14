package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** A completed-archive causal projection. Neither source clock is an impact measurement. */
data class ReplayFinishingChargeAssociation(
    val attackingSide: String,
    val attackerSpeciesName: String,
    val attackerAppearanceAtNanos: Long,
    val recipientSide: String,
    val recipientSpeciesName: String,
    val recipientAppearanceAtNanos: Long,
    val moveName: String,
    val moveSourceAtNanos: Long,
    val faintSourceAtNanos: Long,
    val presentationImpactAtNanos: Long,
    val replacementAtNanos: Long?,
    val moveArticleIds: List<String>,
    val faintArticleIds: List<String>,
    val shieldArticleIds: List<String>,
    val basis: String = "UNCONTESTED_CHARGE_TO_RESOLVED_COUNT_FAINT",
) {
    val evidenceArticleIds: List<String> get() = (moveArticleIds + faintArticleIds + shieldArticleIds).distinct().sorted()
}

internal data class ReplayFinishingChargeCandidate(
    val side: String,
    val moveName: String?,
    val preparationAtNanos: Long,
    val sourceAtNanos: Long,
    val impactAtNanos: Long,
    val articleIds: Set<String>,
    val completed: Boolean,
)

/** Deliberately scoped to one resolved team loss followed by a known replacement.
 * Final-result-only endings, aggregate losses and unresolved mirror performers stay separate.
 */
internal object ReplayFinishingChargeAssociator {
    private const val DEPARTURE_WINDOW = 8_000_000_000L
    private const val SHIELD_CONFIRMATION_TAIL = 2_000_000_000L

    fun resolve(
        articles: List<ArchivedRealityArticle>,
        charges: List<ReplayFinishingChargeCandidate>,
        knockouts: List<ReplayKnockout>,
        fastMoves: List<ReplayFastMoveCycle>,
        actorAt: (String, Long) -> ReplayCombatant?,
        appearanceAt: (String, Long) -> Long,
        nextAppearance: (String, Long) -> Long?,
        acceptsHp: (ArchivedRealityArticle) -> Boolean,
    ): List<ReplayFinishingChargeAssociation> {
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        return knockouts.mapNotNull { ko ->
            val replacement = ko.replacementAtNanos ?: return@mapNotNull null
            if (ko.basis != ReplayTeamLossProjection.BASIS || ko.verifiedPokeBallsAfter == null ||
                ko.verifiedPokeBallsAfter !in 1..2 || ko.recordedAtNanos < replacement) return@mapNotNull null
            val candidates = charges.filter { charge ->
                charge.completed && charge.moveName != null && charge.side in setOf("PLAYER", "OPPONENT") &&
                    charge.side != ko.side && charge.articleIds.isNotEmpty() &&
                    replacement - charge.sourceAtNanos in 1..DEPARTURE_WINDOW &&
                    charge.impactAtNanos < replacement
            }
            // Another completion (even unnamed or of unknown side) leaves the cause ambiguous.
            val charge = candidates.singleOrNull() ?: return@mapNotNull null
            if (charges.any { it !== charge && it.sourceAtNanos in charge.sourceAtNanos..replacement }) return@mapNotNull null
            val attacker = actorAt(charge.side, charge.sourceAtNanos) ?: return@mapNotNull null
            val target = actorAt(ko.side, charge.sourceAtNanos) ?: return@mapNotNull null
            val attackerAppearance = appearanceAt(charge.side, charge.sourceAtNanos)
            val targetAppearance = appearanceAt(ko.side, charge.sourceAtNanos)
            if (targetAppearance != appearanceAt(ko.side, ko.appearanceAtNanos) ||
                target.speciesName != ko.speciesName ||
                appearanceAt(charge.side, charge.preparationAtNanos) != attackerAppearance ||
                appearanceAt(ko.side, charge.preparationAtNanos) != targetAppearance ||
                nextAppearance(ko.side, charge.sourceAtNanos) != replacement ||
                nextAppearance(charge.side, charge.preparationAtNanos)?.let { it <= replacement } == true ||
                knockouts.any { it.side == charge.side && it.appearanceAtNanos == attackerAppearance &&
                    it.presentationAtNanos <= replacement }) return@mapNotNull null
            // A pending or later hit, including an unnamed one, cannot be reassigned to the charge.
            // Use both original impact observations and the existing resolved schedule.
            if (fastMoves.any { cycle ->
                    cycle.impactAtNanos in charge.preparationAtNanos until replacement ||
                        cycle.witness.sourceTimesNanos.any { it in charge.preparationAtNanos until replacement }
                }) return@mapNotNull null
            // Even a canonical use excluded from decorative scheduling is a competing
            // recorded claim. Do not resolve that dispute by awarding this charge the KO.
            if (ordered.any { it.payload is ArchivedFastMoveUseObserved &&
                    it.monotonicTimeNanos!! in charge.preparationAtNanos until replacement }) return@mapNotNull null
            if (ordered.any { article ->
                    val at = article.monotonicTimeNanos!!
                    at in (charge.sourceAtNanos + 1) until replacement && when (val p = article.payload) {
                        is ArchivedGetReadyWitnessed, is ArchivedAttackIncoming,
                        is ArchivedObservationRestartedByUser, is ArchivedObservationSessionStoppedByUser,
                        is ArchivedScreenIgnoredByUser, is ArchivedMatchEnded -> true
                        // A surviving sliver is a contradiction, never rounded down to a KO.
                        is ArchivedActiveHpBarMeasured -> p.side == ko.side && p.filledFraction > 0f &&
                            at >= charge.impactAtNanos && acceptsHp(article)
                        else -> false
                    }
                }) return@mapNotNull null
            val shields = ordered.mapNotNull { a -> shieldCount(a, ko.side)?.let { Triple(a.monotonicTimeNanos!!, it, a.articleId) } }
            val before = shields.lastOrNull { it.first in charge.preparationAtNanos..charge.sourceAtNanos }
                ?: return@mapNotNull null
            val after = shields.firstOrNull { it.first in replacement..(replacement + SHIELD_CONFIRMATION_TAIL) }
                ?: return@mapNotNull null
            val bracket = shields.filter { it.first in before.first..after.first }
            if (bracket.any { it.second != before.second } || ordered.any {
                    it.monotonicTimeNanos!! in replacement..after.first &&
                        (it.payload is ArchivedGetReadyWitnessed || it.payload is ArchivedAttackIncoming ||
                            it.payload is ArchivedChargeMoveUsedAnnounced || it.payload is ArchivedChargedMoveEnergySpent)
                }) return@mapNotNull null
            ReplayFinishingChargeAssociation(charge.side, attacker.speciesName, attackerAppearance,
                ko.side, ko.speciesName, ko.appearanceAtNanos, requireNotNull(charge.moveName),
                charge.sourceAtNanos, ko.recordedAtNanos, charge.impactAtNanos, replacement,
                charge.articleIds.sorted(), ko.sourceArticleIds.sorted(), bracket.map { it.third })
        }
    }

    private fun shieldCount(a: ArchivedRealityArticle, side: String): Int? = when (val p = a.payload) {
        is ArchivedPlayerShieldCountMeasured -> p.visibleCount.takeIf { side == "PLAYER" && p.maximumCount == 2 }
        is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf {
            side == "OPPONENT" && p.resource == "SHIELDS" && p.maximumCount == 2 }
        else -> null
    }?.takeIf { it in 0..2 }
}
