package com.example.overdex.battle.inference

import org.junit.Assert.*
import org.junit.Test

class RecordedTerminalAppearanceSequenceTest {
    @Test fun `full recordings preserve two charged endings and expose the living switch counterexample`() {
        val expected=mapOf("190345" to listOf(170.687066666,358.7727),
            "124455" to listOf(110.247922222),"161531" to emptyList(),"211335" to emptyList(),"112107" to emptyList())
        var frames=0
        expected.forEach { (stamp,ends) ->
            val samples=TerminalAppearanceFixtures.samples(stamp)
            frames+=samples.size
            val actual=TerminalAppearanceSequences.find(samples.mapIndexed { i,s -> TerminalEffectFrame("$stamp-$i",s) })
            assertEquals(stamp,ends.size,actual.size)
            ends.zip(actual).forEach { (end,sequence) -> assertEquals(stamp,end,sequence.clearedAt/1e9,.000001) }
        }
        assertEquals(12241,frames)
    }

    @Test fun `living Quagsire bright washout retires the frozen color rather than producing a disappearance`() {
        val samples=TerminalAppearanceFixtures.samples("211335").filter { it.sourceAtNanos in 77_000_000_000..80_000_000_000 }
        assertTrue("Exercise measured occlusion, not an empty test",samples.any { s -> s.appearance!!.supports.any {
            it.area<=.65*it.baselineArea && it.brightenedFraction>.25
        } })
        assertTrue(TerminalAppearanceSequences.find(samples.mapIndexed { i,s -> TerminalEffectFrame("washout-$i",s) }).isEmpty())
    }
}
