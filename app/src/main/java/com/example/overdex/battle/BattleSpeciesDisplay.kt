package com.example.overdex.battle

/** Catalogue size variants are reference keys, not a witnessed Pokémon GO size label. */
object BattleSpeciesDisplay {
    fun name(speciesName: String): String =
        SIZE_VARIANT.matchEntire(speciesName.trim())?.groupValues?.get(1) ?: speciesName

    // Preserve regional, costume and other explicit forms. Specimen size stays
    // in its separate user-supplied field; it is never inferred from this name.
    private val SIZE_VARIANT = Regex(
        "^(Gourgeist|Pumpkaboo)\\s+\\((Small|Average|Large|Super)\\)$", RegexOption.IGNORE_CASE)
}
