package com.example.overdex.ui.screens.observatory

import com.example.overdex.battle.archive.MatchSummary

/** Metadata only: history never holds image/audio artifacts or a second match ledger. */
internal data class MatchHistoryItem(
    val sourceUri: String,
    val archiveName: String,
    val summary: MatchSummary?,
    val readError: String? = null,
) {
    val resultLabel: String
        get() = if (readError != null) "UNREADABLE RECORDING"
            else summary?.result ?: "INCOMPLETE · RESULT NOT RECORDED"
}

/** Completed/recovered or compact/full packages can represent the same Match. */
internal fun List<MatchHistoryItem>.distinctRecordedMatches(): List<MatchHistoryItem> {
    val readable = filter { it.summary != null && it.readError == null }
        .groupBy { it.summary!!.matchId }
        .values.map { copies ->
            copies.minWith(compareByDescending<MatchHistoryItem> { it.summary!!.articleCount }
                .thenBy { it.sourceUri })
        }
    return readable + filter { it.summary == null || it.readError != null }
}

/** Export, recovery and file-copy times must never change when a match was played. */
internal fun List<MatchHistoryItem>.newestMatchFirst(): List<MatchHistoryItem> =
    sortedWith(compareByDescending<MatchHistoryItem> { it.summary?.recordedAt }
        .thenBy { it.summary?.matchId ?: it.sourceUri }
        .thenBy { it.sourceUri })

internal fun selectedMatchHistoryUri(
    items: List<MatchHistoryItem>,
    previousUri: String?,
    previousMatchId: String? = null,
): String? =
    previousUri?.takeIf { previous -> items.any { it.sourceUri == previous } }
        ?: previousMatchId?.let { id -> items.firstOrNull { it.summary?.matchId == id }?.sourceUri }
        ?: items.firstOrNull()?.sourceUri

/** Active checkpoints become browseable only after completion or interrupted-match recovery. */
internal fun isSavedMatchHistoryArchive(name: String): Boolean = !name.startsWith("active-", ignoreCase = true)
