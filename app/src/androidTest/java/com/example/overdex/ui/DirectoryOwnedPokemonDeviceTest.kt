package com.example.overdex.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.paging.PagingData
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import com.example.overdex.model.*
import com.example.overdex.model.navigation.*
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.screens.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Real CRT GlassShield and LCD/buttons, backed by isolated Room storage. */
class DirectoryOwnedPokemonDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var editor: DirectoryOwnedPokemonEditor
    private lateinit var treeState: TreeState
    private val pikachu = Pokemon(25, "Pikachu", listOf(PokemonType.ELECTRIC), "Kanto",
        fastMoves = listOf(Move("Thunder Shock", PokemonType.ELECTRIC, 3, 9, true, 2)),
        chargedMoves = listOf(Move("Thunderbolt", PokemonType.ELECTRIC, 90, 55, false), Move("Surf", PokemonType.WATER, 75, 45, false)))
    private val bulbasaur = pikachu.copy(id = 1, name = "Bulbasaur")
    private val catalogue = listOf(bulbasaur, pikachu)
    private val database = lazy { Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
        PokedexDatabase::class.java).build() }

    @Composable private fun Harness(save: suspend (OwnedPokemon) -> Unit) {
        val tree = remember { InstrumentTree(listOf(DirectoryNode("BATTLE", listOf(
            DirectoryNode("Add Pokémon", OwnedPokemonField.entries.map { ActionNode(it.label, InstrumentCommand.EditNewOwnedPokemon(it)) }),
            ActionNode("Current Team", InstrumentCommand.OpenCurrentTeam),
        )))) }
        var state by remember { mutableStateOf(tree.getState()) }
        val registration = rememberDirectoryOwnedPokemonEditor(state,
            searchSpecies = { query, regions -> combine(query, regions) { q, range ->
                // Static PagingData otherwise emits no load-state update, so the
                // real editor correctly keeps rejecting choices as still loading.
                PagingData.from(catalogue.filter { it.name.contains(q, true) && it.id in range },
                    sourceLoadStates = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true)))
            } }, loadSpecies = { id -> catalogue.find { it.id == id } }, savePokemon = save,
            onFinished = { tree.collapse("/BATTLE/Add Pokémon"); state = tree.getState() })
        SideEffect { editor = registration; treeState = state }
        fun activate() {
            val command = tree.executeSelected()
            state = tree.getState()
            if (command is InstrumentCommand.EditNewOwnedPokemon) registration.activate(command.field)
        }
        // MainActivity's initial NavHost composition briefly has no route name.
        var route by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { route = "main_menu" }
        CompositionLocalProvider(com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute provides route) {
        ODXFiShell(showBattleOverlay = false, keyboardController = registration.keyboard,
            keyboardPrompt = registration.keyboardPrompt, onKeyActivated = registration.key, lcdLines = registration.lcdLines,
            onUp = { if (!registration.up()) { tree.moveSelection(-1); state = tree.getState() } },
            onDown = { if (!registration.down()) { tree.moveSelection(1); state = tree.getState() } },
            onLeft = { registration.left() }, onRight = { registration.right() },
            onA = { if (!registration.confirm()) activate() },
            onB = { if (!registration.back()) { tree.navigateBack(); state = tree.getState() } },
            onStart = { registration.start() }, onSelect = { registration.select() }) {
            MainMenuScreen(true, {}, registration.nodes, registration.selectedPath)
        }
        }
    }

    private fun button(label: String) {
        compose.waitForIdle()
        val button = if (label == "▼") compose.onNodeWithContentDescription("Down")
            else compose.onAllNodesWithText(label, substring = false).onLast()
        button.performTouchInput { click() }
        compose.waitForIdle()
    }
    private fun key(value: String) { compose.runOnIdle { editor.keyboard.activateKey(value, editor.key) } }
    private fun openBranch() { button("A"); button("▼"); button("A"); button("▼") }
    private fun field(field: OwnedPokemonField) {
        // Drive the physical column, never tap a CRT field through GlassShield.
        repeat(20) {
            if (treeState.selectedPath == "/BATTLE/Add Pokémon/${field.label}") return
            button("▼")
        }
        error("Could not navigate to $field")
    }

    @Test fun addWithButtonsKeepsDirectoryAndSavesOneSharedCardOnlyAtSave() {
        val allowSave = CompletableDeferred<Unit>()
        var writes = 0
        compose.setContent { Harness { card -> writes++; allowSave.await(); database.value.ownedPokemonDao().addOwnedPokemon(card.toEntity()) } }
        try {
            openBranch()
            button("A") // Species chooser remains inside the directory.
            compose.waitUntil { editor.nodes.any { it.node.name.contains("Pikachu") } }
            button("START")
            "PIKA".forEach { key(it.toString()) }
            key("END")
            compose.waitUntil { editor.nodes.any { it.node.name == "#025 Pikachu" } && editor.nodes.none { it.node.name == "#001 Bulbasaur" } }
            compose.runOnIdle { assertTrue(editor.keyboard.isVisible); assertFalse(editor.keyboard.isFocused) }
            button("A")
            field(OwnedPokemonField.NICKNAME); button("A"); key("Z"); key("TYPE:ELECTRIC"); button("B")
            field(OwnedPokemonField.CP); button("A"); "1499".forEach { key(it.toString()) }; button("B")
            field(OwnedPokemonField.FAST_MOVE); button("A"); button("▼"); button("A")
            field(OwnedPokemonField.CHARGED_MOVE_1); button("A"); button("▼"); button("A")
            field(OwnedPokemonField.CHARGED_MOVE_2); button("A")
            compose.runOnIdle { assertFalse(editor.nodes.any { it.path.contains("/choice/") && it.node.name == "Thunderbolt" }) }
            button("▼"); button("A")
            field(OwnedPokemonField.SHADOW); button("A")
            field(OwnedPokemonField.PURIFIED); button("A")
            field(OwnedPokemonField.TEAMS); button("A")
            "Great, Electric, great".forEach { key(it.toString()) }; button("B")
            assertEquals(0, writes)
            field(OwnedPokemonField.SAVE); button("A"); button("A"); button("START")
            compose.runOnIdle { assertEquals(1, writes); allowSave.complete(Unit) }
            compose.waitUntil { treeState.selectedPath == "/BATTLE/Add Pokémon" }
            val cards = runBlocking { database.value.ownedPokemonDao().getAllOwnedPokemonWithSpecies().first() }
            assertEquals(1, cards.size)
            val card = cards.single().owned.toDomain()
            assertEquals(25, card.speciesId)
            assertEquals("Z:ELECTRIC:", card.displayName)
            assertEquals(1499, card.cp)
            assertEquals("Thunder Shock", card.fastMove)
            assertEquals("Thunderbolt", card.chargedMove1)
            assertEquals("Surf", card.chargedMove2)
            assertFalse(card.isShadow)
            assertTrue(card.isPurified)
            assertEquals(listOf("Great", "Electric"), card.teams)
            compose.onNodeWithText("+ Add Pokémon/").assertIsDisplayed()
            compose.onNodeWithText("Current Team").assertIsDisplayed()
        } finally { if (database.isInitialized()) database.value.close() }
    }

    @Test fun failedSaveAndStateRestorationRetainDraftAndCancelCreatesNothing() {
        val restoration = StateRestorationTester(compose)
        val attempted = mutableListOf<OwnedPokemon>()
        restoration.setContent { Harness { card -> attempted += card; throw IOException("test write failure") } }
        openBranch(); button("A")
        compose.waitUntil { editor.nodes.any { it.node.name == "#001 Bulbasaur" } }
        button("A")
        button("START")
        compose.waitUntil { editor.lcdLines.any { it.contains("Could not save") } }
        restoration.emulateSavedInstanceStateRestore()
        openBranch()
        button("START")
        compose.waitUntil { attempted.size == 2 }
        assertEquals(attempted[0].id, attempted[1].id)
        assertEquals(attempted[0].speciesId, attempted[1].speciesId)
        field(OwnedPokemonField.CANCEL); button("A")
        assertEquals(2, attempted.size)
        button("A"); button("▼") // Reopen the cleared draft.
        compose.runOnIdle {
            val row = editor.nodes.first { it.path == "/BATTLE/Add Pokémon/Species" }.node as ActionNode
            assertEquals("Choose species", row.detail)
        }
        button("START")
        compose.runOnIdle { assertEquals(2, attempted.size); assertTrue(editor.lcdLines.any { it.contains("Choose a species") }) }
    }
}
