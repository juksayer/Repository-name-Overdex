package com.example.overdex.battle.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A frame contains one PCM-16 sample per channel, ordered L/R for stereo. */
data class Pcm16CaptureFormat(val sampleRateHz: Int = 48_000, val channelCount: Int = 2) {
    init {
        require(sampleRateHz in 1..192_000)
        require(channelCount in 1..2)
    }

    val bytesPerFrame: Int get() = channelCount * 2
    val bytesPerSecond: Int get() = sampleRateHz * bytesPerFrame

    fun bytesForMillis(millis: Int): Int {
        require(millis >= 0)
        return Math.toIntExact(sampleRateHz.toLong() * millis / 1_000 * bytesPerFrame)
    }

    fun cueCollector() = CueCenteredPcmCollector(
        preRollBytes = bytesForMillis(500),
        postRollBytes = bytesForMillis(700),
        bytesPerSecond = bytesPerSecond,
        channelCount = channelCount,
    )

    fun decode(pcm16le: ByteArray, count: Int = pcm16le.size): Pcm16Audio {
        require(count in 0..pcm16le.size && count % bytesPerFrame == 0)
        val samples = ShortArray(count / 2)
        ByteBuffer.wrap(pcm16le, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return Pcm16Audio(sampleRateHz, channelCount, samples)
    }
}

data class PcmCaptureRequest(val source: String, val format: Pcm16CaptureFormat)

/** Never give up game-only capture just because a device cannot provide stereo. */
object BattleAudioCapturePlan {
    private val requests = listOf(
        PcmCaptureRequest("PLAYBACK_POKEMON_GO", Pcm16CaptureFormat(channelCount = 2)),
        PcmCaptureRequest("PLAYBACK_POKEMON_GO", Pcm16CaptureFormat(channelCount = 1)),
        PcmCaptureRequest("MICROPHONE", Pcm16CaptureFormat(channelCount = 1)),
    )

    fun <T : Any> start(createAndStart: (PcmCaptureRequest) -> T?): Pair<PcmCaptureRequest, T>? {
        for (request in requests) {
            val recording = createAndStart(request) ?: continue
            return request to recording
        }
        return null
    }
}
