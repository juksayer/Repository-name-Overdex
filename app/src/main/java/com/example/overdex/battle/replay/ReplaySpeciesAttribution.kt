package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.observation.EntryAnnouncementSideTracker
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource

/** Only repairs presentation; the original side conclusions stay in the archive. */
internal fun archivedEntrySide(name: String, at: Long, articles: List<ArchivedRealityArticle>): String? {
    val tracker = EntryAnnouncementSideTracker()
    articles.filter { isSideLocatedSpeciesSource(it.sourceId) &&
        (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= at + ENTRY_BADGE_WINDOW }.forEach { article ->
        val species = article.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@forEach
        val side = ActivePokemonSide.entries.firstOrNull { it.name == species.side } ?: return@forEach
        tracker.observeBadge(side, species.speciesName, article.articleId,
            article.monotonicTimeNanos ?: return@forEach)
    }
    return tracker.attribute(name, latestArchivedPlayerRoster(articles, at), allowRosterExclusion = true)?.side?.name
}

/** Current side-located badges outrank a roster inference; mirrors stay unsided. */
internal fun archivedNamedMoveSide(name: String, at: Long, articles: List<ArchivedRealityArticle>): String? {
    val active = articles.filter { isSideLocatedSpeciesSource(it.sourceId) &&
        (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= at }
        .mapNotNull { it.payload as? ArchivedActivePokemonSpeciesWitnessed }.associateBy { it.side }
    val sides = active.filterValues { key(it.speciesName) == key(name) }.keys
    if (sides.size > 1) return null
    sides.singleOrNull()?.let { return it }
    return TeamRosterSpeciesAttributor.sideFor(name, latestArchivedPlayerRoster(articles, at))?.name
}

/** A contradicted saved team must not make every unlisted player an opponent. */
internal fun latestArchivedPlayerRoster(articles: List<ArchivedRealityArticle>, at: Long): List<String> {
    val bySlot = mutableMapOf<Int, String>()
    val configuration = ArchivedPlayerTeamConfiguration(articles)
    val configuredIds = configuration.recordsAt(at).map { it.articleId }.toSet()
    articles.filter { (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= at }
        .sortedBy { it.monotonicTimeNanos }.forEach {
        when (val payload = it.payload) {
            ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser -> bySlot.clear()
            is ArchivedPlayerTeamRosterConfirmation -> if (payload.confirmed) {
                bySlot.clear()
                payload.speciesBySlot.take(3).forEachIndexed { index, species ->
                    if (!species.isNullOrBlank()) bySlot[index + 1] = species
                }
            }
            is ArchivedPlayerTeamSlotConfigured -> if (it.articleId in configuredIds) bySlot[payload.slot] = payload.speciesName
            is ArchivedPlayerTeamRosterSlotWitnessed -> bySlot[payload.slot] =
                com.example.overdex.data.PokemonFormReference.mergeRosterName(
                    bySlot[payload.slot], payload.speciesName, payload.spriteVariant != null)
            else -> Unit
        }
    }
    val roster = bySlot.values.toList()
    val keys = roster.map(::key).toSet()
    // Completed replay can use later badge evidence to reject a stale roster.
    val contradicted = articles.any { article ->
        val badge = article.payload as? ArchivedActivePokemonSpeciesWitnessed
        isSideLocatedSpeciesSource(article.sourceId) && badge?.side == "PLAYER" && key(badge.speciesName) !in keys
    }
    return if (contradicted) emptyList() else roster
}

internal fun configuredLeadContradicted(articles: List<ArchivedRealityArticle>): Boolean {
    val start = articles.firstOrNull { it.payload is ArchivedMatchStarted }?.monotonicTimeNanos ?: return false
    val lead = ArchivedPlayerTeamConfiguration(articles).recordsAt(start)
        .map { it.payload as ArchivedPlayerTeamSlotConfigured }.lastOrNull { it.slot == 1 } ?: return false
    val firstBadge = articles.firstOrNull { article ->
        val badge = article.payload as? ArchivedActivePokemonSpeciesWitnessed
        badge?.side == "PLAYER" && isSideLocatedSpeciesSource(article.sourceId) &&
            (article.monotonicTimeNanos ?: Long.MAX_VALUE) <= start + ENTRY_BADGE_WINDOW
    } ?: return false
    val actual = (firstBadge.payload as ArchivedActivePokemonSpeciesWitnessed).speciesName
    if (key(actual) == key(lead.speciesName)) return false
    if (firstBadge.monotonicTimeNanos!! <= start) return true
    // A prompt switch after GO is possible. Only replace the configured lead
    // when its contradictory replacement was announced BEFORE the countdown ended.
    return articles.any { article ->
        val text = (article.payload as? ArchivedRawText)?.value ?: return@any false
        val name = ENTRY.matchEntire(text.trim())?.groupValues?.get(1) ?: return@any false
        article.sourceId == "ANNOUNCEMENT_WITNESS" && key(name) == key(actual) &&
            article.monotonicTimeNanos?.let { it <= start && firstBadge.monotonicTimeNanos!! - it <= ENTRY_BADGE_WINDOW } == true
    }
}

private fun key(value: String) = TeamRosterSpeciesAttributor.speciesKey(value)
private const val ENTRY_BADGE_WINDOW = 8_000_000_000L
private val ENTRY = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
