package com.example.overdex.battle.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16WavEncoderTest {
    @Test
    fun `encodes PCM payload with a self-describing wav header`() {
        val pcm = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        val wav = Pcm16WavEncoder.encode(pcm, sampleRateHz = 16_000, channelCount = 1)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12)))
        assertEquals(16_000, header.getInt(24))
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16, header.getShort(34).toInt())
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
}
