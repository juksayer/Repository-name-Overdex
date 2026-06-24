package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeciesData(
    val species: List<SpeciesImport>
)