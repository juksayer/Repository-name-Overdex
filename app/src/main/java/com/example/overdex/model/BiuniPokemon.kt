package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class BiuniPokemon(
    val id: Int,
    val num: String,
    val name: String,
    val img: String,
    val type: List<String>
)