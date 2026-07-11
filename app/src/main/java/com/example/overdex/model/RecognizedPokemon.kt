package com.example.overdex.model

/**
 * Represents the current best understanding of a Pokémon
 * assembled from multiple recognition results.
 */
data class RecognizedPokemon(
    val species: String? = null,
    val family: List<String> = emptyList(),
    val cp: Int? = null,
    val fastMove: String? = null,
    val chargedMoveA: String? = null,
    val chargedMoveB: String? = null,
    val shadowBonus: Int? = null
)
