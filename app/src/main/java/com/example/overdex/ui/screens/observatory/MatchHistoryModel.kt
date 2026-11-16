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

/** Small process-local projections; archive identity and metadata invalidate old summaries. */
internal object MatchHistorySummaryCache {
    private data class Cached(val bytes: Long, val modified: Long, val item: MatchHistoryItem)
    private val entries = object : LinkedHashMap<String, Cached>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 128
    }
    @Synchronized fun get(uri: String, bytes: Long, modified: Long): MatchHistoryItem? =
        entries[uri]?.takeIf { it.bytes == bytes && it.modified == modified }?.item
    @Synchronized fun put(uri: String, bytes: Long, modified: Long, item: MatchHistoryItem) {
        if (item.summary != null && item.readError == null) entries[uri] = Cached(bytes, modified, item)
    }
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
