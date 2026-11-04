package com.example.overdex.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedCardDeletionDeviceTest {
    @Test fun deletingATestCardKeepsOtherCopiesAndTheirMembershipsAfterReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "owned-card-delete-test-${System.nanoTime()}"
        val testCard = OwnedPokemon(id = "test-card", speciesId = 1, speciesName = "Bulbasaur", cp = 9000)
        val otherCopy = OwnedPokemon(id = "real-card", speciesId = 1, speciesName = "Bulbasaur", cp = 499,
            teams = listOf("Little League", "Practice"), fastMove = "Vine Whip", chargedMove1 = "Seed Bomb")
        val otherSpecies = OwnedPokemon(id = "other-species", speciesId = 776, speciesName = "Turtonator",
            cp = 1497, teams = listOf("Great League"), fastMove = "Incinerate", chargedMove1 = "Overheat")
        fun open() = Room.databaseBuilder(context, PokedexDatabase::class.java, databaseName).build()
        var database: PokedexDatabase? = null
        try {
            val initial = open().also { database = it }
            val dao = initial.ownedPokemonDao()
            listOf(testCard, otherCopy, otherSpecies).forEach { dao.addOwnedPokemon(it.toEntity()) }
            dao.deleteOwnedPokemon(testCard.id)
            assertNull(dao.getOwnedPokemonById(testCard.id).first())
            assertEquals(otherCopy, dao.getOwnedPokemonById(otherCopy.id).first()?.toDomain())
            assertEquals(otherSpecies, dao.getOwnedPokemonById(otherSpecies.id).first()?.toDomain())
            initial.close()

            val reopened = open().also { database = it }.ownedPokemonDao()
            assertNull(reopened.getOwnedPokemonById(testCard.id).first())
            assertEquals(setOf(otherCopy, otherSpecies),
                reopened.getAllOwnedPokemonWithSpecies().first().map { it.owned.toDomain() }.toSet())
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }
}
