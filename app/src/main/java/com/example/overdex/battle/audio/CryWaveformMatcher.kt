package com.example.overdex.battle.audio

import kotlin.math.*

internal data class CryWaveformReference(val speciesId: Int, val sha256: String, val audio: Pcm16Audio)
internal data class CryWaveformFit(
    val speciesId: Int, val referenceSha256: String, val correlation: Float,
    val offsetNanos: Long, val durationNanos: Long,
)

/**
 * Localizes a complete reference waveform in bounded PCM. The caller supplies a small relevant
 * reference pool; no full-library fallback. Correlation is a measurement, not faint/side evidence.
 * Single-worker use only. One transform size per reference is cached, bounded by MAX_REFERENCES.
 */
internal class CryWaveformMatcher(references: List<CryWaveformReference>) {
    private data class Spectrum(val real: DoubleArray, val imaginary: DoubleArray)
    private class Prepared(val reference: CryWaveformReference, val samples: DoubleArray, val energy: Double) {
        var spectrum: Spectrum? = null
    }
    init { require(references.size <= MAX_REFERENCES) }
    private val prepared = references.distinctBy { it.speciesId to it.sha256 }.mapNotNull { ref ->
        val mono = StatChangeSoundMatcher.mono8k(ref.audio)
        if (mono.size !in MIN_REFERENCE_SAMPLES..MAX_QUERY_SAMPLES || ref.speciesId <= 0 || ref.sha256.isBlank()) null
        else {
            val mean = mono.map { it.toDouble() }.average()
            val samples = DoubleArray(mono.size) { mono[it] - mean }
            val energy = samples.sumOf { it * it }
            if (energy <= 0.0) null else Prepared(ref, samples, energy)
        }
    }

    fun measure(audio: Pcm16Audio): List<CryWaveformFit> = measureMono8k(StatChangeSoundMatcher.mono8k(audio))

    fun measureMono8k(query: ShortArray): List<CryWaveformFit> {
        require(query.size <= MAX_QUERY_SAMPLES)
        val eligible = prepared.filter { it.samples.size <= query.size }
        if (eligible.isEmpty() || query.isEmpty()) return emptyList()
        var size = 1
        while (size < query.size + eligible.maxOf { it.samples.size } - 1) size *= 2
        val real = DoubleArray(size) { if (it < query.size) query[it].toDouble() else 0.0 }
        val imaginary = DoubleArray(size)
        transform(real, imaginary, inverse = false)
        val sums = DoubleArray(query.size + 1)
        val squares = DoubleArray(query.size + 1)
        for (i in query.indices) {
            sums[i + 1] = sums[i] + query[i]
            squares[i + 1] = squares[i] + query[i].toDouble() * query[i]
        }
        // Reuse convolution scratch across references: allocating two full FFT arrays for
        // each species at four scans/second otherwise creates avoidable phone GC pressure.
        val productR = DoubleArray(size)
        val productI = DoubleArray(size)
        return eligible.map { ref ->
            val n = ref.samples.size
            val spectrum = ref.spectrum?.takeIf { it.real.size == size } ?: run {
                val r = DoubleArray(size) { if (it < n) ref.samples[n - 1 - it] else 0.0 }
                val im = DoubleArray(size)
                transform(r, im, inverse = false)
                Spectrum(r, im).also { ref.spectrum = it }
            }
            for (i in 0 until size) {
                productR[i] = real[i] * spectrum.real[i] - imaginary[i] * spectrum.imaginary[i]
                productI[i] = real[i] * spectrum.imaginary[i] + imaginary[i] * spectrum.real[i]
            }
            transform(productR, productI, inverse = true)
            var best = 0.0
            var offset = 0
            for (i in 0..query.size - n) {
                val sum = sums[i + n] - sums[i]
                val variance = (squares[i + n] - squares[i] - sum * sum / n).coerceAtLeast(0.0)
                if (variance <= 0.0) continue
                val correlation = (abs(productR[i + n - 1]) / sqrt(variance * ref.energy)).coerceIn(0.0, 1.0)
                if (correlation > best) { best = correlation; offset = i }
            }
            CryWaveformFit(ref.reference.speciesId, ref.reference.sha256, best.toFloat(),
                offset.toLong() * 1_000_000_000L / RATE, n.toLong() * 1_000_000_000L / RATE)
        }.sortedWith(compareByDescending<CryWaveformFit> { it.correlation }.thenBy { it.speciesId }.thenBy { it.referenceSha256 })
    }

    companion object {
        const val RATE = 8_000
        const val MAX_QUERY_SAMPLES = RATE * 4
        const val MAX_REFERENCES = 32
        private const val MIN_REFERENCE_SAMPLES = RATE / 10

        /** In-place radix-two FFT; inverse includes normalization. */
        private fun transform(real: DoubleArray, imaginary: DoubleArray, inverse: Boolean) {
            val n = real.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val r = real[i]; real[i] = real[j]; real[j] = r
                    val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
                }
            }
            var length = 2
            while (length <= n) {
                val angle = (if (inverse) 2 else -2) * PI / length
                val stepR = cos(angle); val stepI = sin(angle)
                var start = 0
                while (start < n) {
                    var wR = 1.0; var wI = 0.0
                    for (k in 0 until length / 2) {
                        val a = start + k; val b = a + length / 2
                        val r = real[b] * wR - imaginary[b] * wI
                        val im = real[b] * wI + imaginary[b] * wR
                        real[b] = real[a] - r; imaginary[b] = imaginary[a] - im
                        real[a] += r; imaginary[a] += im
                        val nextR = wR * stepR - wI * stepI
                        wI = wR * stepI + wI * stepR; wR = nextR
                    }
                    start += length
                }
                length *= 2
            }
            if (inverse) for (i in 0 until n) { real[i] /= n; imaginary[i] /= n }
        }
    }
}
