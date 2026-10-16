package com.example.overdex.battle.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavPcm16Test {
    @Test fun `known bundled zero length header is recovered without modifying original or accepting it as live evidence`() {
        val pcm = byteArrayOf(-1, 127, 0, -128, -1, -1, 0, 0)
        val standard = Pcm16WavEncoder.encode(pcm, 11_025, 1)
        // The bundled export uses fmt size 18, a zero extension length, and
        // leaves RIFF/data sizes unwritten. Its content hash covers these bytes.
        val exported = ByteArray(standard.size + 2)
        standard.copyInto(exported, 0, 0, 36)
        standard.copyInto(exported, 38, 36)
        ByteBuffer.wrap(exported).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, 0); putInt(16, 18); putInt(42, 0)
        }
        val original = exported.copyOf()
        assertNull(WavPcm16.decode(exported))
        assertArrayEquals(WavPcm16.decode(standard)!!.samples, WavPcm16.decodeBundledReference(exported)!!.samples)
        assertArrayEquals(original, exported)
        assertNull(WavPcm16.decodeBundledReference(exported.copyOf(exported.size - 1)))
        assertNull(WavPcm16.decodeBundledReference(exported.copyOf().apply { this[20] = 3 }))
    }

    @Test fun `full stereo clip preserves signed little endian samples in order`() {
        val samples = ShortArray(48_000 * 2) { index -> (index * 31 - 32768).toShort() }
        samples[0] = Short.MIN_VALUE
        samples[1] = Short.MAX_VALUE
        samples[2] = -1
        samples[3] = 0
        val pcm = ByteArray(samples.size * 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        val decoded = WavPcm16.decode(Pcm16WavEncoder.encode(pcm, 48_000, 2))!!
        assertEquals(48_000, decoded.sampleRateHz)
        assertEquals(2, decoded.channelCount)
        assertArrayEquals(samples, decoded.samples)
    }

    @Test fun `truncated capture remains unavailable instead of yielding partial audio`() {
        val wav = Pcm16WavEncoder.encode(byteArrayOf(-1, 127, 0, -128), 48_000, 1)
        assertNull(WavPcm16.decode(wav.copyOf(wav.size - 1)))
        val impossibleSize = wav.copyOf()
        ByteBuffer.wrap(impossibleSize).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE)
        assertNull(WavPcm16.decode(impossibleSize))
    }

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
