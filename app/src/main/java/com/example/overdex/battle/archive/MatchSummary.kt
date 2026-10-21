package com.example.overdex.battle.archive

/** A small read-only projection of the same ledger used by archive inspection and replay. */
data class MatchSummary(
    val matchId: String,
    val recordedAt: Long?,
    val recordingDurationMillis: Long,
    val battleDurationMillis: Long?,
    val result: String?,
    val articleCount: Int,
    val player: MatchSideSummary,
    val opponent: MatchSideSummary,
    val resultMusic: String?,
    val playerInformation: List<ArchivedPlayerBattleInformationChecked> = emptyList(),
)

data class MatchSideSummary(
    val species: List<String>,
    val fastMoveUses: Int,
    val chargedMoveUses: Int,
    val faintCount: Int,
    val identifiedFastMoves: List<String>,
    val chargedMoves: List<String>,
)

fun MatchArchive.summarize(): MatchSummary {
    val records = articles.filter { it.matchId == matchId }.distinctBy { it.articleId }
    val start = records.firstOrNull { it.payload is ArchivedMatchStarted }
    val end = records.firstOrNull { it.payload is ArchivedMatchEnded }
    fun elapsed(from: ArchivedRealityArticle, to: ArchivedRealityArticle): Long =
        if (from.monotonicTimeNanos != null && to.monotonicTimeNanos != null) {
            (to.monotonicTimeNanos - from.monotonicTimeNanos) / 1_000_000L
        } else to.perceivedAt - from.perceivedAt
    val first = records.minByOrNull { it.perceivedAt }
    // Publication order can differ from capture order; late OCR must not extend the battle.
    val last = first?.let { origin -> records.maxByOrNull { elapsed(origin, it) } }
    val battle = records.filter { (start == null || elapsed(start, it) >= 0) && (end == null || elapsed(it, end) >= 0) }
    fun side(side: String): MatchSideSummary {
        val identities = records.mapNotNull { it.payload as? ArchivedActivePokemonSpeciesWitnessed }
            .filter { it.side == side }.distinctBy { it.speciesId?.toString() ?: it.speciesName.lowercase() }
        val uses = battle.mapNotNull { it.payload as? ArchivedFastMoveUseObserved }
            .filter { it.attackingSide == side }.distinctBy { it.useId }
        val charges = battle.filter { (it.payload as? ArchivedChargedMoveEnergySpent)?.side == side }
            .sortedBy { it.monotonicTimeNanos ?: it.perceivedAt * 1_000_000L }
            .fold(mutableListOf<ArchivedRealityArticle>()) { accepted, article ->
                val previous = accepted.lastOrNull()
                val payload = article.payload as ArchivedChargedMoveEnergySpent
                val old = previous?.payload as? ArchivedChargedMoveEnergySpent
                if (old == null || old.speciesName != payload.speciesName || old.moveName != payload.moveName ||
                    elapsed(previous, article) > 3_000L) accepted += article
                accepted
            }
        return MatchSideSummary(
            species = identities.map { it.speciesName },
            fastMoveUses = uses.size,
            chargedMoveUses = charges.size,
            faintCount = battle.mapNotNull { it.payload as? ArchivedActivePokemonFaintedWitnessed }
                .filter { it.side == side }.distinctBy { it.speciesId?.toString() ?: it.speciesName }.size,
            identifiedFastMoves = records.mapNotNull { it.payload as? ArchivedFastMoveIdentified }
                .filter { it.side == side }.map { "${it.speciesName}: ${it.moveName}" }.distinct(),
            chargedMoves = charges.map { it.payload as ArchivedChargedMoveEnergySpent }
                .map { "${it.speciesName}: ${it.moveName}" }.distinct(),
        )
    }
    val musicComparison = records.mapNotNull { it.payload as? ArchivedMatchResultMusicCompared }.lastOrNull()
    val music = records.mapNotNull { it.payload as? ArchivedMatchResultMusicMeasured }.lastOrNull()
    return MatchSummary(matchId, first?.perceivedAt,
        if (first != null && last != null) elapsed(first, last).coerceAtLeast(0) else 0,
        start?.let { last?.let { tail -> elapsed(it, end ?: tail).coerceAtLeast(0) } },
        (end?.payload as? ArchivedMatchEnded)?.result, records.size, side("PLAYER"), side("OPPONENT"),
        when {
            musicComparison != null && !musicComparison.agrees -> "Music and recorded result disagree"
            musicComparison != null -> "${musicComparison.musicTheme.lowercase().replaceFirstChar(Char::uppercase)} music agrees with result"
            music != null -> "${music.theme.lowercase().replaceFirstChar(Char::uppercase)} music heard; result unconfirmed"
            else -> null
        }, records.mapNotNull { it.payload as? ArchivedPlayerBattleInformationChecked }
            .associateBy { it.speciesName to it.field }.values.toList())
}
