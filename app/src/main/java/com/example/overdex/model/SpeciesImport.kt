package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Data model for importing species-specific metadata (like genus and flavor text).
 */
@Serializable
data class SpeciesImport(
    val id: Int,
    val genus: String,
    val flavor_text: String
)
