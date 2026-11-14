package com.example.overdex.battle.observation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in mechanism experiment: no Android readers, inference, or archive mutations. */
class ArticleBroadcastFanoutProfileTest {
    @Test fun profile() = runBlocking {
        val destination = System.getenv("OVERDEX_BROADCAST_PROFILE")
        assumeTrue("Opt-in diagnostic", destination != null)
        val rows = mutableListOf("listeners,mode,repetition,emitElapsedMs,emitCpuMs,delivered")
        val cpu = ManagementFactory.getThreadMXBean()
        Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { readers ->
            for (count in listOf(0, 1, 16, 64)) for (paced in listOf(false, true))
                for (instrumented in if (count == 64) listOf(false, true) else listOf(false)) {
                // One warm-up then three measured repetitions. Below buffer capacity: no loss allowed.
                repeat(4) { repetition ->
                    val flow = MutableSharedFlow<Int>(4096, 4096, BufferOverflow.DROP_OLDEST)
                    val observed = if (instrumented) ArticleListenerDiagnostics().wrap(flow) else flow
                    val received = AtomicInteger()
                    val jobs = List(count) {
                        launch(readers, start = CoroutineStart.UNDISPATCHED) {
                            var expected = 0
                            observed.collect { value ->
                                assertEquals(expected++, value)
                                // Represents an irrelevant-article reader: inspect and immediately return.
                                received.incrementAndGet()
                            }
                        }
                    }
                    assertEquals(count, flow.subscriptionCount.value)
                    var elapsed = 0L; var processor = 0L
                    repeat(1000) { value ->
                        val before = System.nanoTime()
                        val beforeCpu = cpu.currentThreadCpuTime
                        assertTrue(flow.tryEmit(value))
                        processor += cpu.currentThreadCpuTime - beforeCpu
                        elapsed += System.nanoTime() - before
                        if (paced) delay(1)
                    }
                    withTimeout(30_000) { while (received.get() < count * 1000) delay(1) }
                    jobs.forEach { it.cancelAndJoin() }
                    if (repetition > 0) rows += "$count,${if (paced) "paced" else "burst"}${if (instrumented) "-instrumented" else ""},$repetition,${elapsed/1e6},${processor/1e6},${received.get()}"
                }
            }
        }
        File(requireNotNull(destination)).apply { parentFile?.mkdirs(); writeText(rows.joinToString("\n") + "\n") }
        Unit
    }
}
