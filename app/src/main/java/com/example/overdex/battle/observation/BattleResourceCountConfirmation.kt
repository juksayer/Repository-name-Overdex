package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ObservationSessionStoppedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
import com.example.overdex.battle.reality.RealityArticle

/** Two independently captured, source-ordered readings are required to establish a count. */
internal class BattleResourceCountConfirmation {
    data class Confirmed(
        val count: Int,
        val observed: RealityArticle,
        val evidence: List<RealityArticle>,
    )
    private data class Candidate(val count: Int, val article: RealityArticle, val at: Long)
    private var candidate: Candidate? = null
    private var delivered: Int? = null
    private var lastSourceAt = Long.MIN_VALUE
    private var lifecycleAt = Long.MIN_VALUE
    private var closed = false
    private var matchEndedAt: Long? = null

    fun observeLifecycle(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        if (at <= lifecycleAt) return
        when (article.payload) {
            ObservationRestartedByUser -> {
                lifecycleAt = at
                closed = false
                matchEndedAt = null
                candidate = null
                delivered = null
                lastSourceAt = at
            }
            is MatchEnded -> {
                lifecycleAt = at
                // Recognition may complete after the result. Preserve pairs
                // actually captured before the end; do not accept new frames.
                matchEndedAt = at
            }
            ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                lifecycleAt = at
                closed = true
                candidate = null
            }
            else -> Unit
        }
    }

    fun observe(article: RealityArticle, count: Int?): Confirmed? {
        val at = article.monotonicTimeNanos ?: return null
        // Re-reading one image or delivering an older queued crop is not an
        // independent confirmation and must not replace the current candidate.
        if (closed || at <= lastSourceAt || matchEndedAt?.let { at > it } == true) return null
        lastSourceAt = at
        val previous = candidate
        candidate = count?.let { Candidate(it, article, at) }
        if (count == null || previous == null || previous.count != count ||
            at - previous.at > MAXIMUM_PAIR_GAP_NANOS) return null
        val observed = if (delivered == count) article else previous.article
        delivered = count
        return Confirmed(count, observed, listOf(previous.article, article))
    }

    private companion object {
        // An initial assignment plus its one confirmation tail. Frames from
        // distant battle phases cannot corroborate one another.
        const val MAXIMUM_PAIR_GAP_NANOS = 6_000_000_000L
    }
}
