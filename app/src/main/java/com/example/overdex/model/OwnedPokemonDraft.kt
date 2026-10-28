package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** One unsaved individual. The ID survives edits and retries; binders share the saved card. */
@Serializable
data class OwnedPokemonDraft(
    val id: String = UUID.randomUUID().toString(),
    val speciesId: Int? = null,
    val speciesName: String = "",
    val nickname: String = "",
    val cp: String = "",
    val fastMove: String? = null,
    val chargedMove1: String? = null,
    val chargedMove2: String? = null,
    val shadow: Boolean = false,
    val purified: Boolean = false,
    val shiny: Boolean = false,
    val size: PokemonSize = PokemonSize.UNKNOWN,
    val teams: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun selectSpecies(pokemon: Pokemon): OwnedPokemonDraft = if (speciesId == pokemon.id && speciesName == pokemon.name) this else copy(
        speciesId = pokemon.id, speciesName = pokemon.name,
        fastMove = null, chargedMove1 = null, chargedMove2 = null,
    )

    val validationError: String? get() = when {
        speciesId == null -> "Choose a species first."
        cp.isNotBlank() && (cp.toIntOrNull() ?: 0) <= 0 -> "Enter a positive CP, or leave it blank."
        else -> null
    }

    fun toOwnedPokemon(): OwnedPokemon {
        require(validationError == null) { validationError.orEmpty() }
        return OwnedPokemon(
            id = id, speciesId = requireNotNull(speciesId), displayName = nickname.trim().ifEmpty { null },
            cp = cp.toIntOrNull(), fastMove = fastMove, chargedMove1 = chargedMove1,
            chargedMove2 = chargedMove2, isShadow = shadow, isPurified = purified, isShiny = shiny,
            size = size, teams = OwnedPokemonTeams.parse(teams), createdAt = createdAt,
            speciesName = speciesName.trim().ifEmpty { null },
        )
    }
}

enum class OwnedPokemonField(val label: String) {
    SPECIES("Species"), NICKNAME("Nickname"), CP("CP"), FAST_MOVE("Fast Move"),
    CHARGED_MOVE_1("Charged Move 1"), CHARGED_MOVE_2("Charged Move 2"),
    SHADOW("Shadow"), PURIFIED("Purified"), SHINY("Shiny"), SIZE("Size"), TEAMS("Teams"),
    SAVE("Save Card"), CANCEL("Cancel Card");

    fun value(draft: OwnedPokemonDraft): String? = when (this) {
        SPECIES -> draft.speciesName.ifEmpty { "Choose species" }
        NICKNAME -> draft.nickname.ifBlank { "Optional" }
        CP -> draft.cp.ifBlank { "Not recorded" }
        FAST_MOVE -> draft.fastMove ?: "Not recorded"
        CHARGED_MOVE_1 -> draft.chargedMove1 ?: "Not recorded"
        CHARGED_MOVE_2 -> draft.chargedMove2 ?: "Not recorded"
        SHADOW -> if (draft.shadow) "Yes" else "No"
        PURIFIED -> if (draft.purified) "Yes" else "No"
        SHINY -> if (draft.shiny) "Yes" else "No"
        SIZE -> draft.size.label
        TEAMS -> draft.teams.ifBlank { "Optional · separate names with commas" }
        SAVE, CANCEL -> null
    }
}
