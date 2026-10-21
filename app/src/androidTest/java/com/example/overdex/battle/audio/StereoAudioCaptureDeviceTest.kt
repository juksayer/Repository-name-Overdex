package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic channels exercise preservation/readers; this does not claim game stereo separation. */
class StereoAudioCaptureDeviceTest {
    @Test fun distinctChannelsSurviveCueCutAndArtifactWhileFastMoveReaderKeepsItsMonoResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mono = instrumentation.context.assets.open("fast_move_20261015/17-41-25_36.328.wav")
            .use { requireNotNull(WavPcm16.decode(it.readBytes())) }
        assertEquals(48_000, mono.sampleRateHz)
        assertEquals(1, mono.channelCount)
        assertEquals(57_600, mono.samples.size)
        val format = Pcm16CaptureFormat()
        val raw = ByteArray(format.bytesForMillis(1_200))
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        mono.samples.forEach { value ->
            // Distinct channels whose average is exactly the known recorded fixture.
            val delta = minOf(1_234, Short.MAX_VALUE - value.toInt(), value.toInt() - Short.MIN_VALUE)
            buffer.put((value + delta).toShort())
            buffer.put((value - delta).toShort())
        }
        val original = raw.copyOf()
        val collector = format.cueCollector()
        val input = ByteArray(format.bytesForMillis(500)) + raw
        val readSize = format.bytesForMillis(20)
        for (end in readSize..input.size step readSize) {
            collector.ingest(input.copyOfRange(end - readSize, end),
                end * 1_000_000_000L / format.bytesPerSecond, 100_000L + end * 1_000L / format.bytesPerSecond)
        }
        collector.cue(AudioCaptureCue("stereo-test", BattleCryCueKind.FAST_MOVE_IMPACT, 1_000_015_000L))
        val capture = collector.ingest(ByteArray(readSize), 1_720_000_000L, 101_720L).single() as CueCenteredPcmCapture
        assertArrayEquals(original, capture.pcm16le)
        assertEquals(500_000_000L, capture.startedAtMonotonicNanos)
        val root = File(instrumentation.targetContext.cacheDir, "stereo-test-${System.nanoTime()}")
        try {
            val wav = Pcm16WavEncoder.encode(capture.pcm16le, format.sampleRateHz, format.channelCount)
            val artifact = requireNotNull(FileAudioArtifactStore(root).preserveWav(wav))
            val saved = File(root, artifact.relativePath).readBytes()
            assertArrayEquals(wav, saved)
            val decoded = requireNotNull(WavPcm16.decode(saved))
            assertEquals(2, decoded.channelCount)
            assertArrayEquals(mono.samples, decoded.monoSamples())
            assertTrue(decoded.samples.indices.step(2).any { decoded.samples[it] != decoded.samples[it + 1] })
            val matcher = FastMoveSoundReferences.load(instrumentation.targetContext)
            val expected = matcher.measure(mono)
            val start = System.nanoTime()
            val actual = matcher.measure(decoded)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertEquals(expected, actual)
            assertTrue(FastMoveReferenceMatcher.accepts(actual))
            assertEquals("Spark", actual.first().moveName)
            assertArrayEquals(original, raw)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "Stereo cue: 2 channels, 57600 frames, 1.2s, source start 0.5s; " +
                    "saved bytes unchanged; mono Spark result unchanged; match ${elapsed}ms\n")
            })
        } finally { root.deleteRecursively() }
    }
}
