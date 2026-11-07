package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource

/** A prompt independent badge may reject a stale saved team, never a supported opening switch. */
internal fun earlyUnconfiguredPlayerBadgeContradictsLead(
    articles: List<ArchivedRealityArticle>,
    start: Long,
    lead: ArchivedPlayerTeamSlotConfigured,
    firstBadge: ArchivedRealityArticle,
): Boolean {
    val at = firstBadge.monotonicTimeNanos ?: return false
    if (at - start !in 1..EARLY_PLAYER_BADGE_WINDOW_NANOS ||
        firstBadge.confidence?.let { it >= MIN_BADGE_CONFIDENCE } != true) return false
    val badge = firstBadge.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return false
    if (badge.side != "PLAYER" || !isSideLocatedSpeciesSource(firstBadge.sourceId)) return false
    val actual = badge.speciesName
    val configured = ArchivedPlayerTeamConfiguration(articles).recordsAt(start)
        .map { it.payload as ArchivedPlayerTeamSlotConfigured }
    // A different member of the recorded team can genuinely have switched in
    // immediately. Only a contradiction of the whole saved roster qualifies.
    if (configured.any { key(it.speciesName) == key(actual) }) return false
    val leadKey = key(lead.speciesName)
    val actualKey = key(actual)
    val hasSupportedOpeningOrTransition = articles.any { article ->
        val sourceAt = article.monotonicTimeNanos ?: return@any false
        if (sourceAt > at) return@any false
        when (val payload = article.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> payload.side == "PLAYER" &&
                (key(payload.speciesName) == leadKey ||
                    (sourceAt > start && article.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" &&
                        key(payload.speciesName) == actualKey))
            is ArchivedPlayerTeamRosterSlotWitnessed -> sourceAt <= start && payload.slot == 1 &&
                key(payload.speciesName) == leadKey
            is ArchivedPlayerTeamRosterConfirmation -> sourceAt <= start && payload.confirmed &&
                payload.speciesBySlot.firstOrNull()?.let(::key) == leadKey
            is ArchivedActivePokemonFaintedWitnessed -> sourceAt > start && payload.side == "PLAYER"
            is ArchivedPlayerPokeBallCountMeasured -> sourceAt > start && payload.maximumCount == 3 &&
                payload.visibleCount in 0..2
            is ArchivedRawText -> sourceAt > start && article.sourceId == "ANNOUNCEMENT_WITNESS" &&
                ENTRY.matchEntire(payload.value.trim())?.groupValues?.get(1)?.let(::key) == actualKey
            ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser -> sourceAt > start
            else -> false
        }
    }
    return !hasSupportedOpeningOrTransition
}

private fun key(name: String) = TeamRosterSpeciesAttributor.speciesKey(name)
private const val EARLY_PLAYER_BADGE_WINDOW_NANOS = 2_000_000_000L
private const val MIN_BADGE_CONFIDENCE = .90f
private val ENTRY = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
