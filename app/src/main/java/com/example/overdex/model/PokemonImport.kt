package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class PokemonImport(
    val id: Int,
    val num: String,
    val name: String,
    val img: String,
    val type: List<String>,
    val height: String? = null,
    val weight: String? = null,
)