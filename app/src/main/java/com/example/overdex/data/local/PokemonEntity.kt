package com.example.overdex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val typesJson: String, // Serialized List<PokemonType>
    val region: String,

    val genus: String = "",

    val height: String = "",
    val weight: String = "",

    val prevEvolutionsJson: String = "[]",
    val nextEvolutionsJson: String = "[]",

    val fastMovesJson: String, // Serialized List<Move>
    val chargedMovesJson: String, // Serialized List<Move>
    val spriteUrl: String,
    val cryUrl: String,
    val description: String = "",
    val baseAttack: Int = 0,
    val baseDefense: Int = 0,
    val baseStamina: Int = 0,
)
