package com.example.overdex.model

/**
 * Represents a specific Pokémon in an evolution chain.
 * 
 * @property num The formatted Pokédex number (e.g., "006").
 * @property name The species name.
 */
data class Evolution(
    val num: String,
    val name: String
)
