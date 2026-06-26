package com.example.overdex.data

import androidx.paging.PagingSource
import com.example.overdex.data.local.PokemonDao
import com.example.overdex.data.local.PokemonEntity
import com.example.overdex.model.PokemonType

class PokemonSearchRepository(
    private val pokemonDao: PokemonDao
) {

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