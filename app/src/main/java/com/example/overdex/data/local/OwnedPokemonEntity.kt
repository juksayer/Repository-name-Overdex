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
    createdAt = createdAt,
    updatedAt = updatedAt
)
