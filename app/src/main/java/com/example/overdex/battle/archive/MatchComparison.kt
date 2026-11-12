package com.example.overdex.battle.archive

import com.example.overdex.battle.history.BattleMetricKind
import com.example.overdex.battle.history.BattleRecordProjector
import java.util.Locale

/** A fixed history selection; an unavailable archive must never become a different match. */
data class MatchComparisonEntry(
    val matchId: String,
    val archive: MatchArchive?,
    val error: String? = null,
)

data class MatchComparisonPlayerUses(
    val speciesName: String,
    val fastMoveUses: Int,
    val chargedMoveUses: Int,
)

data class ComparedMatch(
    val matchId: String,
    val summary: MatchSummary?,
    val playerUses: List<MatchComparisonPlayerUses> = emptyList(),
    /** Recorded uses which the existing per-species projector could not attribute. */
    val unattributedPlayerFastMoveUses: Int = 0,
    val unattributedPlayerChargedMoveUses: Int = 0,
    val error: String? = null,
)

/** Read-only metadata projection. No media is opened and no missing attack is manufactured. */
data class MatchComparison(
    val matches: List<ComparedMatch>,
    /** Every Fast/Charged bar in every match uses this same scale, including zero-only batches. */
    val maximumUses: Int,
) {
    companion object {
        fun project(entries: List<MatchComparisonEntry>): MatchComparison {
            val matches = entries.distinctBy { it.matchId }.map { entry ->
                val archive = entry.archive
                when {
                    archive == null -> ComparedMatch(entry.matchId, null, error = entry.error)
                    archive.matchId != entry.matchId -> ComparedMatch(entry.matchId, null,
                        error = "The saved match does not match this selection.")
                    else -> projectMatch(archive).copy(error = entry.error)
                }
            }
            val maximum = matches.maxOfOrNull { match ->
                maxOf(match.playerUses.maxOfOrNull { maxOf(it.fastMoveUses, it.chargedMoveUses) } ?: 0,
                    match.unattributedPlayerFastMoveUses, match.unattributedPlayerChargedMoveUses)
            } ?: 0
            return MatchComparison(matches, maximum.coerceAtLeast(1))
        }

        private fun projectMatch(archive: MatchArchive): ComparedMatch {
            // BattleRecordProjector expects one match. A mixed/imported ledger
            // must not credit the selected team with another match's attacks.
            val selected = archive.copy(articles = archive.articles.filter { it.matchId == archive.matchId })
            val summary = selected.summarize()
            val record = BattleRecordProjector.project(selected)
            val uses = record.events.filter {
                it.kind == BattleMetricKind.FAST_MOVE || it.kind == BattleMetricKind.CHARGED_MOVE
            }.groupBy { speciesKey(it.speciesName) }
            val names = (summary.player.species + uses.values.map { it.first().speciesName })
                .distinctBy(::speciesKey)
            val playerUses = names.map { name ->
                val events = uses[speciesKey(name)].orEmpty()
                MatchComparisonPlayerUses(name,
                    events.count { it.kind == BattleMetricKind.FAST_MOVE },
                    events.count { it.kind == BattleMetricKind.CHARGED_MOVE })
            }
            return ComparedMatch(archive.matchId, summary, playerUses,
                (summary.player.fastMoveUses - playerUses.sumOf { it.fastMoveUses }).coerceAtLeast(0),
                (summary.player.chargedMoveUses - playerUses.sumOf { it.chargedMoveUses }).coerceAtLeast(0))
        }

        private fun speciesKey(name: String): String = name.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
    }
}
