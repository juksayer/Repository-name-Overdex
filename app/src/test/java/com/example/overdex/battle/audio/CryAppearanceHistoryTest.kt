package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class CryAppearanceHistoryTest {
    private fun identity(id:String,at:Long,species:Int?,entry:Boolean=false)=
        CryAppearanceHistory.Identity(id,at,ActivePokemonSide.OPPONENT,species,entry)
    @Test fun `delayed identities reconstruct source order without borrowing replacement identity`() {
        val a=identity("a",1,42);val b=identity("b",5,260);val c=identity("c",8,460)
        val history=CryAppearanceHistory();listOf(c,a,b).forEach(history::accept)
        val intervals=history.intervals()
        assertEquals(listOf(42,260,460),intervals.map { it.speciesId })
        assertEquals(listOf(5L,8L,Long.MAX_VALUE),intervals.map { it.untilNanos })
    }
    @Test fun `repeated badge keeps appearance but explicit same species entry begins another`() {
        val history=CryAppearanceHistory()
        listOf(identity("a",1,42),identity("repeat",2,42),identity("entry",5,42,true)).forEach(history::accept)
        assertEquals(listOf("a","entry"),history.intervals().map { it.id })
    }
    @Test fun `simultaneous conflicting readings form an unknown interval`() {
        val history=CryAppearanceHistory()
        listOf(identity("a",1,42),identity("b",1,260),identity("c",3,460)).forEach(history::accept)
        assertNull(history.intervals().first().speciesId)
    }
    @Test fun `bounded overflow is explicit unavailable history rather than a false appearance`() {
        val history=CryAppearanceHistory()
        for(i in 0..CryAppearanceHistory.MAX_IDENTITIES)history.accept(identity("i$i",i.toLong(),42))
        assertTrue(history.unavailable);assertTrue(history.intervals().isEmpty())
        history.reset();assertFalse(history.unavailable)
    }
}
