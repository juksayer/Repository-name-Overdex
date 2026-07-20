package com.example.overdex.data

import androidx.paging.PagingSource
import com.example.overdex.data.local.PokemonDao
import com.example.overdex.data.local.PokemonEntity
import com.example.overdex.model.PokemonType

/**
 * Provides paged search results for the Pokémon collection.
 */
class PokemonSearchRepository(
    private val pokemonDao: PokemonDao
) {

    /**
     * Executes a search query with optional type filtering.
     * 
     * @return A [PagingSource] for use with the Paging 3 library.
     */
    fun search(
        query: String,
        type: PokemonType?
    ): PagingSource<Int, PokemonEntity> {

        return when {
            type != null ->
                pokemonDao.getPokemonByType("%${type.name}%")

            query.isBlank() ->
                pokemonDao.getAllPokemon()

            else ->
                pokemonDao.searchPokemon("%$query%")
        }
    }
}