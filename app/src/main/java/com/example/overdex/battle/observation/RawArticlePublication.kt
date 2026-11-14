package com.example.overdex.battle.observation

import com.example.overdex.battle.reality.RealityArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Direct receipt lookup; completed Articles remain owned by the Timeline, not this registry.
 * Match publishes raw testimony in custody sequence order. The watermark avoids
 * exposing an appended article before its broadcast boundary. Availability has
 * a separate publication lane and must not use this testimony receipt API.
 */
internal class RawArticlePublication(private val lookup: (Long) -> RealityArticle?) {
    private val lock = Any()
    private val slots = Semaphore(256)
    private val pending = mutableMapOf<Long, MutableSet<CompletableDeferred<RealityArticle>>>()
    private var publishedThrough = Long.MIN_VALUE
    private var closed = false
    internal val pendingCount get() = synchronized(lock) { pending.values.sumOf { it.size } }

    suspend fun await(sequence: Long): RealityArticle = slots.withPermit {
        val waiter = CompletableDeferred<RealityArticle>()
        synchronized(lock) {
            if (closed) throw CancellationException("Match released")
            if (sequence <= publishedThrough) {
                lookup(sequence)?.let { return@withPermit it }
            }
            pending.getOrPut(sequence) { mutableSetOf() }.add(waiter)
        }
        try { waiter.await() } finally {
            synchronized(lock) {
                pending[sequence]?.let { entries ->
                    entries.remove(waiter)
                    if (entries.isEmpty()) pending.remove(sequence)
                }
            }
        }
    }

    fun published(article: RealityArticle) {
        val sequence = requireNotNull(article.sequenceNumber)
        val waiters = synchronized(lock) {
            if (closed) return
            publishedThrough = maxOf(publishedThrough, sequence)
            pending.remove(sequence)?.toList().orEmpty()
        }
        // Resume outside the registry lock. Publication does not await consumers.
        waiters.forEach { it.complete(article) }
    }

    fun close() {
        val waiters = synchronized(lock) {
            closed = true
            pending.values.flatMap { it.toList() }.also { pending.clear() }
        }
        waiters.forEach { it.cancel(CancellationException("Match released")) }
    }
}
