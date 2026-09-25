package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable

/**
 * A durable, evidence-preserving reference to one interval in an exported Match.
 * The excerpt deliberately copies no crops, audio, or Timeline articles.
 */
@Serializable
data class ReplayExcerpt(
    val schemaVersion: Int = 1,
    val excerptId: String,
    val archiveMatchId: String,
    val archiveSha256: String,
    val startMonotonicTimeNanos: Long,
    val endMonotonicTimeNanos: Long,
    val startArticleId: String? = null,
    val endArticleId: String? = null,
    val title: String? = null
) {
    init {
        require(startMonotonicTimeNanos <= endMonotonicTimeNanos) {
            "Replay excerpt end must not precede its start."
        }
    }
}
