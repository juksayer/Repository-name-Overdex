package com.example.overdex.battle.observation

import java.text.Normalizer
import java.util.Locale

/** Vocabulary belongs to this text witness; raw OCR is never repaired in the Timeline. */
internal class ForcedReplacementPhrasePolicy {
    data class Resolution(val basis: String, val evidenceReferences: List<String>)
    private data class Candidate(val phrase: String, val at: Long, val references: List<String>)
    private var candidate: Candidate? = null
    private var latestAt = Long.MIN_VALUE
    private var acceptedAt = Long.MIN_VALUE

    fun observe(text: String, at: Long, references: List<String>): Resolution? {
        if (at <= latestAt) return null
        latestAt = at
        if (acceptedAt != Long.MIN_VALUE && at - acceptedAt < ForcedReplacementCaptureGate.WINDOW_NANOS) return null
        val normalized = normalize(text)
        val exact = PHRASES.firstOrNull { normalized == it }
        if (exact != null) {
            acceptedAt = at; candidate = null
            return Resolution("EXACT_REPLACEMENT_PROMPT", references.distinct())
        }
        // A near-complete phrase needs two independent source frames. Short
        // fragments such as "Pokémon" or "Switch" cannot satisfy this witness.
        val near = PHRASES.singleOrNull { normalized.length >= it.length - 2 && distance(normalized, it) <= 2 }
        val previous = candidate
        candidate = near?.let { Candidate(it, at, references) }
        if (near == null || previous == null || previous.phrase != near ||
            at - previous.at !in 150_000_000L..1_500_000_000L) return null
        acceptedAt = at; candidate = null
        return Resolution("REPEATED_NEAR_COMPLETE_REPLACEMENT_PROMPT", (previous.references + references).distinct())
    }

    companion object {
        private val PHRASES = listOf("switchinanewpokemon", "chooseanotherpokemon")
        private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
            .replace('0', 'o').replace('1', 'i')

        private fun distance(a: String, b: String): Int {
            if (kotlin.math.abs(a.length - b.length) > 2) return 3
            var previous = IntArray(b.length + 1) { it }
            for (i in a.indices) {
                val next = IntArray(b.length + 1); next[0] = i + 1
                for (j in b.indices) next[j + 1] = minOf(next[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1)
                previous = next
            }
            return previous[b.length]
        }
    }
}
