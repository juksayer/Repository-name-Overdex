package com.example.overdex.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.OverdexApplication
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.screens.PokemonDetailScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DroidballDexDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun installsIntoExistingCatalogWithoutDuplicatesOrRecognitionCandidates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PokedexDatabase::class.java).build()
        try {
            val dao = db.pokemonDao()
            val pikachu = DroidballDexEntry.entity.copy(id = 25, name = "Pikachu", region = "Kanto")
            dao.insertAll(listOf(pikachu))
            DroidballDexEntry.install(dao)
            DroidballDexEntry.install(dao)
            assertEquals(2, dao.getCount())
            assertEquals(pikachu, dao.getPokemonById(25))
            val sprites = LocalSpriteProvider(context.assets)
            val repository = PokemonRepository(dao, sprites)
            val mascot = requireNotNull(repository.getPokemonById(10001))
            assertEquals("Droidball", mascot.name)
            assertEquals(listOf(PokemonType.PSYCHIC), mascot.types)
            assertEquals("#10001", mascot.formattedId)
            assertEquals(listOf("Pikachu"), repository.getAllSpeciesNames().toList())
            assertEquals(listOf(25), repository.getRegistrationCatalogue().map { it.id })
            for (query in listOf("Droidball", "10001")) {
                val result = repository.search(query, null).load(PagingSource.LoadParams.Refresh(null, 18, false))
                assertEquals(listOf(10001), (result as PagingSource.LoadResult.Page).data.map { it.id })
            }
            val notes = FieldNoteRepository(context).getFieldNotesForPokemon(10001)
            assertEquals(4, notes.size)
            assertEquals(FieldNoteRepository(context).getFieldNotesForPokemon(1026), notes)
            val loader = ImageLoader(context)
            try {
                assertTrue(loader.execute(ImageRequest.Builder(context).data(mascot.spriteUrl).build()) is SuccessResult)
            } finally { loader.shutdown() }
        } finally { db.close() }
    }

    @Test fun actualStartupMakesMascotAvailableAndDetailNavigatesThroughFieldNotes() {
        val app = context.applicationContext as OverdexApplication
        lateinit var viewModel: PokedexViewModel
        compose.runOnIdle {
            viewModel = ViewModelProvider(app, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PokedexViewModel::class.java]
        }
        val pokemon = runBlocking {
            withTimeout(30_000) {
                while (viewModel.getPokemonById(10001) == null) delay(100)
                requireNotNull(viewModel.getPokemonById(10001))
            }
        }
        var returned = false
        compose.setContent {
            PokemonDetailScreen(pokemon, FilterSettings(), {}, {}, {}, { returned = true },
                onPlayCry = { error("Mascot has no Pokémon GO cry") },
                onMoveClick = {}, onTypeClick = {}, onRegionClick = {}, onEvolutionClick = {},
                isBinderMode = true, viewModel = viewModel)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("FIELD NOTE").fetchSemanticsNodes().size == 4 }
        compose.onNodeWithText("#10001").assertExists()
        compose.onNodeWithText("Observation Pokémon").assertExists()
        compose.onNodeWithText("ATK 0", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Play Cry").assertDoesNotExist()
        compose.onNodeWithText("Fast Moves").assertDoesNotExist()
        fun save(name: String) {
            File(context.cacheDir, "droidball-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithContentDescription("Down").performTouchInput { click() } // Artwork
        compose.waitForIdle()
        save("detail")
        repeat(6) { compose.onNodeWithContentDescription("Down").performTouchInput { click() } }
        compose.waitForIdle()
        save("field-notes")
        compose.onAllNodesWithText("B").onLast().performTouchInput { click() }
        compose.runOnIdle { assertTrue(returned) }
    }
}
