package com.example.overdex.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.OwnedPokemonCardDetailScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The active card detail uses the real GlassShield, LCD and physical instrument buttons. */
class OwnedCardDetailDeletionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PokedexDatabase
    private val testCard = OwnedPokemon(id = "test-card", speciesId = 1, speciesName = "Bulbasaur", cp = 9000)
    private val otherCopy = OwnedPokemon(id = "real-card", speciesId = 1, speciesName = "Bulbasaur", cp = 499,
        teams = listOf("Little League", "Practice"), fastMove = "Vine Whip", chargedMove1 = "Seed Bomb")
    private val species = Pokemon(1, "Bulbasaur", listOf(PokemonType.GRASS, PokemonType.POISON), "Kanto",
        fastMoves = emptyList(), chargedMoves = emptyList())
    private var writes = 0
    private var returned = false
    private var backs = 0
    private var writeCompleted = false

    @Before fun createIsolatedCards() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            PokedexDatabase::class.java).build()
        listOf(testCard, otherCopy).forEach { database.ownedPokemonDao().addOwnedPokemon(it.toEntity()) }
    }

    @After fun closeDatabase() { database.close() }

    private fun show(delete: suspend (String) -> Unit = { id ->
        writes++
        database.ownedPokemonDao().deleteOwnedPokemon(id)
        writeCompleted = true
    }) {
        compose.setContent {
            var hasReturned by remember { mutableStateOf(false) }
            // Match MainActivity's initial unnamed route before NavHost has settled.
            var route by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { route = "specimens/detail/test-card" }
            CompositionLocalProvider(com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute provides route) {
                if (hasReturned) Text("RETURNED TO BINDER")
                else OwnedPokemonCardDetailScreen(testCard.id, testCard, species, spriteUrl = null,
                    deleteOwnedCard = delete, onEdit = {}, onRecords = {},
                    onDeleteSuccess = { assertTrue("Navigation must wait for the write", writeCompleted)
                        returned = true; hasReturned = true },
                    onBack = { backs++ })
            }
        }
        compose.waitForIdle()
    }

    private fun button(label: String) {
        val node = if (label == "DOWN") compose.onNodeWithContentDescription("Down")
            else compose.onAllNodesWithText(label, substring = false).onLast()
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun touchLcd(text: String) {
        compose.onNode(hasText("> $text", substring = false) or hasText("▶ $text", substring = false))
            .performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun assertCardsIntact() = runBlocking {
        assertEquals(testCard, database.ownedPokemonDao().getOwnedPokemonById(testCard.id).first()?.toDomain())
        assertEquals(otherCopy, database.ownedPokemonDao().getOwnedPokemonById(otherCopy.id).first()?.toDomain())
    }

    @Test fun defaultAAndBackAndTouchKeepNeverDelete() {
        show()
        button("SELECT")
        compose.onNodeWithText("▶ KEEP CARD").assertIsDisplayed()
        button("A") // Opening and immediately confirming defaults to keeping the card.
        touchLcd("DELETE CARD")
        button("DOWN")
        button("B")
        touchLcd("DELETE CARD")
        touchLcd("KEEP CARD")
        compose.runOnIdle { assertEquals(0, writes); assertEquals(0, backs); assertFalse(returned) }
        assertCardsIntact()
    }

    @Test fun explicitDeleteWritesOneCardAndReturnsOnlyAfterCompletion() {
        val allowWrite = CompletableDeferred<Unit>()
        show { id ->
            writes++
            allowWrite.await()
            database.ownedPokemonDao().deleteOwnedPokemon(id)
            writeCompleted = true
        }
        button("SELECT"); button("DOWN"); button("A")
        button("A"); button("B") // Repeated input cannot duplicate or abandon the pending write.
        compose.runOnIdle { assertEquals(1, writes); assertFalse(returned); assertEquals(0, backs) }
        assertCardsIntact()
        compose.runOnIdle { allowWrite.complete(Unit) }
        compose.waitUntil { returned }
        compose.onNodeWithText("RETURNED TO BINDER").assertIsDisplayed()
        runBlocking {
            assertNull(database.ownedPokemonDao().getOwnedPokemonById(testCard.id).first())
            assertEquals(otherCopy, database.ownedPokemonDao().getOwnedPokemonById(otherCopy.id).first()?.toDomain())
        }
    }

    @Test fun failedDeleteStaysOnConfirmationAndCanBeCancelled() {
        show { writes++; throw IOException("test write failure") }
        touchLcd("DELETE CARD")
        touchLcd("DELETE CARD") // Explicit second tap confirms from the accessible LCD.
        compose.onNodeWithText("Could not delete this card. Please retry.").assertExists()
        compose.onNodeWithText("▶ KEEP CARD").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, writes); assertFalse(returned) }
        assertCardsIntact()
        button("B")
        compose.onNodeWithText("EDIT CARD", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, backs); assertFalse(returned) }
    }
}
