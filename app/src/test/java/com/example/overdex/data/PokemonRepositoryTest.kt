package com.example.overdex.data

import androidx.paging.PagingSource
import com.example.overdex.data.local.PokemonDao
import com.example.overdex.data.local.PokemonEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonRepositoryTest {

    @Test
    fun `getPokemonById(4) returns Pokemon with correct sprite URL from SpriteProvider`() = runBlocking {
        // Arrange
        val expectedSpriteUrl = "https://example.com/sprites/4.png"
        val fakeDao = FakePokemonDao()
        val fakeSpriteProvider = object : SpriteProvider {
            override fun getSpriteUrl(id: Int, isShiny: Boolean, isShadow: Boolean, isPurified: Boolean): String {
                return if (id == 4) expectedSpriteUrl else ""
            }
            override fun exists(id: Int, isShiny: Boolean): Boolean = true
        }
        
        val repository = PokemonRepository(fakeDao, fakeSpriteProvider)
        
        // Act
        val pokemon = repository.getPokemonById(4)
        
        // Assert
        assertEquals("Charmander", pokemon?.name)
        assertEquals(expectedSpriteUrl, pokemon?.spriteUrl)
    }

    private class FakePokemonDao : PokemonDao {
        override fun searchPokemon(query: String): PagingSource<Int, PokemonEntity> = TODO()
        override fun getPokemonByType(type: String): PagingSource<Int, PokemonEntity> = TODO()
        override fun getAllPokemon(): PagingSource<Int, PokemonEntity> = TODO()

        override suspend fun getPokemonById(id: Int): PokemonEntity? {
            return if (id == 4) {
                PokemonEntity(
                    id = 4,
                    name = "Charmander",
                    typesJson = "[]",
                    region = "Kanto",
                    fastMovesJson = "[]",
                    chargedMovesJson = "[]",
                    spriteUrl = "WRONG_URL", // This should be overwritten by the repository
                    cryUrl = "cry://4"
                )
            } else null
        }

        override suspend fun getPokemonByName(name: String): PokemonEntity? = TODO()
        override suspend fun insertAll(pokemon: List<PokemonEntity>) = TODO()
        override suspend fun clearAll() = TODO()
    }
}
