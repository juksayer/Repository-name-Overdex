package com.example.overdex.model

data class SearchRequest(
    val text: String = "",
    val type: PokemonType? = null
)