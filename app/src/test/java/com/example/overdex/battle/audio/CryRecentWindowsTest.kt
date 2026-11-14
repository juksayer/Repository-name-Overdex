package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class CryRecentWindowsTest {
    private fun window(at:Long,generation:Long=0,source:String="game")=CryAudioWindowSnapshot(
        Pcm16Audio(8000,1,ShortArray(32000)),at*1_000_000_000,at*1000,source,at,generation)
    @Test fun `late identity catchup is bounded in age count and acquisition generations`() {
        val recent=CryRecentWindows()
        for(t in 4L..100L)recent.remember(window(t))
        val selected=recent.preceding(window(101))
        assertEquals(10,selected.size)
        assertEquals(91_000_000_000,selected.first().endNanos)
        assertTrue(recent.preceding(window(115)).isEmpty())
        recent.remember(window(102,1))
        assertTrue(recent.preceding(window(103,0)).isEmpty())
        assertEquals(1,recent.preceding(window(103,1)).size)
        recent.remember(window(104,1,"new-source"))
        assertTrue(recent.preceding(window(105,1)).isEmpty())
    }
}
