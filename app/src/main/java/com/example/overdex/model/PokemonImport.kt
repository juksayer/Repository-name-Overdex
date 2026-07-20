package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Data model used for importing species from a JSON source.
 */
@Serializable
data class PokemonImport(
    val id: Int,
    val num: String,
    val name: String,
    val img: String,
    val type: List<String>,
    val height: String? = null,
    val weight: String? = null,
    val prev_evolution: List<EvolutionImport>? = null,
    val next_evolution: List<EvolutionImport>? = null,
)

/**
 * Nested evolution data within an import source.
 */
@Serializable
data class EvolutionImport(
    val num: String,
    val name: String
)
