package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class PokemonImportList(
    val pokemon: List<PokemonImport>
)