package com.example.overdex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.OverdexApplication
import com.example.overdex.model.PokemonType
import com.example.overdex.model.SearchRequest
import com.example.overdex.ui.navigation.DirectorySearchResetEffect
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DirectorySearchResetDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directoryEndsSearchButDetailReturnPreservesTheCurrentVisit() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OverdexApplication
        lateinit var model: PokedexViewModel
        compose.runOnIdle {
            model = ViewModelProvider(app, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PokedexViewModel::class.java]
        }
        compose.setContent {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val query by model.searchQuery.collectAsState()
            val request by model.searchRequest.collectAsState()
            var reopenSearch by remember { mutableStateOf(false) }
            DirectorySearchResetEffect(entry?.destination?.route) {
                model.resetSearchSession()
                reopenSearch = false
            }
            NavHost(nav, startDestination = "main_menu") {
                composable("main_menu") {
                    Button(onClick = { nav.navigate("list") }) { Text("OPEN POKEDEX") }
                }
                composable("list") {
                    Column {
                        Text("QUERY: ${query.ifEmpty { "empty" }}")
                        Text("TYPE: ${request.type?.name ?: "none"}")
                        Text("REOPEN SEARCH: $reopenSearch")
                        Button(onClick = { nav.navigate("detail") }) { Text("OPEN DETAIL") }
                        Button(onClick = { nav.popBackStack() }) { Text("DIRECTORY") }
                    }
                }
                composable("detail") {
                    Column {
                        Button(onClick = { nav.popBackStack() }) { Text("BACK TO BINDER") }
                        Button(onClick = {
                            model.updateTypeFilter(PokemonType.FIRE)
                            model.updateSearchQuery("FIRE")
                            reopenSearch = true
                            nav.popBackStack()
                        }) { Text("SEARCH THIS TYPE") }
                    }
                }
            }
        }
        fun press(text: String) { compose.onNodeWithText(text).performClick(); compose.waitForIdle() }
        press("OPEN POKEDEX")
        compose.runOnIdle {
            model.updateSearchQuery("a query with no Pokemon results")
        }
        press("OPEN DETAIL"); press("BACK TO BINDER")
        compose.onNodeWithText("QUERY: a query with no Pokemon results").assertIsDisplayed()
        press("OPEN DETAIL"); press("SEARCH THIS TYPE")
        compose.onNodeWithText("QUERY: FIRE").assertIsDisplayed()
        compose.onNodeWithText("TYPE: FIRE").assertIsDisplayed()
        press("DIRECTORY")
        compose.runOnIdle {
            assertEquals("", model.searchQuery.value)
            assertEquals(SearchRequest(), model.searchRequest.value)
        }
        press("OPEN POKEDEX")
        compose.onNodeWithText("QUERY: empty").assertIsDisplayed()
        compose.onNodeWithText("TYPE: none").assertIsDisplayed()
        compose.onNodeWithText("REOPEN SEARCH: false").assertIsDisplayed()

        // Type-only filters have no query text to clear, but must still reset.
        compose.runOnIdle { model.updateTypeFilter(PokemonType.WATER) }
        press("DIRECTORY"); press("OPEN POKEDEX")
        compose.onNodeWithText("TYPE: none").assertIsDisplayed()
        compose.runOnIdle { assertTrue(model.searchRequest.value.activeFilters.isEmpty()) }
    }
}
