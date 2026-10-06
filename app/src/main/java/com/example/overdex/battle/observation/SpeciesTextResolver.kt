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

        // Prefer explicit compact-badge glyph confusions over arbitrary
        // substitutions. The winner must remain unambiguous.
        val candidates = normalizedNames.mapNotNull { (original, normalizedName) ->
            val allowedEdits = SpeciesOcrTypography.allowedCost(normalizedName.length)
            val distance = closestWindowDistance(normalizedText, normalizedName)
            original.takeIf { distance <= allowedEdits }?.let { it to distance }
        }
        val bestDistance = candidates.minOfOrNull { it.second } ?: return null
        val best = candidates.filter { it.second == bestDistance }
        return best.singleOrNull()?.first
    }

    private fun closestWindowDistance(text: String, candidate: String): Int {
        if (text.length < candidate.length) return SpeciesOcrTypography.weightedDistance(text, candidate)
        return (candidate.length - 2..candidate.length + 2)
            .filter { it > 0 && it <= text.length }
            .flatMap { width -> (0..text.length - width).map { start -> text.substring(start, start + width) } }
            .minOf { window -> SpeciesOcrTypography.weightedDistance(window, candidate) }
    }

    private fun normalize(value: String): String =
        value.uppercase().filter(Char::isLetterOrDigit)
}
