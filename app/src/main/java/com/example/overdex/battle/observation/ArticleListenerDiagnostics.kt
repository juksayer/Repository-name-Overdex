package com.example.overdex.battle.observation

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Opt-in counters only. Does not buffer, launch, filter, or publish articles. */
internal class ArticleListenerDiagnostics(
    private val clock: () -> Long = System::nanoTime,
    private val sampleEvery: Int = 64,
) {
    init { require(sampleEvery > 0) }
    data class Reading(val subscriptions: Int, val active: Int, val deliveries: Long,
        val samples: Long, val callbackElapsedNanos: Long, val maximumCallbackNanos: Long)
    private class Counter {
        val subscriptions = AtomicInteger(); val active = AtomicInteger()
        val deliveries = AtomicLong(); val samples = AtomicLong()
        val elapsed = AtomicLong(); val maximum = AtomicLong()
    }
    private val counters = linkedMapOf<String, Counter>()
    private val active = AtomicInteger()
    private val peak = AtomicInteger()
    val activeSubscriptions get() = active.get()
    val peakSubscriptions get() = peak.get()
    @Synchronized private fun counter(name: String): Counter {
        val boundedName = name.take(240)
        val key = if (boundedName in counters || counters.size < MAX_NAMES) boundedName else "overflow"
        return counters.getOrPut(key) { Counter() }
    }
    @Synchronized fun snapshot(): Map<String, Reading> = counters.mapValues { (_, c) ->
        Reading(c.subscriptions.get(), c.active.get(), c.deliveries.get(), c.samples.get(),
            c.elapsed.get(), c.maximum.get())
    }

    @OptIn(InternalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
    fun <T> wrap(upstream: SharedFlow<T>, label: String? = null): SharedFlow<T> = object : SharedFlow<T> {
        override val replayCache: List<T> get() = upstream.replayCache
        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            // Compiler-generated collector names retain the owner and inline-filter call site.
            // No stack walking, article references, or per-delivery strings are retained.
            val c = counter(label ?: collector.javaClass.name)
            c.subscriptions.incrementAndGet(); c.active.incrementAndGet()
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            var delivered = 0L
            try {
                upstream.collect { value ->
                    c.deliveries.incrementAndGet()
                    val sampled = delivered++ % sampleEvery == 0L
                    if (!sampled) collector.emit(value) else {
                        val before = clock()
                        try { collector.emit(value) } finally {
                            val elapsed = (clock() - before).coerceAtLeast(0)
                            c.samples.incrementAndGet(); c.elapsed.addAndGet(elapsed)
                            c.maximum.accumulateAndGet(elapsed, ::maxOf)
                        }
                    }
                }
            } finally {
                c.active.decrementAndGet(); active.decrementAndGet()
            }
        }
    }
    companion object { const val MAX_NAMES = 128 }
}
