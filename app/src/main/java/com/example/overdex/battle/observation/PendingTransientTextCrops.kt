package com.example.overdex.battle.observation

import com.example.overdex.battle.reality.RealityArticle

/** Bounded visual scheduling only. Selected pixels always keep their own source clock. */
internal class PendingTransientTextCrops(
    private val capacity: Int = 8,
    private val maximumAgeNanos: Long = 10_000_000_000L,
) {
    init { require(capacity > 0 && maximumAgeNanos > 0) }

    private data class Frame(val article: RealityArticle, val episode: Long? = null, val score: Float = 0f)
    private data class Episode(val id: Long, val signature: Long, val lastSeenAt: Long)
    data class Selection(
        val article: RealityArticle,
        val kind: String,
        val pendingCount: Int,
        val replacedTotal: Long,
        val coalescedTotal: Long,
        val overflowTotal: Long,
        val expiredTotal: Long,
        internal val episode: Long?,
    )

    private val candidates = mutableListOf<Frame>()
    private var latest: Frame? = null
    private var inFlight: Frame? = null
    private var retry: Frame? = null
    private var episode: Episode? = null
    private var nextEpisode = 0L
    private var lastOfferedAt = Long.MIN_VALUE
    private var replacedTotal = 0L
    private var coalescedTotal = 0L
    private var overflowTotal = 0L
    private var expiredTotal = 0L

    @Synchronized
    fun offer(article: RealityArticle, visual: TransientTextProbe.Reading?) {
        val at = article.monotonicTimeNanos ?: return
        if (at <= lastOfferedAt) return
        lastOfferedAt = at
        if (latest != null) replacedTotal++
        latest = Frame(article)
        if (visual?.hasText != true || visual.signature == 0L) {
            // Blank/weak or different visual content breaks a continuous episode.
            // It remains eligible for ordinary OCR through the latest fallback.
            episode = null
            return
        }
        val previous = episode?.takeIf {
            it.signature == visual.signature && at - it.lastSeenAt <= EPISODE_GAP_NANOS
        }
        if (previous != null) {
            coalescedTotal++
            episode = previous.copy(lastSeenAt = at)
            val index = candidates.indexOfFirst { it.episode == previous.id }
            if (index >= 0) {
                val retained = candidates[index]
                // Keep the first readable frame unless a substantially stronger
                // frame arrives. A replacement retains ITS source time, never
                // the earlier episode's time.
                if (visual.score > retained.score + .05f) {
                    candidates[index] = Frame(article, previous.id, visual.score)
                }
                latest = Frame(article, previous.id, visual.score)
            } else if (inFlight?.episode == previous.id) {
                // One newer frame may rescue an unsuccessful in-flight read,
                // even if the short message disappears before OCR completes.
                // Do not queue another native request until its result is known.
                retry = Frame(article, previous.id, visual.score)
                latest = null
            } else {
                // This continuous visual episode already had its OCR attempt.
                // Do not repeatedly spend turns on the same unchanged message.
                latest = null
            }
            return
        }
        val id = ++nextEpisode
        episode = Episode(id, visual.signature, at)
        val frame = Frame(article, id, visual.score)
        candidates += frame
        latest = frame
        if (candidates.size > capacity) {
            candidates.removeAt(0)
            overflowTotal++
        }
    }

    @Synchronized
    fun hasPending(nowNanos: Long): Boolean {
        expire(nowNanos)
        return candidates.isNotEmpty() || latest != null
    }

    @Synchronized
    fun select(nowNanos: Long): Selection? {
        expire(nowNanos)
        val frame: Frame
        val kind: String
        if (candidates.isNotEmpty()) {
            frame = candidates.removeAt(0)
            kind = "TEXT_KEYFRAME"
            if (latest?.episode == frame.episode) latest = null
        } else {
            frame = latest ?: return null
            latest = null
            kind = "LATEST_FALLBACK"
        }
        inFlight = frame
        retry = null
        return Selection(frame.article, kind, count(), replacedTotal, coalescedTotal,
            overflowTotal, expiredTotal, frame.episode)
    }

    @Synchronized
    fun succeeded(selection: Selection) {
        if (inFlight?.article?.id != selection.article.id) return
        inFlight = null
        retry = null
    }

    /** Retry at most one separately captured frame retained during this attempt. */
    @Synchronized
    fun failed(selection: Selection) {
        if (inFlight?.article?.id != selection.article.id) return
        val retained = retry
        inFlight = null
        retry = null
        if (retained != null) {
            candidates.add(0, retained)
            if (candidates.size > capacity) {
                val removed = candidates.removeAt(candidates.lastIndex)
                // Eviction is not a completed OCR attempt. A later capture of
                // that same still-visible message must remain eligible.
                if (episode?.id == removed.episode) episode = null
                overflowTotal++
            }
        } else if (selection.episode != null && episode?.id == selection.episode) episode = null
    }

    @Synchronized
    fun clear() { candidates.clear(); latest = null; episode = null; inFlight = null; retry = null }

    private fun count() = candidates.size + if (latest != null &&
        candidates.none { it.article.id == latest?.article?.id ||
            (it.episode != null && it.episode == latest?.episode) }) 1 else 0

    private fun expire(nowNanos: Long) {
        val expired = candidates.filter { nowNanos - it.article.monotonicTimeNanos!! > maximumAgeNanos }
        if (expired.isNotEmpty()) {
            candidates.removeAll(expired.toSet())
            expiredTotal += expired.size
            if (expired.any { it.episode == episode?.id }) episode = null
        }
        if (latest?.article?.monotonicTimeNanos?.let { nowNanos - it > maximumAgeNanos } == true) {
            latest = null
        }
    }

    private companion object { const val EPISODE_GAP_NANOS = 1_500_000_000L }
}
