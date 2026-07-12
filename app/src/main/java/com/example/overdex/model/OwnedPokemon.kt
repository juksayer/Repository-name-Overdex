package com.example.overdex.model

import java.util.UUID

data class OwnedPokemon(
    val id: String = UUID.randomUUID().toString(),
    val speciesId: Int,
    val displayName: String? = null,
    val cp: Int? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isShiny: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
