package com.example.overdex.battle.audio

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BattleSoundFeaturesTest {
    @Test fun `overlapping PCM reuses only byte-identical spectral windows`() {
        val random = Random(43)
        val samples = ShortArray(4_096) { random.nextInt(-30_000, 30_000).toShort() }
        val first = BattleSoundFeatures.spectra(samples)
        val before = BattleSoundFeatures.stats()
        val overlapping = BattleSoundFeatures.spectra(samples.copyOfRange(128, samples.size))
        val after = BattleSoundFeatures.stats()
        assertEquals(before.computed, after.computed)
        assertEquals(overlapping.size.toLong(), after.reused - before.reused)
        overlapping.forEachIndexed { i, frame -> assertArrayEquals(first[i + 1], frame, 0f) }
        val changed = samples.copyOf().also { it[0] = (it[0] + 100).toShort() }
        BattleSoundFeatures.spectra(changed)
        assertEquals(after.computed + 1, BattleSoundFeatures.stats().computed)
        // A consumer's normalization never mutates the shared raw spectrum.
        val copy = first.first().copyOf()
        BattleSoundFeatures.normalizedCopy(first.first())
        assertArrayEquals(copy, first.first(), 0f)
    }

    @Test fun `shared spectra preserve the original spectral feature definition`() {
        val samples = ShortArray(256) { (12_000 * sin(it * .73) + 4_000 * cos(it * .19)).toInt().toShort() }
        val spectrum = BattleSoundFeatures.spectra(samples).single()
        for (bin in spectrum.indices) {
            var real = 0.0; var imaginary = 0.0
            for (i in samples.indices) {
                val input = samples[i] / 32768.0 * (.5 - .5 * cos(2 * PI * i / 255))
                val angle = 2 * PI * (bin * 2 + 1) * i / 256
                real += input * cos(angle); imaginary -= input * sin(angle)
            }
            val expected = sqrt(sqrt(real * real + imaginary * imaginary))
            assertEquals(expected, spectrum[bin].toDouble(), .002)
        }
    }

    @Test fun `centroid optimization matches direct DFT for short long and noisy pulses`() {
        val random = Random(19)
        for (size in listOf(16, 80, 239, 256, 512)) {
            val samples = ShortArray(size) { random.nextInt(-20_000, 20_000).toShort() }
            var weighted = 0.0; var total = 0.0
            for (bin in 1..size / 2) {
                var real = 0.0; var imaginary = 0.0
                samples.forEachIndexed { i, value ->
                    val angle = 2 * PI * bin * i / size
                    real += value * cos(angle); imaginary -= value * sin(angle)
                }
                val magnitude = sqrt(real * real + imaginary * imaginary)
                weighted += magnitude * bin * 8_000 / size; total += magnitude
            }
            assertEquals((weighted / total).toFloat(), FastMoveSoundAnalyzer.spectralCentroid(samples, 0, size, 8_000), .01f)
        }
    }
}
