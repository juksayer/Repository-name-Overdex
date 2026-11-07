package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor

/** Completed-archive presentation intervals. No testimony or identity instant is changed. */
internal class ReplayKnockoutTrack(
    private val articles: List<ArchivedRealityArticle>,
    appearancesBySide: Map<String, List<ReplayIdentityObservation>>,
) {
    // Conflicting duplicate IDs cannot supply a unique custody link.
    private val articlesById = articles.groupBy { it.articleId }.mapValues { (_, copies) -> copies.singleOrNull() }

    val knockouts: List<ReplayKnockout> = articles.mapNotNull { article ->
        val faint = article.payload as? ArchivedActivePokemonFaintedWitnessed ?: return@mapNotNull null
        val name = faint.speciesName ?: return@mapNotNull null
        val at = article.monotonicTimeNanos ?: return@mapNotNull null
        val appearances = appearancesBySide[faint.side].orEmpty()
        val current = appearances.indexOfLast { it.atNanos <= at }
        if (current < 0) return@mapNotNull null
        fun matches(index: Int) = index >= 0 && key(appearances[index].speciesName) == key(name)
        val beyondLateWindow = at - appearances[current].atNanos > LATE_FAINT_NANOS
        val resourceEvidence = if (matches(current - 1)) {
            citedResourceDrop(article, faint, appearances[current - 1], appearances[current].atNanos)
        } else null
        val index = when {
            // A delayed stable count can arrive several seconds after the new
            // species. Extend only this exact outgoing appearance's interval
            // when the faint cites its identity and the same-side count drop.
            matches(current - 1) && (!beyondLateWindow || resourceEvidence != null) -> current - 1
            matches(current) -> current
            else -> return@mapNotNull null
        }
        val appearance = appearances[index]
        val replacement = appearances.getOrNull(index + 1)
        val start = if (replacement == null || at < replacement.atNanos) at else
            maxOf(appearance.atNanos, replacement.atNanos - MIN_VISIBLE_KO_NANOS)
        ReplayKnockout(
            side = faint.side, speciesName = name, appearanceAtNanos = appearance.atNanos,
            recordedAtNanos = at, presentationAtNanos = start,
            replacementAtNanos = replacement?.atNanos,
            sourceArticleIds = (listOf(article.articleId) + listOfNotNull(replacement?.articleId) +
                replacement?.evidenceArticleIds.orEmpty() + resourceEvidence?.articleIds.orEmpty()).distinct(),
            verifiedPokeBallsAfter = resourceEvidence?.remainingBalls,
        )
    }.distinctBy { it.side to it.appearanceAtNanos }

    /** A named actor alone cannot attach an arbitrarily delayed faint to a voluntary switch. */
    private fun citedResourceDrop(
        article: ArchivedRealityArticle,
        faint: ArchivedActivePokemonFaintedWitnessed,
        appearance: ReplayIdentityObservation,
        replacementAt: Long,
    ): ResourceDrop? {
        val at = article.monotonicTimeNanos ?: return null
        if (faint.basis !in setOf("${faint.side}_POKE_BALL_COUNT_DECREASE",
                "${faint.side}_POKE_BALL_COUNT_DECREASE_WITH_BATTLE_CRY")) return null
        val cited = (article.predecessorIds + article.evidenceReferences.orEmpty()).distinct()
            .mapNotNull { articlesById[it] }.filter { it.matchId == article.matchId }
        val identity = cited.singleOrNull { witness ->
            val species = witness.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@singleOrNull false
            val observedAt = witness.monotonicTimeNanos ?: return@singleOrNull false
            species.side == faint.side && key(species.speciesName) == key(appearance.speciesName) &&
                observedAt >= appearance.atNanos && observedAt < replacementAt
        } ?: return null
        val counts = cited.mapNotNull { it.pokeBallCount(faint.side) }.sortedBy { it.at }
        if (counts.size != 2) return null
        val (before, after) = counts
        // This path has a replacement, so a contradictory final 1 -> 0
        // reading cannot prove the outgoing actor's non-final knockout.
        if (before.at < appearance.atNanos || before.at >= replacementAt || after.at != at ||
            before.count - after.count != 1 || after.count < 1) return null
        // A stable unchanged count after entry proves a switch. Do not use a
        // later drop to retroactively turn that departure into a knockout.
        if (articles.any { witness ->
                witness.matchId == article.matchId && witness.pokeBallCount(faint.side)?.let {
                    it.at in replacementAt until after.at && it.count == before.count
                } == true
            }) return null
        return ResourceDrop(listOf(identity.articleId, before.articleId, after.articleId), after.count)
    }

    private data class ResourceDrop(val articleIds: List<String>, val remainingBalls: Int)

    private data class BallCount(val articleId: String, val at: Long, val count: Int)

    private fun ArchivedRealityArticle.pokeBallCount(side: String): BallCount? {
        val count = when (val value = payload) {
            is ArchivedPlayerPokeBallCountMeasured -> value.visibleCount.takeIf {
                side == "PLAYER" && value.maximumCount == 3
            }
            is ArchivedOpponentBattleResourceCountMeasured -> value.visibleCount.takeIf {
                side == "OPPONENT" && value.resource == "POKE_BALLS" && value.maximumCount == 3
            }
            else -> null
        } ?: return null
        return BallCount(articleId, monotonicTimeNanos ?: return null, count).takeIf { count in 0..3 }
    }

    fun at(side: String, cursor: Long): ReplayKnockout? = knockouts.lastOrNull {
        it.side == side && cursor >= it.presentationAtNanos &&
            (it.replacementAtNanos == null || cursor < it.replacementAtNanos)
    }

    companion object {
        private const val LATE_FAINT_NANOS = 2_000_000_000L
        const val MIN_VISIBLE_KO_NANOS = 650_000_000L
        private fun key(name: String) = TeamRosterSpeciesAttributor.speciesKey(name)
    }
}

internal data class ReplayKnockout(
    val side: String,
    val speciesName: String,
    val appearanceAtNanos: Long,
    val recordedAtNanos: Long,
    val presentationAtNanos: Long,
    val replacementAtNanos: Long?,
    val sourceArticleIds: List<String>,
    /** Completed-archive presentation only; the cited measurement retains its original clock. */
    val verifiedPokeBallsAfter: Int? = null,
) {
    // Leave a visible beat lying down before the next sprite replaces it.
    val finishByNanos: Long? get() = replacementAtNanos?.let {
        maxOf(presentationAtNanos + 1, it - minOf(100_000_000L, (it - presentationAtNanos) / 5))
    }
}
