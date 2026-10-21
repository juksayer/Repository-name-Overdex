package com.example.overdex.data.registration

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.PokemonEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationRegionSearchDeviceTest {
    @Test fun regionAndNameFiltersAreAppliedTogetherBeforePaging() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PokedexDatabase::class.java).build()
        try {
            val dao = db.pokemonDao()
            dao.insertAll(listOf(1 to "Bulbasaur", 152 to "Chikorita", 899 to "Wyrdeer", 906 to "Sprigatito").map { (id, name) ->
                PokemonEntity(id, name, "[]", "Old imported region", fastMovesJson = "[]", chargedMovesJson = "[]", spriteUrl = "", cryUrl = "")
            })
            suspend fun ids(query: String, first: Int, last: Int): List<Int> {
                val loaded = dao.searchPokemonInDexRange("%$query%", first, last, null)
                    .load(PagingSource.LoadParams.Refresh(null, 50, false))
                return (loaded as PagingSource.LoadResult.Page).data.map { it.id }
            }
            assertEquals(listOf(152), ids("", 152, 251))
            assertEquals(emptyList<Int>(), ids("Bulba", 152, 251))
            assertEquals(listOf(899), ids("Wyr", 899, 905))
            assertEquals(listOf(906), ids("906", 906, 1025))
        } finally { db.close() }
    }
}
