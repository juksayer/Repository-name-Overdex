package com.example.overdex.data.registration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import com.example.overdex.battle.audio.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlin.coroutines.coroutineContext

@Serializable data class RegistrationCryCandidate(val speciesId: Int, val speciesName: String,
    val similarity: Float, val referenceSha256: String)
@Serializable data class RegistrationCryEvidence(val capturedAt: Long, val audioSha256: String,
    val durationMillis: Long, val candidates: List<RegistrationCryCandidate>, val confirmedSpeciesId: Int? = null,
    val source: String = "POKEMON_GO_INTERNAL_PLAYBACK")
data class RegistrationCryState(val busy: Boolean = false, val message: String = "", val evidence: RegistrationCryEvidence? = null)

/** Explicit five-second import sample. No microphone fallback, file retention or battle witnesses. */
object RegistrationCryCapture {
    suspend fun record(context: Context, projection: MediaProjection): Pcm16Audio {
        val sampleRate = 48_000
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUid(context.packageManager.getApplicationInfo("com.nianticlabs.pokemongo", 0).uid)
            .addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val record = AudioRecord.Builder().setAudioPlaybackCaptureConfig(capture)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(sampleRate / 5 * 2, AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "Game audio capture is unavailable." }
            record.startRecording()
            val samples = ShortArray(sampleRate * 5)
            var offset = 0
            while (offset < samples.size) {
                coroutineContext.ensureActive()
                val read = record.read(samples, offset, minOf(sampleRate / 50, samples.size - offset), AudioRecord.READ_BLOCKING)
                check(read > 0) { "Game audio capture stopped." }
                offset += read
            }
            val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
            check(peak >= 64) { "No useful game audio. Enable Pokémon sound effects and retry." }
            val threshold = maxOf(64, peak / 25)
            val first = samples.indexOfFirst { kotlin.math.abs(it.toInt()) >= threshold }
            val last = samples.indexOfLast { kotlin.math.abs(it.toInt()) >= threshold }
            check(last - first >= sampleRate / 10) { "The sound was too short. Tap the Pokémon after Listen starts." }
            return Pcm16Audio(sampleRate, 1, samples.copyOfRange((first - 2400).coerceAtLeast(0), (last + 2400).coerceAtMost(samples.size)))
        } finally { runCatching { record.stop() }; record.release() }
    }

    fun fingerprint(audio: Pcm16Audio): String {
        val bytes = ByteBuffer.allocate(audio.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        audio.samples.forEach(bytes::putShort)
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }
}
