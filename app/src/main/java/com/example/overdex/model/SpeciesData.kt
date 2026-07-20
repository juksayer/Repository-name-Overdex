package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Root container for a list of imported species data.
 */
@Serializable
data class SpeciesData(
    val species: List<SpeciesImport>
)
