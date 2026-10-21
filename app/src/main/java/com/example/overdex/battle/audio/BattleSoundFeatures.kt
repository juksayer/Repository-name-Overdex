package com.example.overdex.battle.audio

import kotlin.math.*

/** Shared 8 kHz spectral windows. Cached values are immutable; callers normalize copies. */
internal object BattleSoundFeatures {
    const val SIZE = 256
    const val HOP = 128
    private const val CAPACITY = 2_048 // Less than 2 MiB of sample/spectrum arrays, independent of match length.
    private val window = FloatArray(SIZE) { (.5 - .5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
    private val coefficients = FloatArray(64) { (2 * cos(2 * PI * (it * 2 + 1) / SIZE)).toFloat() }
    private data class Entry(val samples: ShortArray, val spectrum: FloatArray)
    private val cache = object : LinkedHashMap<Long, Entry>(CAPACITY, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?) = size > CAPACITY
    }
    private var computed = 0L
    private var reused = 0L
    data class Stats(val computed: Long, val reused: Long, val retained: Int)
    fun stats(): Stats = synchronized(cache) { Stats(computed, reused, cache.size) }

    fun spectra(samples: ShortArray): List<FloatArray> {
        if (samples.size < SIZE) return emptyList()
        return (0..samples.size - SIZE step HOP).map { start ->
            var hash = -3750763034362895579L
            for (i in 0 until SIZE) hash = (hash xor samples[start + i].toLong()) * 1099511628211L
            val saved = synchronized(cache) {
                // Hash is only an index: compare every sample, so collisions cannot create evidence.
                cache[hash]?.takeIf { entry -> entry.samples.indices.all { entry.samples[it] == samples[start + it] } }
                    ?.also { reused++ }
            }
            if (saved != null) saved.spectrum else {
                val input = FloatArray(SIZE) { samples[start + it] / 32768f * window[it] }
                val spectrum = FloatArray(coefficients.size) { bin ->
                    val coefficient = coefficients[bin]
                    var previous = 0f; var older = 0f
                    for (sample in input) {
                        val next = sample + coefficient * previous - older
                        older = previous; previous = next
                    }
                    sqrt(sqrt(max(0f, previous * previous + older * older - coefficient * previous * older)))
                }
                synchronized(cache) {
                    cache[hash] = Entry(samples.copyOfRange(start, start + SIZE), spectrum)
                    computed++
                }
                spectrum
            }
        }
    }

    fun normalizedCopy(spectrum: FloatArray): FloatArray = spectrum.copyOf().also { values ->
        val norm = sqrt(values.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
        for (bin in values.indices) values[bin] /= norm
    }
}
