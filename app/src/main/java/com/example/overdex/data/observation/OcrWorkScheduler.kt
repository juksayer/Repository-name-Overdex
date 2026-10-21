package com.example.overdex.data.observation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class OcrPriority { URGENT, TRANSIENT, NORMAL }

/**
 * Admit one job to ML Kit at a time. Its Latin clients share a backend queue,
 * so priority must be applied here, before process() is called. Urgent identity
 * and result reads pass waiting routine reads, but never interrupt an active
 * bitmap read. Brief announcements go before routine button labels. Bounded
 * bursts give every lane a turn even during sustained species confirmations.
 */
internal class OcrWorkScheduler {
    private class Request(val priority: OcrPriority) {
        val ready = CompletableDeferred<Unit>()
    }

    private val lock = Any()
    private val waiting = mutableListOf<Request>()
    private var active: Request? = null
    private var urgentRun = 0
    private var transientRun = 0

    internal fun waitingCount(priority: OcrPriority): Int = synchronized(lock) {
        waiting.count { it.priority == priority }
    }

    suspend fun <T> run(priority: OcrPriority, block: suspend () -> T): T =
        execute(enqueue(priority, onlyIfIdle = false)!!, block)

    /** Recheck at admission, not just at enqueue time; never send obsolete routine pixels to ML Kit. */
    suspend fun <T : Any> runFresh(priority: OcrPriority, isFresh: () -> Boolean, block: suspend () -> T): T? =
        run(priority) { if (isFresh()) block() else null }

    suspend fun <T : Any> runIfIdle(block: suspend () -> T): T? {
        val request = enqueue(OcrPriority.NORMAL, onlyIfIdle = true) ?: return null
        return execute(request, block)
    }

    private fun enqueue(priority: OcrPriority, onlyIfIdle: Boolean): Request? = synchronized(lock) {
        if (onlyIfIdle && active != null) return@synchronized null
        Request(priority).also { request ->
            if (active == null) {
                active = request
                urgentRun = if (priority == OcrPriority.URGENT) 1 else 0
                transientRun = if (priority == OcrPriority.TRANSIENT) 1 else 0
                request.ready.complete(Unit)
            } else {
                waiting += request
            }
        }
    }

    private suspend fun <T> execute(request: Request, block: suspend () -> T): T = try {
        request.ready.await()
        currentCoroutineContext().ensureActive()
        block()
    } finally {
        val next = synchronized(lock) {
            if (active !== request) {
                waiting.remove(request)
                null
            } else {
                val hasNormal = waiting.any { it.priority == OcrPriority.NORMAL }
                val hasTransient = waiting.any { it.priority == OcrPriority.TRANSIENT }
                val hasUrgent = waiting.any { it.priority == OcrPriority.URGENT }
                val preferred = when {
                    hasUrgent && (urgentRun < 2 || (!hasNormal && !hasTransient)) -> OcrPriority.URGENT
                    hasTransient && (transientRun < 2 || !hasNormal) -> OcrPriority.TRANSIENT
                    hasNormal -> OcrPriority.NORMAL
                    else -> OcrPriority.URGENT
                }
                val index = waiting.indexOfFirst { it.priority == preferred }.takeIf { it >= 0 } ?: 0
                val selected = if (waiting.isEmpty()) null else waiting.removeAt(index)
                active = selected
                urgentRun = if (selected?.priority == OcrPriority.URGENT) (urgentRun + 1).coerceAtMost(2) else 0
                transientRun = when (selected?.priority) {
                    OcrPriority.TRANSIENT -> (transientRun + 1).coerceAtMost(2)
                    OcrPriority.NORMAL, null -> 0
                    else -> transientRun
                }
                selected
            }
        }
        next?.ready?.complete(Unit)
    }
}
