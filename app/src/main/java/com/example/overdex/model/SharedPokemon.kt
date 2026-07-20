package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * A lightweight representation of a Pokémon specimen for sharing over the chat transport.
 */
@Serializable
data class SharedPokemon(
    val speciesId: Int,
    val speciesName: String,
    val cp: Int?,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isShiny: Boolean,
    val primaryType: String,
    val secondaryType: String?
)
