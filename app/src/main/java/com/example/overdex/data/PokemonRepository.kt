package com.example.overdex.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.overdex.data.local.PokemonDao
import com.example.overdex.data.local.PokemonEntity
import com.example.overdex.model.Evolution
import com.example.overdex.model.EvolutionImport
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json

class PokemonRepository(
    private val pokemonDao: PokemonDao
) {

    private val searchRepository = PokemonSearchRepository(pokemonDao)

    suspend fun getPokemonById(id: Int): Pokemon? {
        return pokemonDao.getPokemonById(id)?.toDomain()
    }

    suspend fun getPokemonByName(name: String): Pokemon? {
        return pokemonDao.getPokemonByName(name)?.toDomain()
    }

    fun search(
        query: String,
        type: PokemonType?
    ): PagingSource<Int, Pokemon> {
        val source = searchRepository.search(query, type)

        return object : PagingSource<Int, Pokemon>() {

            override suspend fun load(
                params: LoadParams<Int>
            ): LoadResult<Int, Pokemon> {
                return when (val result = source.load(params)) {
                    is PagingSource.LoadResult.Page -> {
                        PagingSource.LoadResult.Page(
                            data = result.data.map { it.toDomain() },
                            prevKey = result.prevKey,
                            nextKey = result.nextKey,
                            itemsBefore = result.itemsBefore,
                            itemsAfter = result.itemsAfter
                        )
                    }

                    is PagingSource.LoadResult.Error -> {
                        PagingSource.LoadResult.Error(result.throwable)
                    }

                    is PagingSource.LoadResult.Invalid -> {
                        PagingSource.LoadResult.Invalid()
                    }
                }
            }

            override fun getRefreshKey(
                state: PagingState<Int, Pokemon>
            ): Int? {
                return null
            }
        }
    }
    private fun PokemonEntity.toDomain(): Pokemon {
        val types = try {
            Json.decodeFromString<List<PokemonType>>(typesJson)
        } catch (e: Exception) {
            emptyList()
        }

        val fastMoves = try {
            Json.decodeFromString<List<Move>>(fastMovesJson)
        } catch (e: Exception) {
            emptyList()
        }

        val chargedMoves = try {
            Json.decodeFromString<List<Move>>(chargedMovesJson)
        } catch (e: Exception) {
            emptyList()
        }

        val prevEvolutions = try {
            Json.decodeFromString<List<EvolutionImport>>(prevEvolutionsJson)
                .map { Evolution(it.num, it.name) }
        } catch (e: Exception) {
            emptyList()
        }

        val nextEvolutions = try {
            Json.decodeFromString<List<EvolutionImport>>(nextEvolutionsJson)
                .map { Evolution(it.num, it.name) }
        } catch (e: Exception) {
            emptyList()
        }

        return Pokemon(
            id = id,
            name = name,
            types = types,
            region = region,
            genus = genus,
            prevEvolutions = prevEvolutions,
            nextEvolutions = nextEvolutions,
            height = height,
            weight = weight,
            baseAttack = baseAttack,
            baseDefense = baseDefense,
            baseStamina = baseStamina,
            fastMoves = fastMoves,
            chargedMoves = chargedMoves,
            spriteUrl = "",
            cryUrl = cryUrl,
            description = description
        )
    }
}