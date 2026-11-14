package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CryWaveformMatcherTest {
    private fun audio(samples: ShortArray) = Pcm16Audio(8_000, 1, samples)
    private fun signal(seed: Int, length: Int = 1600): ShortArray {
        val random = Random(seed)
        return ShortArray(length) { random.nextInt(-6000,6001).toShort() }
    }
    @Test fun `localizes a full cry through gain polarity offset and independent noise`() {
        val reference=signal(1)
        val query=signal(2,12000)
        for(i in reference.indices) query[i+3217]=(-reference[i]*2+query[i]/4+1000).toShort()
        val result=CryWaveformMatcher(listOf(CryWaveformReference(42,"ref",audio(reference)))).measure(audio(query)).single()
        assertEquals(3217L*1_000_000_000/8000,result.offsetNanos)
        assertEquals(200_000_000L,result.durationNanos)
        assertTrue(result.correlation>.98f)
    }
    @Test fun `equal amplitude envelopes do not make unrelated waveforms the same cry`() {
        val ref=signal(1);val other=signal(2)
        val fit=CryWaveformMatcher(listOf(CryWaveformReference(42,"ref",audio(ref)))).measure(audio(other)).single()
        assertTrue(fit.correlation<.15f)
    }
    @Test fun `silence short windows and empty candidate pools cannot match`() {
        val matcher=CryWaveformMatcher(listOf(CryWaveformReference(42,"ref",audio(signal(1)))))
        assertEquals(0f,matcher.measure(audio(ShortArray(3000))).single().correlation)
        assertTrue(matcher.measure(audio(ShortArray(100))).isEmpty())
        assertTrue(CryWaveformMatcher(emptyList()).measure(audio(signal(2))).isEmpty())
    }
    @Test fun `cached transforms remain correct when window size changes`() {
        val reference=signal(1)
        val matcher=CryWaveformMatcher(listOf(CryWaveformReference(42,"ref",audio(reference))))
        for(length in listOf(4000,9000,4000)) {
            val query=ShortArray(length); reference.copyInto(query,1000)
            val fit=matcher.measure(audio(query)).single()
            assertEquals(125_000_000L,fit.offsetNanos)
            assertEquals(1f,fit.correlation,.00001f)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun `rejects an unbounded analysis window`() {
        CryWaveformMatcher(emptyList()).measureMono8k(ShortArray(32001))
    }
}
