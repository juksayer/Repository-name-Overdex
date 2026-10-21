package com.example.overdex.battle.audio

import com.example.overdex.model.observation.CapturedAudioFrame
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Pcm16CaptureFormatTest {
    @Test fun `stereo fractional cue preserves left right order duration and source time after ring wrap`() {
        val format = Pcm16CaptureFormat()
        val collector = format.cueCollector()
        val pcm = stereo(10 * 48_000)
        val readSize = format.bytesForMillis(20)
        for (end in readSize..pcm.size step readSize) {
            collector.ingest(pcm.copyOfRange(end - readSize, end),
                end * 1_000_000_000L / format.bytesPerSecond, 100_000L + end * 1_000L / format.bytesPerSecond)
        }
        val cue = AudioCaptureCue("fractional", BattleCryCueKind.FAST_MOVE_IMPACT, 9_000_015_000L)
        assertNull(collector.cue(cue))
        val capture = collector.ingest(ByteArray(readSize), 10_020_000_000L, 110_020L)
            .single() as CueCenteredPcmCapture
        assertArrayEquals(pcm.copyOfRange(format.bytesForMillis(8_500), format.bytesForMillis(9_700)), capture.pcm16le)
        assertEquals(8_500_000_000L, capture.startedAtMonotonicNanos)
        assertEquals(108_500L, capture.startedAtWallTimeMillis)
        val frame = CapturedAudioFrame(capture.pcm16le, format.sampleRateHz, format.channelCount,
            capture.startedAtWallTimeMillis, capture.startedAtMonotonicNanos, cue.articleId, cue.kind)
        assertEquals(1_200_000_000L, frame.durationNanos)
        val decoded = WavPcm16.decode(Pcm16WavEncoder.encode(capture.pcm16le, format.sampleRateHz, format.channelCount))!!
        assertEquals(2, decoded.channelCount)
        assertEquals(57_600 * 2, decoded.samples.size)
        assertArrayEquals(format.decode(capture.pcm16le).samples, decoded.samples)
        assertTrue(decoded.samples.filterIndexed { i, _ -> i % 2 == 0 }.all { it > 0 })
        assertTrue(decoded.samples.filterIndexed { i, _ -> i % 2 == 1 }.all { it < 0 })
    }

    @Test fun `mono fallback keeps the same pre post and read durations`() {
        for (channels in 1..2) {
            val format = Pcm16CaptureFormat(channelCount = channels)
            val collector = format.cueCollector()
            collector.ingest(ByteArray(format.bytesForMillis(1_000)), 1_000_000_000L, 101_000L)
            collector.cue(AudioCaptureCue("cue", BattleCryCueKind.SPECIES_ENTRY, 1_000_000_000L))
            val capture = collector.ingest(ByteArray(format.bytesForMillis(700)), 1_700_000_000L, 101_700L)
                .single() as CueCenteredPcmCapture
            assertEquals(format.bytesForMillis(1_200), capture.pcm16le.size)
            assertEquals(500_000_000L, capture.startedAtMonotonicNanos)
            assertEquals(960 * channels * 2, format.bytesForMillis(20))
        }
    }

    @Test fun `stereo requests cannot split a frame at construction or ingestion`() {
        assertThrows(IllegalArgumentException::class.java) {
            CueCenteredPcmCollector(2, 4, 192_000, channelCount = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pcm16CaptureFormat().cueCollector().ingest(ByteArray(6), 1_000L, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) { Pcm16CaptureFormat().decode(ByteArray(6)) }
        val wav = Pcm16WavEncoder.encode(ByteArray(6), 48_000, 1)
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putShort(22, 2)
        assertNull(WavPcm16.decode(wav))
    }

    @Test fun `signed stereo downmix does not overflow swap channels or change source`() {
        val samples = shortArrayOf(32767, 32767, -32768, -32768, 12000, -6000, 500, -500)
        val original = samples.copyOf()
        val audio = Pcm16Audio(8_000, 2, samples)
        val mono = audio.monoSamples()
        assertArrayEquals(shortArrayOf(32767, -32768, 3000, 0), mono)
        assertArrayEquals(mono, StatChangeSoundMatcher.mono8k(audio))
        mono[0] = 0
        assertArrayEquals(original, audio.samples)
    }

    @Test fun `cry ranking uses the same mono projection for captured and reference stereo`() {
        val stereo = Pcm16CaptureFormat().decode(stereo(48_000))
        val mono = Pcm16Audio(48_000, 1, stereo.monoSamples())
        val reference = CryReference("cry", 25, null, "a".repeat(64), 0, "audio/wav")
        val prepared = CryAcousticMatcher.prepare(reference, stereo)
        assertArrayEquals(CryAcousticMatcher.prepare(reference, mono).envelope, prepared.envelope, 0f)
        assertEquals(CryAcousticMatcher.rankPrepared(mono, listOf(prepared)),
            CryAcousticMatcher.rankPrepared(stereo, listOf(prepared)))
        assertEquals(CryAcousticMatcher.rank(mono, listOf(reference to mono)),
            CryAcousticMatcher.rank(stereo, listOf(reference to stereo)))
    }

    @Test fun `supported stereo never opens mono or microphone even if the stream is quiet`() {
        val attempts = mutableListOf<PcmCaptureRequest>()
        val opened = BattleAudioCapturePlan.start { request ->
            attempts += request
            ByteArray(request.format.bytesForMillis(20)) // Silence is a valid running stream.
        }!!
        assertEquals(1, attempts.size)
        assertEquals("PLAYBACK_POKEMON_GO", opened.first.source)
        assertEquals(2, opened.first.format.channelCount)
    }

    @Test fun `unsupported stereo falls back to mono game capture before microphone`() {
        val attempts = mutableListOf<PcmCaptureRequest>()
        val opened = BattleAudioCapturePlan.start { request ->
            attempts += request
            if (request.format.channelCount == 1) "recording" else null
        }!!
        assertEquals(listOf(2, 1), attempts.map { it.format.channelCount })
        assertTrue(attempts.all { it.source == "PLAYBACK_POKEMON_GO" })
        assertEquals(1, opened.first.format.channelCount)
    }

    @Test fun `unavailable game capture retains explicit mono microphone provenance or reports unavailable`() {
        val attempts = mutableListOf<PcmCaptureRequest>()
        val opened = BattleAudioCapturePlan.start { request ->
            attempts += request
            if (request.source == "MICROPHONE") "recording" else null
        }!!
        assertEquals(3, attempts.size)
        assertEquals("MICROPHONE", opened.first.source)
        assertEquals(1, opened.first.format.channelCount)
        assertNull(BattleAudioCapturePlan.start<String> { null })
    }

    private fun stereo(frames: Int): ByteArray = ByteArray(frames * 4).also { pcm ->
        val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        repeat(frames) { frame ->
            shorts.put((1_000 + frame % 10_000).toShort())
            shorts.put((-500 - frame % 7_000).toShort())
        }
    }
}
