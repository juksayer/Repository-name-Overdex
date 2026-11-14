package com.example.overdex.battle.observation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ArticleListenerDiagnosticsTest {
    @Test fun `wrapper retains replay references order and cancellation`() = runBlocking {
        val upstream = MutableSharedFlow<Any>(replay = 8)
        val a = Any(); val b = Any()
        upstream.emit(a); upstream.emit(b)
        val diagnostics = ArticleListenerDiagnostics(sampleEvery = 1)
        val wrapped = diagnostics.wrap(upstream)
        assertSame(a, wrapped.replayCache[0])
        val received = wrapped.take(2).toList()
        assertSame(a, received[0]); assertSame(b, received[1])
        assertEquals(0, diagnostics.activeSubscriptions)
        assertEquals(1, diagnostics.peakSubscriptions)
        assertEquals(2L, diagnostics.snapshot().values.sumOf { it.deliveries })
        assertEquals(0, upstream.subscriptionCount.value)
    }

    @Test fun `post subscription filters still count irrelevant deliveries`() = runBlocking {
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val diagnostics = ArticleListenerDiagnostics(sampleEvery = 2)
        val seen = mutableListOf<Int>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            diagnostics.wrap(upstream).filter { it % 2 == 0 }.collect { seen += it }
        }
        repeat(4) { upstream.emit(it) }
        yield()
        job.cancelAndJoin()
        assertEquals(listOf(0, 2), seen)
        assertEquals(4L, diagnostics.snapshot().values.sumOf { it.deliveries })
        assertEquals(2L, diagnostics.snapshot().values.sumOf { it.samples })
        assertEquals(0, diagnostics.activeSubscriptions)
    }

    @Test fun `suspended callback counts waiting without making publisher wait`() = runBlocking {
        var now = 0L
        val d = ArticleListenerDiagnostics({ now }, sampleEvery = 1)
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            d.wrap(upstream).collect { entered.complete(Unit); release.await() }
        }
        assertTrue(upstream.tryEmit(1)); entered.await()
        assertTrue(upstream.tryEmit(2))
        now = 500_000_000
        job.cancelAndJoin()
        val reading = d.snapshot().values.single()
        assertEquals(500_000_000L, reading.callbackElapsedNanos)
        assertEquals(1L, reading.deliveries)
        assertEquals(0, d.activeSubscriptions)
    }

    @Test fun `diagnostic callback failure propagates and releases subscription`() = runBlocking {
        val d = ArticleListenerDiagnostics(sampleEvery = 1)
        val upstream = MutableSharedFlow<Int>(replay = 1).apply { tryEmit(1) }
        val failure = IllegalStateException("reader failed")
        try {
            d.wrap(upstream).collect { throw failure }
        } catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(0, d.activeSubscriptions)
        assertEquals(0, upstream.subscriptionCount.value)
    }
    @Test fun `listener name storage stays bounded including overflow`() = runBlocking {
        val d = ArticleListenerDiagnostics()
        val flow = MutableSharedFlow<Int>(replay = 1).apply { tryEmit(1) }
        repeat(300) { d.wrap(flow, "listener-$it").first() }
        assertEquals(ArticleListenerDiagnostics.MAX_NAMES + 1, d.snapshot().size)
        assertEquals(300L, d.snapshot().values.sumOf { it.deliveries })
        assertEquals(0, d.activeSubscriptions)
    }

}
