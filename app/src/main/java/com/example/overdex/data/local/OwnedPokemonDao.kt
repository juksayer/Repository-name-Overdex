package com.example.overdex.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedPokemonDao {
    @Query("SELECT * FROM owned_pokemon ORDER BY createdAt DESC")
    fun getAllOwnedPokemon(): Flow<List<OwnedPokemonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addOwnedPokemon(owned: OwnedPokemonEntity)

    @Update
    suspend fun updateOwnedPokemon(owned: OwnedPokemonEntity)

    @Query("SELECT * FROM owned_pokemon WHERE id = :id LIMIT 1")
    fun getOwnedPokemonById(id: String): Flow<OwnedPokemonEntity?>

    @Query("DELETE FROM owned_pokemon WHERE id = :id")
    suspend fun deleteOwnedPokemon(id: String)
}
