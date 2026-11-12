package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import kotlinx.coroutines.*
import com.example.overdex.model.Pokemon

class MatchProcessingDiagnosticsTest {
    @Test fun `elapsed waiting and CPU work remain separate and counters are bounded`() {
        var elapsed = 0L; var cpu = 0L
        val d = MatchProcessingDiagnostics({ elapsed }, { cpu })
        val s = d.begin(7, "fixture")
        elapsed += 1_000_000_000; cpu += 100
        s.next("slow_compute")
        elapsed += 500; cpu += 400
        s.complete(7)
        val result = d.snapshot()
        assertEquals(1_000_000_000, result.stages.getValue("raw_preparation/fixture").elapsedNanos)
        assertEquals(100, result.stages.getValue("raw_preparation/fixture").cpuNanos)
        assertEquals(400, result.stages.getValue("slow_compute/fixture").cpuNanos)
        assertEquals(1, result.completed); assertNull(result.activeSequence)
        repeat(3_000) { d.begin(it.toLong(), "kind$it").complete(it.toLong()); d.rebuild("resolver$it", it) }
        assertTrue(d.snapshot().stages.size <= MatchProcessingDiagnostics.MAX_KEYS + 1)
        assertTrue(d.snapshot().rebuilds.size <= MatchProcessingDiagnostics.MAX_REBUILD_KEYS + 1)
        assertTrue(d.snapshot().stages.values.all { it.histogram.sum() == it.calls })
        assertTrue(d.snapshot().stages.values.all { it.cpuHistogram.sum() + it.invalidCpuSamples == it.calls })
    }

    @Test fun `publication breakdown isolates append delay without overlapping stages`() = runBlocking {
        val wall = java.util.concurrent.atomic.AtomicLong(0)
        val cpu = java.util.concurrent.atomic.AtomicLong(0)
        val diagnostics = MatchProcessingDiagnostics(wall::get, cpu::get)
        val backing = InMemoryRealityTimeline()
        val timeline = object : RealityTimeline {
            override fun append(article: RealityArticle) {
                wall.addAndGet(500_000_000)
                cpu.addAndGet(50_000_000)
                backing.append(article)
            }
            override fun getArticles() = backing.getArticles()
        }
        val custody = InMemoryTestimonyCustody()
        val match = Match("publication-breakdown", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(), processingDiagnostics = diagnostics)
        try {
            custody.submitTestimony(SourceId("FIXTURE"), RawTestimony("unclassified fixture"),
                1234, .9f, listOf("crop-proof"), 1234000)
            withTimeout(5000) { match.awaitAcceptedEvidencePublished() }
            val stages = diagnostics.snapshot().stages.filterKeys { it.startsWith("raw_publication.") }
            assertEquals(setOf("timeline_append", "hp_timing", "species_cues", "article_broadcast", "receipt_notification"),
                stages.keys.map { it.substringAfter('.').substringBefore('/') }.toSet())
            assertTrue(stages.values.all { it.calls == 1L })
            assertEquals(500_000_000L, stages.values.sumOf { it.elapsedNanos })
            assertEquals(50_000_000L, stages.values.sumOf { it.cpuNanos })
            assertEquals(500_000_000L, stages.getValue("raw_publication.timeline_append/RawTestimony").elapsedNanos)
            assertEquals(0L, stages.getValue("raw_publication.article_broadcast/RawTestimony").elapsedNanos)
            val raw = backing.getArticles().single()
            assertEquals(1234L, raw.perceivedAt)
            assertEquals(1234000L, raw.monotonicTimeNanos)
            assertEquals(listOf("crop-proof"), raw.evidenceReferences)
            assertEquals(1L, diagnostics.snapshot().completed)
            assertEquals(1, custody.getRecords().filterIsInstance<TestimonyRecord>().size)
        } finally { match.release() }
    }

    @Test fun `unsupported CPU clock does not pretend to measure idle computation`() {
        val d = MatchProcessingDiagnostics({ 100L }, { -1L })
        d.begin(0, "unsupported").complete(0)
        val timing = d.snapshot().stages.values.single()
        assertEquals(1L, timing.invalidCpuSamples)
        assertEquals(0L, timing.cpuHistogram.sum())
    }

