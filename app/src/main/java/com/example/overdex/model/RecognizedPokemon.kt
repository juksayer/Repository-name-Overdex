package com.example.overdex.model

/**
 * Represents the system's current best understanding of a Pokémon specimen,
 * assembled from multiple recognition results.
 * 
 * This model is typically used as an intermediate step between raw OCR output
 * and a finalized [OwnedPokemon] specimen record.
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
