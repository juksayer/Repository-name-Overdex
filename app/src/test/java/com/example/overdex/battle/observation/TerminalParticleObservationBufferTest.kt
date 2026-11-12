package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.VisualFramePublication
import org.junit.Assert.*
import org.junit.Test

class TerminalParticleObservationBufferTest {
    private val start = 100_000_000_000L
    private var clock = start
    private var resets = 0
    private var reads = 0
    private val batches = mutableListOf<RealityArticle>()
    private val buffer = TerminalParticleObservationBuffer({ resets++ }, { payload, refs, at, wall ->
        batches += RealityArticle(ArticleId("batch-${batches.size}"), wall, wall + 1000,
            SourceId("TERMINAL_PARTICLE_WITNESS"), payload, evidenceReferences = refs,
            monotonicTimeNanos = at, matchId = MatchId("particles"))
    }, { clock })
    private val assignment = TerminalParticleAssignment(ActivePokemonSide.OPPONENT, start, start + 20_000_000_000L, listOf("grade"))
    private val bounds = BattleCropBounds(0,0,672,1000)
    private fun sample(at: Long = clock, readable: Boolean = true, pub: VisualFramePublication? = null,
        rect: BattleCropBounds? = bounds): String? = buffer.accept(672,1493,rect,at,7000 + at - start,pub,readable) {
        reads++
        TerminalParticleFrameMeasurement(TerminalParticleReadStatus.SAMPLED, at, at - 50_000_000L,
            listOf(TerminalParticlePoint(100.0,200.0,10,3,4,.8,.8,.6,30.0)))
    }
    private fun payloads() = batches.map { it.payload as TerminalParticleObservations }

