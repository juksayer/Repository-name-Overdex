package com.example.overdex.battle.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class FastMoveSoundAnalyzerTest {
    @Test
    fun `measures a distinct sound after silent preroll`() {
        val sampleRate = 16_000
        val samples = ShortArray(sampleRate) { index ->
            if (index !in 8_000 until 11_200) 0
            else (Short.MAX_VALUE * 0.45 * sin(2.0 * Math.PI * 900 * (index - 8_000) / sampleRate)).toInt().toShort()
        }

        val measured = FastMoveSoundAnalyzer.measure(Pcm16Audio(sampleRate, 1, samples))

        assertTrue(measured.audible)
        assertNotNull(measured.onsetOffsetNanos)
        assertNotNull(measured.soundDurationNanos)
        // RMS amplitude is lower than the generated waveform's peak amplitude.
        assertTrue(measured.peakAmplitude > 0.25f)
        assertTrue(requireNotNull(measured.spectralCentroidHz) > 400f)
    }

    @Test
    fun `48 kHz playback preserves onset and duration in real time`() {
        val rate = 48_000
        val samples = ShortArray(rate) { i ->
            if (i !in rate / 2 until rate * 7 / 10) 0
            else (Short.MAX_VALUE * 0.45 * sin(2.0 * Math.PI * 900 * i / rate)).toInt().toShort()
        }
        val result = FastMoveSoundAnalyzer.measure(Pcm16Audio(rate, 1, samples))
        org.junit.Assert.assertEquals(500_000_000L, result.onsetOffsetNanos)
        assertTrue(requireNotNull(result.soundDurationNanos) in 200_000_000L..240_000_000L)
    }

    @Test
    fun `silence is retained as an explicit no-pulse measurement`() {
        val measured = FastMoveSoundAnalyzer.measure(Pcm16Audio(16_000, 1, ShortArray(16_000)))

        assertFalse(measured.audible)
        assertTrue(measured.peakAmplitude == 0f)
    }
}
