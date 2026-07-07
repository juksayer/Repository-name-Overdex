package com.example.overdex.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedPokemonDao {
    @Query("""
        SELECT owned.*, pokemon.name as speciesName 
        FROM owned_pokemon as owned
        LEFT JOIN pokemon ON owned.speciesId = pokemon.id
        ORDER BY owned.createdAt DESC
    """)
    fun getAllOwnedPokemonWithSpecies(): Flow<List<OwnedPokemonWithSpecies>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addOwnedPokemon(owned: OwnedPokemonEntity)

    @Update
    suspend fun updateOwnedPokemon(owned: OwnedPokemonEntity)

    @Query("SELECT * FROM owned_pokemon WHERE id = :id LIMIT 1")
    fun getOwnedPokemonById(id: String): Flow<OwnedPokemonEntity?>

    @Query("DELETE FROM owned_pokemon WHERE id = :id")
    suspend fun deleteOwnedPokemon(id: String)
}
