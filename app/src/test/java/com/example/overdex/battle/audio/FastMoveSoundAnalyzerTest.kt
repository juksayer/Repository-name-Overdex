package com.example.overdex.battle.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class FastMoveSoundAnalyzerTest {
    @Test fun `a loud preexisting sound does not set an impossible threshold for a later pulse`() {
        val samples = ShortArray(8_000) { i ->
            val level = when (i) { in 0..1599 -> .7; in 4000..5599 -> .45; else -> .08 }
            (32767 * level * sin(2 * Math.PI * 900 * i / 8000)).toInt().toShort()
        }
        val result = FastMoveSoundAnalyzer.measure(Pcm16Audio(8_000, 1, samples))
        assertTrue(result.audible)
        org.junit.Assert.assertEquals(500_000_000L, result.onsetOffsetNanos)
    }

    @Test fun `steady sound has no pulse and stereo timing is not doubled`() {
        val constant = ShortArray(8_000) { i -> (12_000 * sin(2 * Math.PI * 900 * i / 8000)).toInt().toShort() }
        assertFalse(FastMoveSoundAnalyzer.measure(Pcm16Audio(8_000, 1, constant)).audible)
        val stereo = ShortArray(16_000) { i -> if (i / 2 in 4000..5599) constant[i / 2] else 0 }
        val result = FastMoveSoundAnalyzer.measure(Pcm16Audio(8_000, 2, stereo))
        org.junit.Assert.assertEquals(500_000_000L, result.onsetOffsetNanos)
    }

    @Test
    fun `early sound cue keeps the attacker opposite the damaged bar`() {
        val player = com.example.overdex.battle.custody.ActivePokemonSide.PLAYER
        val opponent = com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT
        org.junit.Assert.assertEquals(opponent, fastMoveCueAttackingSide(
            com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured(player, 1f, .9f, .1f)))
        org.junit.Assert.assertEquals(player, fastMoveCueAttackingSide(
            com.example.overdex.battle.custody.HpBarBorderPulse(opponent, com.example.overdex.battle.custody.ApertureStatus.PRESENT, .4f, 8)))
        org.junit.Assert.assertNull(fastMoveCueAttackingSide(
            com.example.overdex.battle.custody.HpBarBorderPulse(player, com.example.overdex.battle.custody.ApertureStatus.ABSENT)))
    }

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
