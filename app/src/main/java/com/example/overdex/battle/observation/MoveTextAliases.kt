package com.example.overdex.battle.observation

/** Reference qualifiers are not always printed in Pokémon GO's announcement. */
object MoveTextAliases {
    private val weatherBall = Regex("^Weather Ball \\((Fire|Ice|Normal|Rock|Water)\\)$", RegexOption.IGNORE_CASE)

    fun normalizedNames(referenceName: String): Set<String> = buildSet {
        add(SpeciesOcrTypography.normalizeLabel(referenceName))
        if (weatherBall.matches(referenceName)) add(SpeciesOcrTypography.normalizeLabel("Weather Ball"))
    }

    // Callers must still require a unique match within the performer's legal
    // movepool. A bare Weather Ball must never pick a type from a global list.
    /** A padded button reading may also contain its independent matchup hint. */
    fun buttonLabelText(reading: String): String =
        reading.replace(buttonEffectiveness, "").trim()

    fun matchesButtonLabel(reading: String, referenceName: String): Boolean =
        // A damaged matchup hint on the next line must not invalidate a fully
        // legible move name. Each accepted line still matches a complete alias.
        reading.lineSequence().any { matchesButtonLine(buttonLabelText(it), referenceName) } ||
            matchesButtonLine(buttonLabelText(reading), referenceName)

    private fun matchesButtonLine(reading: String, referenceName: String): Boolean {
        val text = SpeciesOcrTypography.normalizeLabel(reading)
        return normalizedNames(referenceName).any { name ->
            if (text == name) true
            else if (name.length < 6 || text.length != name.length) false
            else {
                // Preserve every character: a cut-off prefix still fails. On
                // real ready-button pixels ML Kit reads FARTH POWER; the lower
                // stroke distinguishing E/F can be lost. Other repairs remain
                // restricted to the existing, explicit lookalike alphabet.
                val changed = name.indices.filter { name[it] != text[it] }
                SpeciesOcrTypography.glyphRepairCost(text, name) != null ||
                    changed.singleOrNull()?.let { setOf(text[it], name[it]) == setOf('E', 'F') } == true
            }
        }
    }

    private val buttonEffectiveness = Regex("(?i)(NOT\\s*VERY\\s*EFFECTIVE|SUPER\\s*EFFECTIVE)[.!…]*")

    fun matches(reading: String, referenceName: String): Boolean =
        SpeciesOcrTypography.normalizeLabel(reading) in normalizedNames(referenceName)
}
