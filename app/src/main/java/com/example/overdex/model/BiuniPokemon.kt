package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * A legacy or specific-format data model for importing Pokémon data.
 */
@Serializable
data class BiuniPokemon(
    val id: Int,
    val num: String,
    val name: String,
    val img: String,
    val type: List<String>
)
