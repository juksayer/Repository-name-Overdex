package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable

/**
 * A durable, evidence-preserving reference to one interval in an exported Match.
 * Original event times stay in the source clock. New clips retain a separate,
 * compressed Timeline snapshot; no crops or audio are duplicated.
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
    val title: String? = null,
    val createdAtMillis: Long = 0,
    val notes: String = "",
    /** Presentation recovery stays explicitly separate from original articles. */
    val recoveredIdentities: List<ReplayIdentityObservation> = emptyList(),
) {
    init {
        require(startMonotonicTimeNanos <= endMonotonicTimeNanos) {
            "Replay excerpt end must not precede its start."
        }
    }
}
