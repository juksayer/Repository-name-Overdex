package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor

/**
 * Player badges and entry announcements can contain a nickname, not a species.
 * A completed recording can resolve that nickname from BOTH player charge labels
 * when exactly one configured team member has that pair. A move label is identity
 * support here, never proof that the move was used.
 */
internal object ReplayPlayerIdentityRecovery {
    fun recover(
        articles: List<ArchivedRealityArticle>,
        team: List<ArchivedPlayerTeamSlotConfigured>,
    ): List<ReplayIdentityObservation> {
        if (team.isEmpty()) return emptyList()
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        val labels = mutableMapOf<Int, ArchivedRealityArticle>()
        val recovered = mutableListOf<ReplayIdentityObservation>()
        val readings = ordered.filter {
            it.payload is ArchivedRawText && it.sourceId.startsWith("PLAYER_SPECIES_OVERLAY_PIPELINE_OCR")
        }
        for (article in ordered) {
            val at = article.monotonicTimeNanos ?: continue
            if ((article.payload as? ArchivedActivePokemonFaintedWitnessed)?.side == "PLAYER") {
                labels.clear()
            }
            val label = article.payload as? ArchivedPlayerChargeMoveLabelRead ?: continue
            if (label.slot !in 1..2) continue
            labels[label.slot] = article
            if (labels.size != 2) continue
            val firstAt = labels.values.minOf { it.monotonicTimeNanos!! }
            if (at - firstAt > LABEL_PAIR_WINDOW) continue
            val names = labels.values.map { key((it.payload as ArchivedPlayerChargeMoveLabelRead).text) }.toSet()
            if (names.size != 2 || names.any { it.isBlank() }) continue
            val member = team.filter { candidate ->
                candidate.chargedMoveNames.map(::key).toSet() == names
            }.singleOrNull() ?: continue

            // The side-specific badge anchors this appearance. Do not backdate
            // unrelated button text across a switch, faint or conflicting badge.
            val badge = readings.lastOrNull {
                it.monotonicTimeNanos!! in (firstAt - LABEL_PAIR_WINDOW)..firstAt
            } ?: continue
            val nickname = key((badge.payload as ArchivedRawText).value)
            if (nickname.isBlank()) continue
            val badgeAt = badge.monotonicTimeNanos!!
            if (ordered.any { evidence ->
                    val time = evidence.monotonicTimeNanos ?: return@any false
                    if (time !in badgeAt..at) return@any false
                    when (val payload = evidence.payload) {
                        is ArchivedActivePokemonFaintedWitnessed -> payload.side == "PLAYER"
                        is ArchivedActivePokemonSpeciesWitnessed -> payload.side == "PLAYER" &&
                            speciesKey(payload.speciesName) != speciesKey(member.speciesName)
                        is ArchivedRawText -> evidence.sourceId.startsWith("PLAYER_SPECIES_OVERLAY_PIPELINE_OCR") &&
                            key(payload.value).let { it.isNotBlank() && it != nickname }
                        else -> false
                    }
                }) continue
            val entry = ordered.firstOrNull {
                it.sourceId == "ANNOUNCEMENT_WITNESS" &&
                    it.monotonicTimeNanos!! in (badgeAt - ENTRY_WINDOW)..minOf(at, badgeAt + ENTRY_WINDOW) &&
                    entryName(it)?.let(::key) == nickname
            }
            val start = entry?.monotonicTimeNanos ?: badgeAt
            if (recovered.any { it.atNanos == start && it.speciesId == member.speciesId }) continue
            recovered += ReplayIdentityObservation(
                side = "PLAYER", speciesName = member.speciesName, speciesId = member.speciesId,
                atNanos = start,
                basis = "RECONSTRUCTED FROM PLAYER BADGE AND BOTH CHARGE LABELS",
                articleId = article.articleId,
                evidenceArticleIds = (listOf(badge.articleId) + listOfNotNull(entry?.articleId) +
                    labels.values.map { it.articleId }).distinct(),
            )
        }
        return recovered
    }

    private fun entryName(article: ArchivedRealityArticle) = (article.payload as? ArchivedRawText)
        ?.value?.trim()?.let { ENTRY.matchEntire(it)?.groupValues?.get(1) }
    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
    private fun speciesKey(value: String) = TeamRosterSpeciesAttributor.speciesKey(value)
    private val ENTRY = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
    private const val LABEL_PAIR_WINDOW = 6_000_000_000L
    private const val ENTRY_WINDOW = 1_500_000_000L
}
