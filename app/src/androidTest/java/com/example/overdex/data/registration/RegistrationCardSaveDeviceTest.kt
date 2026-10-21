package com.example.overdex.data.registration

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toEntity
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RegistrationCardSaveDeviceTest {
    @Test fun retryingAnInterruptedScanPreservesOneCardAndLaterEdits() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            PokedexDatabase::class.java).build()
        try {
            val dao = database.ownedPokemonDao()
            val card = OwnedPokemon(id = "scan-session", speciesId = 25, cp = 500, fastMove = "Thunder Shock")
            assertTrue(dao.addScannedPokemon(card.toEntity()) != -1L)
            dao.updateOwnedPokemon(card.copy(displayName = "Sparky", teams = listOf("Great", "Electric")).toEntity())
            assertEquals(-1L, dao.addScannedPokemon(card.toEntity()))
            assertEquals(1, dao.getAllOwnedPokemonWithSpecies().first().size)
            val saved = requireNotNull(dao.getOwnedPokemonById(card.id).first())
            assertEquals("Sparky", saved.displayName)
            assertEquals("[\"Great\",\"Electric\"]", saved.teamsJson)
        } finally { database.close() }
    }
}
