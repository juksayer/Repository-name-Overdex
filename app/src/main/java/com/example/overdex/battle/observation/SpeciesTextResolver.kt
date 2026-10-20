package com.example.overdex.battle.observation

internal data class SpeciesTextResolution(val speciesName: String, val confidence: Float, val basis: String)

internal data class SpeciesTextMatch(
    val resolution: SpeciesTextResolution? = null,
    val ambiguous: Boolean = false,
)

/** Extracts a known species name while keeping catalogue repair distinct from exact OCR. */
object SpeciesTextResolver {
    fun resolve(rawText: String, knownSpeciesNames: Set<String>): String? =
        match(rawText, knownSpeciesNames).resolution?.speciesName

    internal fun match(rawText: String, knownSpeciesNames: Set<String>): SpeciesTextMatch {
        val normalizedText = normalize(rawText)
        if (normalizedText.isEmpty()) return SpeciesTextMatch()
        val normalizedNames = knownSpeciesNames
            .map { it to normalize(it) }
            .filter { (_, name) -> name.isNotEmpty() }
        val normalizedLines = rawText.lineSequence()
            .map(::normalize)
            .filter(String::isNotEmpty)
            .toSet()
        normalizedNames
            .filter { (_, name) -> normalizedText == name || name in normalizedLines }
            .maxByOrNull { (_, name) -> name.length }
            ?.let {
                val caseEvidence = SpeciesOcrTypography.caseEvidence(rawText, it.first)
                return SpeciesTextMatch(SpeciesTextResolution(
                    it.first, 0.92f + caseEvidence * 0.06f,
                    if (caseEvidence == 1f) "EXACT_CATALOGUE_TEXT_EXPECTED_CASE" else "EXACT_CATALOGUE_TEXT_CASE_NORMALIZED",
                ))
            }

        // Recover faint/missing ink from the complete label, not an arbitrary
        // substring of unrelated text. CP is the only adjacent badge field we
        // strip; whitespace may itself be the OCR footprint of a missing letter.
        val labels = (rawText.lineSequence() + sequenceOf(rawText))
            .map { normalize(badgeCp.replace(it, "")) }
            .filter { it.length >= 5 }
            .toSet()
        val missingLetterMatches = normalizedNames.mapNotNull { (original, canonical) ->
            labels.mapNotNull { label -> missingLetterCount(label, canonical) }.minOrNull()
                ?.let { original to it }
        }
        if (missingLetterMatches.isNotEmpty()) {
            // Do not prefer fewer missing letters if another legal species fits:
            // PORYGO could mean Porygon, Porygon2 or Porygon-Z.
            val only = missingLetterMatches.singleOrNull() ?: return SpeciesTextMatch(ambiguous = true)
            // Reference names can include a form/sex descriptor that the badge
            // does not spell out. Those visible base labels still compete:
            // NIDORA must not become Nidorina while Nidoran remains possible.
            val base = visibleBaseName(only.first)
            if (knownSpeciesNames.any { name ->
                    val other = visibleBaseName(name)
                    other != base && labels.any { missingLetterCount(it, other) != null }
                }) return SpeciesTextMatch(ambiguous = true)
            return SpeciesTextMatch(SpeciesTextResolution(
                only.first, if (only.second == 1) 0.82f else 0.76f,
                "UNAMBIGUOUS_CATALOGUE_MISSING_LETTERS_${only.second}",
            ))
        }

        // Prefer explicit compact-badge glyph confusions over arbitrary
        // substitutions. The winner must remain unambiguous.
        val candidates = normalizedNames.mapNotNull { (original, normalizedName) ->
            // A clean prefix of a longer word is not evidence for a very short
            // species. For example, MEWTO is a damaged MEWTWO reading, not MEW.
            if (normalizedName.length <= 4 && normalizedLines.none {
                    kotlin.math.abs(it.length - normalizedName.length) <= 1
                }
            ) return@mapNotNull null
            val allowedEdits = SpeciesOcrTypography.allowedCost(normalizedName.length)
            val distance = closestWindowDistance(normalizedText, normalizedName)
            original.takeIf { distance <= allowedEdits }?.let { it to distance }
        }
        val bestDistance = candidates.minOfOrNull { it.second } ?: return SpeciesTextMatch()
        val best = candidates.filter { it.second == bestDistance }
        val only = best.singleOrNull() ?: return SpeciesTextMatch(ambiguous = true)
        return SpeciesTextMatch(SpeciesTextResolution(only.first, 0.82f, "UNAMBIGUOUS_CATALOGUE_FUZZY_MATCH"))
    }

    private fun missingLetterCount(label: String, canonical: String): Int? {
        val missing = canonical.length - label.length
        if (missing !in 1..2 || label.length * 4 < canonical.length * 3) return null
        var visible = 0
        for (letter in canonical) {
            if (visible < label.length && label[visible] == letter) visible++
        }
        return missing.takeIf { visible == label.length }
    }

    private val badgeCp = Regex("(?i)(?<![A-Z0-9])CP\\s*[0-9]{1,5}(?![A-Z0-9])")

    private fun visibleBaseName(name: String): String {
        val base = name.substringBefore(" (")
        return normalize(when {
            base.equals("Nidoran Female", true) || base.equals("Nidoran Male", true) -> "Nidoran"
            else -> base
        })
    }

    private fun closestWindowDistance(text: String, candidate: String): Int {
        if (text.length < candidate.length) return SpeciesOcrTypography.weightedDistance(text, candidate)
        return (candidate.length - 2..candidate.length + 2)
            .filter { it > 0 && it <= text.length }
            .flatMap { width -> (0..text.length - width).map { start -> text.substring(start, start + width) } }
            .minOf { window -> SpeciesOcrTypography.weightedDistance(window, candidate) }
    }

    private fun normalize(value: String): String =
        SpeciesOcrTypography.normalizeLabel(value)
}
