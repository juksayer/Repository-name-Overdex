package com.example.overdex.model

import java.util.UUID

/**
 * Represents a specific Pokémon specimen owned by a trainer.
 * 
 * Unlike the species-level [Pokemon] model, [OwnedPokemon] contains individual
 * stats, moves, and customizations for a specific instance.
 * 
 * @property id Unique identifier for the specimen.
 * @property speciesId The Pokédex ID of the specimen's species.
 * @property cp The combat power of the specimen.
 * @property isShadow True if the specimen is in shadow form.
 * @property isPurified True if the specimen has been purified.
 * @property isShiny True if the specimen is a shiny variant.
 * @property fastMove The name of the specimen's current fast move.
 * @property chargedMove1 The name of the specimen's first charged move.
 * @property chargedMove2 The name of the specimen's second charged move (if unlocked).
 */
data class OwnedPokemon(
    val id: String = UUID.randomUUID().toString(),
    val speciesId: Int,
    val displayName: String? = null,
    val cp: Int? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isShiny: Boolean = false,
    val isFavorite: Boolean = false,
    val fastMove: String? = null,
    val chargedMove1: String? = null,
    val chargedMove2: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
