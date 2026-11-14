package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class RepeatCryDepartureRuleTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private fun time(seconds: Double) = (seconds * 1_000_000_000L).toLong()
    private fun appearance(id: String, side: ActivePokemonSide, species: Int?, from: Double, until: Double) =
        CryAppearanceInterval(id, side, species, time(from), time(until), listOf("identity-$id"))
    private fun cry(id: String, species: Int, at: Double, duration: Double = when (species) {
        776 -> 2.072; 323 -> 1.190; 42 -> 1.051; 260 -> 1.196; 460 -> 1.921; 711 -> 2.003; else -> .3
    }) =
        LocalizedCryOccurrence(id, species, time(at), time(at + duration), listOf("audio-$id"))

    @Test fun `recorded 1605 sequence separates four departure candidates from both ordinary switches`() {
        // Video-labeled source intervals and waveform onset matches; not archive faint conclusions.
        val appearances = listOf(
            appearance("p-t", player, 776, 30.0, 76.0), appearance("p-c", player, 323, 76.0, 98.0),
            appearance("p-g", player, 711, 98.0, 140.0), appearance("o-g", opponent, 42, 30.0, 96.0),
            appearance("o-s1", opponent, 260, 96.0, 100.0), appearance("o-a", opponent, 460, 100.0, 109.0),
            appearance("o-s2", opponent, 260, 109.0, 140.0),
        )
        val cries = listOf(cry("t1",776,31.996), cry("g1",42,33.105), cry("t2",776,74.615),
            cry("c1",323,77.367), cry("g2",42,93.943), cry("s1",260,97.740),
            cry("pg1",711,98.785), cry("a1",460,101.217), cry("a2",460,107.895),
            cry("s2",260,110.135), cry("s3",260,137.015))
        val result = RepeatCryDepartureRule.candidates(cries, appearances)
        assertEquals(listOf("t2","g2","a2","s3"), result.map { it.cryOccurrenceIds.last() })
        assertEquals(listOf(player,opponent,opponent,opponent),result.map { it.side })
        assertEquals(result, RepeatCryDepartureRule.candidates(cries.reversed(), appearances.reversed()))
        assertTrue(result.none { "o-s1" in it.possibleAppearanceIds || "p-c" in it.possibleAppearanceIds })
    }

    @Test fun `overlapping windows cannot count one cry twice or extend a candidate repeatedly`() {
        val history=listOf(appearance("p",player,776,0.0,100.0),appearance("o",opponent,42,0.0,100.0))
        val sounds=listOf(cry("a",42,1.0,1.0),cry("duplicate",42,1.2,1.0),cry("b",42,5.0),cry("c",42,8.0))
        val result=RepeatCryDepartureRule.candidates(sounds,history)
        assertEquals(1,result.size)
        assertEquals(listOf("a","b"),result.single().cryOccurrenceIds)
    }

    @Test fun `simultaneous mirrors remain ambiguous even with extra samples`() {
        val history=listOf(appearance("p",player,776,0.0,100.0),appearance("o",opponent,776,0.0,100.0))
        val result=RepeatCryDepartureRule.candidates((1..6).map { cry("c$it",776,it*3.0) },history)
        assertNull(result.single().side)
        assertEquals(listOf("o","p"),result.single().possibleAppearanceIds)
    }

    @Test fun `same species later safe swap starts its own cry history`() {
        val history=listOf(appearance("p1",player,776,0.0,10.0),appearance("p2",player,711,10.0,50.0),
            appearance("o1",opponent,42,0.0,12.0),appearance("o2",opponent,776,12.0,50.0))
        val sounds=listOf(cry("lead",776,1.0),cry("swap",776,13.0),cry("repeat",776,20.0))
        val result=RepeatCryDepartureRule.candidates(sounds,history)
        assertEquals(listOf("swap","repeat"),result.single().cryOccurrenceIds)
        assertEquals(opponent,result.single().side)
    }

    @Test fun `unknown opposing identity and contradictory sources cannot select a side`() {
        val sounds=listOf(cry("a",776,1.0),cry("b",776,5.0))
        val p=appearance("p",player,776,0.0,100.0)
        assertTrue(RepeatCryDepartureRule.candidates(sounds,listOf(p)).isEmpty())
        assertTrue(RepeatCryDepartureRule.candidates(sounds,listOf(p,appearance("o",opponent,null,0.0,100.0))).isEmpty())
        assertTrue(RepeatCryDepartureRule.candidates(sounds + sounds.first().copy(speciesId=42),
            listOf(p,appearance("o",opponent,42,0.0,100.0))).isEmpty())
    }
}
