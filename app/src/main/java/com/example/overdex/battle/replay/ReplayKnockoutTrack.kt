package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor

/** Completed-archive presentation intervals. No testimony or identity instant is changed. */
internal class ReplayKnockoutTrack(
    articles: List<ArchivedRealityArticle>,
    appearancesBySide: Map<String, List<ReplayIdentityObservation>>,
) {
    val knockouts: List<ReplayKnockout> = articles.mapNotNull { article ->
        val faint = article.payload as? ArchivedActivePokemonFaintedWitnessed ?: return@mapNotNull null
        val name = faint.speciesName ?: return@mapNotNull null
        val at = article.monotonicTimeNanos ?: return@mapNotNull null
        val appearances = appearancesBySide[faint.side].orEmpty()
        val current = appearances.indexOfLast { it.atNanos <= at }
        if (current < 0) return@mapNotNull null
        fun matches(index: Int) = index >= 0 && key(appearances[index].speciesName) == key(name)
        val index = when {
            matches(current) -> current
            // Badge counts sometimes arrive just after the replacement's name.
            // Only the immediately outgoing, explicitly named actor can qualify.
            matches(current - 1) && at - appearances[current].atNanos <= LATE_FAINT_NANOS -> current - 1
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
                replacement?.evidenceArticleIds.orEmpty()).distinct(),
        )
    }.distinctBy { it.side to it.appearanceAtNanos }

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
) {
    // Leave a visible beat lying down before the next sprite replaces it.
    val finishByNanos: Long? get() = replacementAtNanos?.let {
        maxOf(presentationAtNanos + 1, it - minOf(100_000_000L, (it - presentationAtNanos) / 5))
    }
}
