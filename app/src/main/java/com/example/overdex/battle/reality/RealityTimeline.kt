package com.example.overdex.battle.reality

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Reality Timeline: Preserves immutable records concerning Articles.
 * 
 * It is responsible for the objective history of what was perceived 
 * and what was reasoned during a Match.
 */
interface RealityTimeline {
    /**
     * Appends a new, immutable record to the chronological ledger.
     */
    fun append(article: RealityArticle)

    /**
     * Retrieves all preserved records in the order they were appended.
     */
    fun getArticles(): List<RealityArticle>
}

/**
 * A thread-safe, in-memory implementation of [RealityTimeline].
 * 
 * Completely isolated from historical interpretive timelines.
 */
class InMemoryRealityTimeline : RealityTimeline {
    private val articles = CopyOnWriteArrayList<RealityArticle>()

    override fun append(article: RealityArticle) {
        articles.add(article)
    }

    override fun getArticles(): List<RealityArticle> = articles.toList()
}