    @Test fun `independent appearance survives missing particle bounds and archive round trip without inventing a particle`() {
        buffer.synchronize(assignment.copy(side=ActivePokemonSide.PLAYER),7000)
        val appearance=TerminalAppearanceMeasurement("SAMPLED",336,747,
            listOf(listOf(0,373,185,710),listOf(220,300,336,560),listOf(0,210,150,315)),
            1.0,18.0,50.0,listOf(TerminalAppearanceSupport(start-200_000_000,listOf(1,2),4000,3500,2000,.1)),
            previousSourceAtNanos=start-50_000_000)
        buffer.accept(672,1493,null,clock,7000,null,true,independentAppearanceReadable=true) {
            TerminalParticleFrameMeasurement(TerminalParticleReadStatus.UNREADABLE,clock,appearance=appearance)
        }
        buffer.synchronize(null,8000)
        val sample=payloads().single().samples.single()
        assertEquals(appearance,sample.appearance)
        assertNull(sample.bounds)
        assertNull(sample.previousSourceAtNanos)
        assertEquals("UNREADABLE",sample.status)
        assertTrue(sample.points.isEmpty())
        val archive=MatchArchive(matchId="appearance",articles=batches.map(RealityArticleArchiveMapper::map))
        assertEquals(archive,MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive)))
    }

    @Test fun `high rate readings retain their original clocks geometry and features through compact archive`() {
        buffer.synchronize(assignment,7000)
        repeat(21) { i -> clock = start + i * 50_000_000L; sample(pub = VisualFramePublication(1,i + 1L,clock)) }
        buffer.synchronize(null,8000)
        assertEquals(listOf(20,1),payloads().map { it.samples.size })
        assertTrue(payloads().last().ended)
        assertEquals(21,payloads().last().totalSamples)
        val mapped = batches.map(RealityArticleArchiveMapper::map)
        val compact = MatchArchiveArticleSelector.select(mapped,MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(mapped,compact)
        val archive = MatchArchive(matchId = "particles",articles = compact)
        assertEquals(archive,MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive)))
        val samples = compact.flatMap { (it.payload as ArchivedTerminalParticleObservations).samples }
        assertEquals((0..20).map { start + it * 50_000_000L }, samples.map { it.sourceAtNanos })
        assertEquals(21L,samples.last().publicationSequence)
        assertEquals(listOf(0,0,672,1000),samples.last().bounds)
        assertEquals(100.0,samples.last().points.single().x,0.0)
        assertTrue(compact.all { it.evidenceReferences == listOf("grade") })
        assertFalse(compact.any { it.payload is ArchivedActivePokemonFaintedWitnessed || it.payload is ArchivedActiveHpBarMeasured })
    }

    @Test fun `obscured stale and invalid frames interrupt pixels without becoming absent-sprite measurements`() {
        buffer.synchronize(assignment,7000)
        sample(readable = false)
        clock += 600_000_000L
        sample(at = start + 1)
        clock++
        sample(rect = null)
        buffer.synchronize(null,8000)
        assertEquals(0,reads)
        assertEquals(listOf("OBSCURED","SOURCE_STALE","INVALID_BOUNDS"),payloads().single().samples.map { it.status })
        assertTrue(payloads().single().samples.all { it.points.isEmpty() && it.previousSourceAtNanos == null })
        assertEquals(5,resets) // Open, three interruptions, close.
    }

    @Test fun `duplicate and older frames cannot be attached to a later appearance`() {
        buffer.synchronize(assignment,7000)
        sample()
        clock += 1_000_000_000L
        buffer.synchronize(assignment.copy(phaseAt=clock,until=clock + 10_000_000_000L,evidenceReferences=listOf("new")),8000)
        assertNull(sample(at=start))
        sample()
        assertNull(sample())
        assertNull(sample(at=clock - 1))
        buffer.synchronize(null,8000)
        assertEquals(2,reads)
        assertEquals(listOf(listOf("grade"),listOf("new")),batches.map { it.evidenceReferences })
    }

    @Test fun `old stream never replaces the newer watermark and stream replacement resets continuity`() {
        buffer.synchronize(assignment,7000)
        sample(pub=VisualFramePublication(3,5,clock))
        clock++
        assertEquals("OLD_PUBLICATION",sample(pub=VisualFramePublication(2,9,clock)))
        clock++
        assertEquals("OLD_PUBLICATION",sample(pub=VisualFramePublication(3,4,clock)))
        clock++
        sample(pub=VisualFramePublication(4,1,clock))
        assertEquals(2,reads)
        assertEquals(4,resets)
    }

    @Test fun `a delayed Used context flushes previous readings without rewriting them`() {
        buffer.synchronize(assignment,7000); sample()
        clock += 50_000_000L
        buffer.synchronize(assignment.copy(evidenceReferences=listOf("grade","used")),7050); sample()
        buffer.synchronize(null,7100)
        assertEquals(listOf(listOf("grade"),listOf("grade","used")),batches.map { it.evidenceReferences })
        assertEquals(listOf(false,true),payloads().map { it.ended })
        assertEquals(2,resets)
    }

    @Test fun `timer flushes a partial batch only when due and ending preserves zero-frame availability`() {
        buffer.synchronize(assignment,7000); sample()
        clock += 100_000_000L; buffer.flushIfDue()
        assertTrue(batches.isEmpty())
        clock += 1_000_000_000L; buffer.flushIfDue()
        assertEquals(1,batches.size)
        buffer.synchronize(null,9000)
        assertTrue(payloads().last().ended)
        assertTrue(payloads().last().samples.isEmpty())
        buffer.synchronize(assignment.copy(phaseAt=clock),9000)
        buffer.synchronize(null,9001)
        assertEquals(0,payloads().last().totalSamples)
    }

    @Test fun `unbounded custom input cannot overrun per phase measurement budget`() {
        buffer.synchronize(assignment,7000)
        repeat(1000) { clock++; sample() }
        buffer.synchronize(null,8000)
        assertEquals(TerminalParticleObservationBuffer.MAX_SAMPLES,reads)
        assertEquals(600,payloads().last().totalSamples)
        assertTrue(payloads().all { it.samples.size <= 20 })
    }
}
