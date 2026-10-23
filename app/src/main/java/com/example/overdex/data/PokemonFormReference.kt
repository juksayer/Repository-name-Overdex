package com.example.overdex.data

import com.example.overdex.model.GameMasterPokemon

/** Form identity is separate from shiny/gender artwork and the shared Pokédex number. */
object PokemonFormReference {
    fun spriteForm(variant: String): String = variant.split('.')
        .filterNot { it.isBlank() || it == "base" || it == "s" || it.matches(Regex("g\\d+")) }
        .joinToString(".").ifEmpty { "base" }

    fun byName(name: String, catalogue: List<GameMasterPokemon>): GameMasterPokemon? {
        val key = key(name)
        // An exact form must win even if a bare/base alias occurs earlier in the file.
        catalogue.firstOrNull { key(it.speciesName) == key }?.let { return it }
        if ('(' in name) return null
        return catalogue.firstOrNull { key(it.speciesName.substringBefore(" (")) == key }
    }

    fun bySprite(dex: Int, variant: String, catalogue: List<GameMasterPokemon>): GameMasterPokemon? {
        val form = spriteForm(variant)
        val candidates = catalogue.filter { it.dex == dex }
        if (form == "base") return candidates.singleOrNull { '(' !in it.speciesName }
        val label = when (form) {
            "fALOLA" -> "Alolan"
            "fGALARIAN" -> "Galarian"
            "fHISUIAN" -> "Hisuian"
            "fPALDEA" -> "Paldean"
            else -> form.takeIf { it.startsWith('f') }?.drop(1) ?: return null
        }
        // Unknown/costume tokens never fall back to the ordinary form.
        return candidates.singleOrNull {
            key(it.speciesName.substringAfter(" (", "").substringBeforeLast(')')) == key(label)
        }
    }

    /** Bare OCR does not undo an explicit form from this scan; a new visual form can. */
    fun mergeRosterName(previous: String?, incoming: String, explicitSprite: Boolean = false): String =
        if (!explicitSprite && '(' !in incoming && previous != null && '(' in previous &&
            baseKey(previous) == baseKey(incoming)) previous else incoming

    /** Only player-side consumers may use the player's roster to refine a bare badge. */
    fun playerReferenceName(observed: String, roster: Collection<String>): String {
        val candidates = playerReferenceNames(observed, roster)
        return candidates.singleOrNull() ?: observed
    }

    /** A shared badge label cannot pick the ordinary form just because it has no suffix. */
    fun playerReferenceNames(observed: String, roster: Collection<String>): List<String> {
        if ('(' in observed) return listOf(observed)
        return roster.filter { baseKey(it) == baseKey(observed) }.distinctBy(::key)
            .ifEmpty { listOf(observed) }
    }

    private fun baseKey(value: String) = key(value.substringBefore(" ("))
    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
}
