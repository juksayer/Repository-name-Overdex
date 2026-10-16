package com.example.overdex.battle.reality

/**
 * The Reality Timeline: preserves immutable records concerning Articles.
 * It is responsible for the objective history of what was perceived and
 * what was reasoned during a Match.
 */
interface RealityTimeline {
    /** Appends a new, immutable record to the chronological ledger. */
    fun append(article: RealityArticle)

    /** Retrieves all preserved records in the order they were appended. */
    fun getArticles(): List<RealityArticle>

    /** Only the newly appended tail is needed by an incremental checkpoint. */
    fun getArticlesFrom(index: Int): List<RealityArticle> = getArticles().drop(index)
}

/**
 * Thread-safe in-memory ledger for a match.
 *
 * Capture can append hundreds of independent crop records per minute. A
 * copy-on-write list copied the complete history for every append, delaying
 * downstream witnesses until a match ended. The lock preserves the same
 * ordering and snapshot semantics without making a longer match slower.
 */
class InMemoryRealityTimeline : RealityTimeline {
    private val lock = Any()
    private val articles = mutableListOf<RealityArticle>()

    override fun append(article: RealityArticle) = synchronized(lock) {
        articles += article
    }

    override fun getArticles(): List<RealityArticle> = synchronized(lock) {
        articles.toList()
    }

    override fun getArticlesFrom(index: Int): List<RealityArticle> = synchronized(lock) {
        require(index in 0..articles.size)
        articles.subList(index, articles.size).toList()
    }
}
