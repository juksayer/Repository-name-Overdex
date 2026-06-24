package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeciesImport(
    val id: Int,
    val genus: String,
    val flavor_text: String
)