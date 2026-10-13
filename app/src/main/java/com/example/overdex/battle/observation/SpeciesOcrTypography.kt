package com.example.overdex.battle.observation

/** Pokémon GO species-label typography and known OCR glyph confusions. */
internal object SpeciesOcrTypography {
    private val confusionGroups = listOf(
        setOf('O', '0', 'Q'),
        setOf('I', '1', 'L', '|', '!'),
        setOf('S', '5', 'Z', '$'),
        setOf('A', '4', '@'),
        setOf('B', '8'),
        setOf('G', '6'),
        setOf('Z', '2'),
        setOf('E', '3', '€')
    )

    /**
     * Only for species/move vocabulary matching, never nickname storage. Keep
     * suspicious glyphs in the comparison: silently deleting them could turn
     * a damaged reading into a falsely exact legal name. Formatting punctuation
     * is ignored equally in the reading and reference name.
     */
    fun normalizeLabel(value: String): String = value.trim().trimEnd('!', '.', '?')
        .uppercase().filterNot { it.isWhitespace() || it in "-.,:'’()♀♂" }

    /** At most two familiar lookalikes, with no missing letters or arbitrary edits. */
    fun glyphRepairCost(reading: String, canonical: String): Int? {
        if (reading.length != canonical.length) return null
        var differences = 0
        for (index in reading.indices) {
            if (reading[index] == canonical[index]) continue
            if (substitutionCost(reading[index], canonical[index]) != CONFUSION_COST) return null
            differences++
        }
        return differences.takeIf { it in 1..2 }
    }

    fun weightedDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it * ORDINARY_EDIT_COST }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = (i + 1) * ORDINARY_EDIT_COST
            for (j in right.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + ORDINARY_EDIT_COST,
                    current[j] + ORDINARY_EDIT_COST,
                    previous[j] + substitutionCost(left[i], right[j])
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    fun allowedCost(speciesLength: Int): Int = when {
        speciesLength >= 10 -> 4
        speciesLength >= 6 -> 2
        else -> 0
    }

    /** Case is supporting evidence only; OCR often returns an all-caps line. */
    fun caseEvidence(rawText: String, canonicalSpeciesName: String): Float {
        val exact = Regex("(?<![A-Za-z0-9])${Regex.escape(canonicalSpeciesName)}(?![A-Za-z0-9])")
        if (exact.containsMatchIn(rawText)) return 1f
        val caseInsensitive = Regex(
            "(?<![A-Za-z0-9])${Regex.escape(canonicalSpeciesName)}(?![A-Za-z0-9])",
            RegexOption.IGNORE_CASE
        )
        return if (caseInsensitive.containsMatchIn(rawText)) 0.72f else 0.45f
    }

    private fun substitutionCost(left: Char, right: Char): Int {
        if (left == right) return 0
        return if (confusionGroups.any { left in it && right in it }) CONFUSION_COST else ORDINARY_EDIT_COST
    }

    private const val CONFUSION_COST = 1
    private const val ORDINARY_EDIT_COST = 3
}
