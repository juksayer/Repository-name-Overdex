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
        return (recovered + identitiesAtNamedCharge(ordered, team)).distinctBy { it.atNanos to it.speciesId }
    }

    /** A native player badge can carry the same nickname printed on a completed
     * move. Keep its punctuation: two unrelated OCR fragments must not turn
     * into the same name just because their symbols were stripped. */
    private fun identitiesAtNamedCharge(
        ordered: List<ArchivedRealityArticle>,
        team: List<ArchivedPlayerTeamSlotConfigured>,
    ): List<ReplayIdentityObservation> {
        if (team.size != 3 || team.map { it.slot }.toSet() != setOf(1, 2, 3) ||
            team.any { it.chargedMoveNames.size != 2 || it.chargedMoveNames.any(String::isBlank) }) return emptyList()
        val go = ordered.firstOrNull { it.payload is ArchivedMatchStarted }?.monotonicTimeNanos ?: return emptyList()
        val byId = ordered.associateBy { it.articleId }
        val badges = ordered.filter { it.sourceId == "PLAYER_SPECIES_OVERLAY_PIPELINE_OCR_RAW" && it.payload is ArchivedRawText }
        val channel = ordered.filter { it.sourceId == "ANNOUNCEMENT_WITNESS" || it.sourceId == "QTE_PROGRESS_TEXT_WITNESS" }
        return channel.mapNotNull { banner ->
            if (banner.sourceId != "ANNOUNCEMENT_WITNESS") return@mapNotNull null
            val text = (banner.payload as? ArchivedRawText)?.value ?: return@mapNotNull null
            val match = USED.matchEntire(text.trim()) ?: return@mapNotNull null
            val nickname = badgeText(match.groupValues[1])
            val move = match.groupValues[2].trim()
            val at = requireNotNull(banner.monotonicTimeNanos)
            if (at <= go || nickname.isEmpty()) return@mapNotNull null
            val member = team.singleOrNull { slot -> slot.chargedMoveNames.any { it.equals(move, true) } } ?: return@mapNotNull null
            val cue = channel.lastOrNull { row ->
                val time = requireNotNull(row.monotonicTimeNanos)
                val raw = (row.payload as? ArchivedRawText)?.value?.trim().orEmpty()
                time in maxOf(go, at - 10_000_000_000L) until at &&
                    (row.sourceId == "QTE_PROGRESS_TEXT_WITNESS" && raw.uppercase() in PLAYER_GRADES ||
                        row.sourceId == "ANNOUNCEMENT_WITNESS" && raw.isNotEmpty() && !raw.equals("Get Ready!", true))
            } ?: return@mapNotNull null
            if (cue.sourceId != "QTE_PROGRESS_TEXT_WITNESS") return@mapNotNull null
            val badge = badges.filter { row ->
                kotlin.math.abs(requireNotNull(row.monotonicTimeNanos) - at) <= ENTRY_WINDOW &&
                    badgeText((row.payload as ArchivedRawText).value) == nickname
            }.minByOrNull { kotlin.math.abs(requireNotNull(it.monotonicTimeNanos) - at) } ?: return@mapNotNull null
            val crop = badge.evidenceReferences.orEmpty().mapNotNull(byId::get).singleOrNull {
                val source = it.payload as? ArchivedCropCaptured
                source?.cropName == "PlayerActiveSpeciesTextCrop" && source.nativeTeamBadgePresent == true
            } ?: return@mapNotNull null
            val captured = crop.monotonicTimeNanos ?: return@mapNotNull null
            if (captured <= go || kotlin.math.abs(captured - at) > ENTRY_WINDOW) return@mapNotNull null
            val interval = requireNotNull(cue.monotonicTimeNanos)..maxOf(at, captured)
            if (ordered.any { row ->
                    requireNotNull(row.monotonicTimeNanos) in interval && when (val evidence = row.payload) {
                        is ArchivedActivePokemonSpeciesWitnessed -> evidence.side == "PLAYER" &&
                            !evidence.speciesName.equals(member.speciesName, true)
                        is ArchivedActivePokemonFaintedWitnessed -> evidence.side == "PLAYER"
                        is ArchivedSpeciesCheckMeasured -> evidence.side == "PLAYER" && evidence.status == "OPENED" &&
                            evidence.reason in setOf("ENTRY", "SWITCH", "DEPARTURE", "BALL_DECREASE", "FAINT") &&
                            evidence.triggerMonotonicNanos in interval
                        else -> false
                    }
                }) return@mapNotNull null
            ReplayIdentityObservation("PLAYER", member.speciesName, member.speciesId, captured,
                "RECONSTRUCTED FROM NATIVE PLAYER CROP AND NAMED CHARGE",
                articleId = badge.articleId, evidenceArticleIds = listOf(crop.articleId, badge.articleId, cue.articleId, banner.articleId))
        }
    }

    private fun badgeText(value: String) = value.filterNot(Char::isWhitespace).uppercase()
    private val USED = Regex("""^(.+?)\s+used\s+([\p{L}][\p{L} '.-]*?)[!.]*$""", RegexOption.IGNORE_CASE)
    private val PLAYER_GRADES = setOf("NICE!", "GREAT!", "EXCELLENT!")

    private fun entryName(article: ArchivedRealityArticle) = (article.payload as? ArchivedRawText)
        ?.value?.trim()?.let { ENTRY.matchEntire(it)?.groupValues?.get(1) }
    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
    private fun speciesKey(value: String) = TeamRosterSpeciesAttributor.speciesKey(value)
    private val ENTRY = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
    private const val LABEL_PAIR_WINDOW = 6_000_000_000L
    private const val ENTRY_WINDOW = 1_500_000_000L
}
