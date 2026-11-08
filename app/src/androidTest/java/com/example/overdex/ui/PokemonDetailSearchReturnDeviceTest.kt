package com.example.overdex.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.overdex.POKEMON_DETAIL_ROUTE
import com.example.overdex.openPokemonSearchFromDetail
import com.example.overdex.pokemonDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise actual destination matching and stack removal, including a detail's evolution chain. */
class PokemonDetailSearchReturnDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun journalEvolutionSearchShowsResultsAndBackReturnsToTheSameJournalEntry() {
        val nav = navigation()
        compose.runOnIdle {
            nav.navigate("field_notes")
            val journal = nav.currentBackStackEntry!!
            journal.savedStateHandle["selected-note"] = "181-002"
            nav.navigate(pokemonDetailRoute(181, originRoute = "field_notes"))
            val origin = nav.currentBackStackEntry!!.arguments!!.getString("origin")!!
            nav.navigate(pokemonDetailRoute(179, originRoute = origin))
            assertEquals("field_notes", nav.currentBackStackEntry!!.arguments!!.getString("origin"))

            nav.openPokemonSearchFromDetail(nav.currentBackStackEntry!!.arguments!!.getString("origin")!!)

            assertEquals("list", nav.currentDestination!!.route)
            assertEquals(journal.id, nav.previousBackStackEntry!!.id)
            assertTrue(nav.popBackStack())
            assertEquals("field_notes", nav.currentDestination!!.route)
            assertEquals(journal.id, nav.currentBackStackEntry!!.id)
            assertEquals("181-002", nav.currentBackStackEntry!!.savedStateHandle.get<String>("selected-note"))
            assertTrue(nav.popBackStack())
            assertEquals("main_menu", nav.currentDestination!!.route)
        }
    }

    @Test fun existingBinderLinksDefaultToListAndEvolutionSearchReusesItsOriginalEntry() {
        val nav = navigation()
        compose.runOnIdle {
            nav.navigate("list")
            val list = nav.currentBackStackEntry!!
            // Existing callers and links without an origin retain their original destination.
            nav.navigate("detail/1?binder=true")
            val args = nav.currentBackStackEntry!!.arguments!!
            assertTrue(args.getBoolean("binder"))
            assertEquals("list", args.getString("origin"))
            nav.navigate(pokemonDetailRoute(2, args.getBoolean("binder"), args.getString("origin")!!))

            nav.openPokemonSearchFromDetail(nav.currentBackStackEntry!!.arguments!!.getString("origin")!!)

            assertEquals("list", nav.currentDestination!!.route)
            assertEquals(list.id, nav.currentBackStackEntry!!.id)
            assertEquals("main_menu", nav.previousBackStackEntry!!.destination.route)
            assertTrue(nav.popBackStack())
            assertEquals("main_menu", nav.currentDestination!!.route)
        }
    }

    private fun navigation(): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "main_menu") {
                composable("main_menu") {}
                composable("field_notes") {}
                composable("list") {}
                composable(POKEMON_DETAIL_ROUTE, arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("binder") { type = NavType.BoolType; defaultValue = false },
                    navArgument("origin") { type = NavType.StringType; defaultValue = "list" },
                )) {}
            }
        }
        compose.waitForIdle()
        return nav
    }
}
