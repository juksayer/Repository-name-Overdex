package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Container for a list of [PokemonImport] objects, typically deserialized from a JSON file.
 */
@Serializable
data class PokemonImportList(
    val pokemon: List<PokemonImport>
)
