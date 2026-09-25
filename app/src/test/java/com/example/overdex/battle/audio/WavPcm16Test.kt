package com.example.overdex.battle.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WavPcm16Test {
    @Test fun `decodes the WAV emitted by Droidball`() {
        val wav = Pcm16WavEncoder.encode(byteArrayOf(1, 0, 2, 0), 16_000, 1)
        val decoded = WavPcm16.decode(wav)!!
        assertEquals(16_000, decoded.sampleRateHz)
        assertEquals(2, decoded.samples.size)
    }

    @Test fun `same audio ranks its reference first despite leading silence`() {
        val source = Pcm16Audio(16_000, 1, shortArrayOf(0, 500, 2_000, 500, 0, 0, 0, 0))
        val query = Pcm16Audio(16_000, 1, shortArrayOf(0, 0, 0, 500, 2_000, 500, 0, 0, 0, 0))
        val candidate = CryReference("x", 25, null, "a".repeat(64), 0, "audio/wav")
        assertEquals(25, CryAcousticMatcher.rank(query, listOf(candidate to source)).single().speciesId)
    }
}