    @Test fun `CPU delta across threads is marked unavailable`() {
        val d = MatchProcessingDiagnostics({ 100L }, { 100L })
        val span = d.begin(0, "thread-hop")
        val worker = Thread { span.complete(0) }
        worker.start(); worker.join()
        assertEquals(1L, d.snapshot().stages.values.single().invalidCpuSamples)
    }

    @Test fun `active progress survives snapshots without completing work`() {
        var elapsed = 10L
        val d = MatchProcessingDiagnostics({ elapsed }, { 0 })
        val s = d.begin(99, "HP"); s.next("faint_reconciliation"); elapsed = 110
        assertEquals(100, d.snapshot().activeAgeNanos)
        assertEquals("faint_reconciliation", d.snapshot().activeStage)
        assertEquals(0, d.snapshot().completed)
        s.complete(99)
        assertEquals(99, d.snapshot().lastCompletedSequence)
    }
    @Test fun `suspended legacy interpretation remains pending and emits no profiler testimony`() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        val wall = java.util.concurrent.atomic.AtomicLong(0)
        val cpu = java.util.concurrent.atomic.AtomicLong(0)
        val diagnostics = MatchProcessingDiagnostics(wall::get, cpu::get)
        val knowledge = object : FakePokemonKnowledge() {
            override suspend fun getPokemonByName(name: String): Pokemon? {
                entered.complete(Unit); resume.await(); return super.getPokemonByName(name)
            }
        }
        val custody = InMemoryTestimonyCustody(); val timeline = InMemoryRealityTimeline()
        val match = Match("diagnostic-suspension", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = knowledge, processingDiagnostics = diagnostics)
        try {
            custody.submitTestimony(SourceId("SPECIES_WITNESS"), RawTestimony("Pikachu"), 1234, null, listOf("crop"), 1234000)
            withTimeout(5000) {
                entered.await()
                assertEquals("legacy_interpretation", diagnostics.snapshot().activeStage)
                val drain = async(start = CoroutineStart.UNDISPATCHED) { match.awaitAcceptedEvidencePublished() }
                assertFalse(drain.isCompleted)
                wall.set(10_000_000_000); cpu.set(200)
                resume.complete(Unit); drain.await()
            }
            val timing = diagnostics.snapshot().stages.getValue("legacy_interpretation/RawTestimony")
            assertEquals(10_000_000_000, timing.elapsedNanos); assertEquals(200, timing.cpuNanos)
            assertEquals(1, custody.getRecords().filterIsInstance<TestimonyRecord>().size)
            assertEquals(1234000L, timeline.getArticles().single().monotonicTimeNanos)
        } finally { resume.complete(Unit); match.release() }
    }

    @Test fun `instrumentation preserves raw evidence derived content and lineage`() = runBlocking {
        suspend fun capture(enabled: Boolean): List<String> {
            val custody = InMemoryTestimonyCustody(); val timeline = InMemoryRealityTimeline()
            val match = Match("equivalence",custody=custody,realityTimeline=timeline,pokemonKnowledge=FakePokemonKnowledge(),
                processingDiagnostics=if(enabled) MatchProcessingDiagnostics() else null,
                recordArticleId={ArticleId("source-$it")})
            try {
                custody.submitTestimony(SourceId("ATTACK_INCOMING_WITNESS"),RawTestimony("Attack incoming!"),
                    5000, .9f, listOf("independent-crop"), 5000000)
                custody.submitAvailability(SourceId("fixture"),false,5001)
                withTimeout(5000) { match.awaitAcceptedEvidencePublished() }
                assertTrue(timeline.getArticles().any { it.payload == AttackIncoming })
                return timeline.getArticles().map { a ->
                    "${a.sourceId}|${a.payload}|${a.perceivedAt}|${if(a.payload is WitnessOperating) "availability-clock" else a.monotonicTimeNanos}|${a.confidence}|${a.predecessorIds}|${a.evidenceReferences}"
                }.sorted()
            } finally { match.release() }
        }
        assertEquals(capture(false),capture(true))
    }

}
