package com.example.overdex.battle.inference

import org.junit.Assert.*
import org.junit.Test

class TerminalCollapseSequenceTest {
    @Test fun `factory crop full recordings preserve finishes and expose QTE lookalikes at twenty Hz`() {
        // Wider territory includes five visually reviewed QTE lookalikes, plus the two
        // final charged collapses. These are effect sequences, never standalone faints.
        val expected = mapOf(
            "211335" to listOf(181.400622222),
            "112107" to listOf(93.540211111, 95.022866666, 111.0607, 111.9284, 118.178833333),
            "124455" to listOf(133.554955555),
            "161531" to emptyList(), "190345" to emptyList())
        for ((stamp, ends) in expected) {
            val actual = TerminalCollapseSequences.find(TerminalSequenceFixtures.factoryFrames(stamp))
            assertEquals(stamp, ends.size, actual.size)
            ends.zip(actual).forEach { (time, seq) -> assertEquals(stamp, time, seq.clearedAt / 1e9, 0.000001) }
        }
    }
    @Test fun `full five-recording numeric corpus preserves two finishes and the competing QTE sequence`() {
        val expected = mapOf("211335" to listOf(181.400622222), "112107" to listOf(111.901422222,118.137611111),
            "124455" to emptyList(), "161531" to emptyList(), "190345" to emptyList())
        for ((stamp, ends) in expected) {
            val actual = TerminalCollapseSequences.find(TerminalSequenceFixtures.frames(stamp))
            assertEquals(stamp, ends.size, actual.size)
            ends.zip(actual).forEach { (time, seq) -> assertEquals(time, seq.clearedAt / 1e9, 0.000001) }
        }
    }

    @Test fun `source reordering and duplicate batch deliveries cannot change the sequence`() {
        val frames = TerminalSequenceFixtures.frames("211335").filter { it.sample.sourceAtNanos in 180_500_000_000L..182_000_000_000L }
        val expected = TerminalCollapseSequences.find(frames)
        assertEquals(1, expected.size)
        assertEquals(expected, TerminalCollapseSequences.find(frames.reversed() + frames))
    }

    @Test fun `missing obscured conflicting or moved frames cannot provide the two clear observations`() {
        val frames = TerminalSequenceFixtures.frames("211335").filter { it.sample.sourceAtNanos in 180_500_000_000L..181_450_000_000L }
        val clear = TerminalCollapseSequences.find(frames).single().clearedAt
        for (status in listOf("OBSCURED", "SOURCE_GAP", "SOURCE_STALE", "CLUTTER", "BASELINE")) {
            assertTrue(status, TerminalCollapseSequences.find(frames.map {
                if (it.sample.sourceAtNanos == clear) it.copy(sample = it.sample.copy(status = status)) else it
            }).isEmpty())
        }
        val last = frames.single { it.sample.sourceAtNanos == clear }
        assertTrue(TerminalCollapseSequences.find(frames + last.copy(sample=last.sample.copy(flashes=frames.first().sample.flashes,
            status="OBSCURED"))).isEmpty())
        assertTrue(TerminalCollapseSequences.find(frames.map { if (it == last)
            it.copy(sample=it.sample.copy(publicationStream=99)) else it }).isEmpty())
        assertTrue(TerminalCollapseSequences.find(frames.map { if (it == last)
            it.copy(sample=it.sample.copy(sourceWidth=1080)) else it }).isEmpty())
    }
}
