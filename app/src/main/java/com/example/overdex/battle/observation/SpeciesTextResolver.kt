package com.example.overdex.battle.observation

/** Extracts a known species name from noisy multi-line OCR without inventing a match. */
object SpeciesTextResolver {
    fun resolve(rawText: String, knownSpeciesNames: Set<String>): String? {
        val normalizedText = normalize(rawText)
        if (normalizedText.isEmpty()) return null
        val normalizedNames = knownSpeciesNames
            .map { it to normalize(it) }
            .filter { (_, name) -> name.isNotEmpty() }
        normalizedNames
            .filter { (_, name) -> normalizedText.contains(name) }
            .maxByOrNull { (_, name) -> name.length }
            ?.let { return it.first }

        // OCR commonly confuses one glyph (for example O/0) in the compact
        // Pokémon GO badges. Accept one small edit only when it identifies one
        // unambiguous known species; never manufacture a weak match.
        val candidates = normalizedNames.mapNotNull { (original, normalizedName) ->
            val allowedEdits = when {
                normalizedName.length >= 10 -> 2
                normalizedName.length >= 6 -> 1
                else -> 0
            }
            val distance = closestWindowDistance(normalizedText, normalizedName)
            original.takeIf { distance <= allowedEdits }?.let { it to distance }
        }
        val bestDistance = candidates.minOfOrNull { it.second } ?: return null
        val best = candidates.filter { it.second == bestDistance }
        return best.singleOrNull()?.first
    }

    private fun closestWindowDistance(text: String, candidate: String): Int {
        if (text.length < candidate.length) return editDistance(text, candidate)
        return (candidate.length - 2..candidate.length + 2)
            .filter { it > 0 && it <= text.length }
            .flatMap { width -> (0..text.length - width).map { start -> text.substring(start, start + width) } }
            .minOf { window -> editDistance(window, candidate) }
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun normalize(value: String): String =
        value.uppercase().filter(Char::isLetterOrDigit)
}
