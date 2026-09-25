package com.example.overdex.battle.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodes little-endian 16-bit PCM as a canonical WAV artifact. */
object Pcm16WavEncoder {
    fun encode(pcm16le: ByteArray, sampleRateHz: Int, channelCount: Int): ByteArray {
        require(sampleRateHz > 0)
        require(channelCount > 0)
        require(pcm16le.size % (channelCount * 2) == 0)
        val byteRate = sampleRateHz * channelCount * 2
        return ByteBuffer.allocate(44 + pcm16le.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm16le.size)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channelCount.toShort())
            putInt(sampleRateHz)
            putInt(byteRate)
            putShort((channelCount * 2).toShort())
            putShort(16)
            put("data".toByteArray())
            putInt(pcm16le.size)
            put(pcm16le)
        }.array()
    }
}
