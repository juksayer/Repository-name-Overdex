package com.example.overdex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.overdex.model.OwnedPokemon

@Entity(tableName = "owned_pokemon")
data class OwnedPokemonEntity(
    @PrimaryKey val id: String,
    val speciesId: Int,
    val displayName: String?,
    val cp: Int?,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isShiny: Boolean,
    val isFavorite: Boolean = false,
    val fastMove: String?,
    val chargedMove1: String?,
    val chargedMove2: String?,
    val createdAt: Long,
    val updatedAt: Long
)

fun OwnedPokemonEntity.toDomain() = OwnedPokemon(
    id = id,
    speciesId = speciesId,
    displayName = displayName,
    cp = cp,
    isShadow = isShadow,
    isPurified = isPurified,
    isShiny = isShiny,
    isFavorite = isFavorite,
    fastMove = fastMove,
    chargedMove1 = chargedMove1,
    chargedMove2 = chargedMove2,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OwnedPokemon.toEntity() = OwnedPokemonEntity(
    id = id,
    speciesId = speciesId,
    displayName = displayName,
    cp = cp,
    isShadow = isShadow,
    isPurified = isPurified,
    isShiny = isShiny,
    isFavorite = isFavorite,
    fastMove = fastMove,
    chargedMove1 = chargedMove1,
    chargedMove2 = chargedMove2,
    createdAt = createdAt,
    updatedAt = updatedAt
)

data class OwnedPokemonWithSpecies(
    @androidx.room.Embedded val owned: OwnedPokemonEntity,
    val speciesName: String?
)
