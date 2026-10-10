package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.OwnedPokemonTeams
import com.example.overdex.model.NicknameTokens
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch

private const val TEAMS_INDEX = 8
private const val FIELD_COUNT = 10
private const val SAVE_BUTTON_INDEX = 9

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditSpecimenScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var editedState by remember { mutableStateOf<OwnedPokemon?>(null) }
    var species by remember { mutableStateOf<Pokemon?>(null) }
    var teamsDraft by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val keyboardController = rememberTerminalKeyboardController()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val fieldRequesters = remember { List(FIELD_COUNT) { BringIntoViewRequester() } }
    LaunchedEffect(selectedIndex) { fieldRequesters[selectedIndex].bringIntoView() }

    // Initialize state
    LaunchedEffect(ownedPokemon) {
        if (editedState == null) {
            ownedPokemon?.let {
                editedState = it
                teamsDraft = it.teams.joinToString(", ")
                species = pokedexViewModel.getPokemonById(it.speciesId)
            }
        }
    }

    fun handleSave() {
        if (saving) return
        editedState?.let { specimen ->
            saving = true
            saveError = null
            scope.launch {
                try {
                    collectionViewModel.saveOwnedPokemon(
                        specimen.copy(teams = OwnedPokemonTeams.parse(teamsDraft), updatedAt = System.currentTimeMillis())
                    )
                    onFinish()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    saveError = "Could not save changes. Please retry."
                } finally { saving = false }
            }
        }
    }

    fun handleActivatedKey(key: String, specimen: OwnedPokemon) {
        if (selectedIndex == 0 || selectedIndex == 1 || selectedIndex == TEAMS_INDEX) {
            val currentText = when (selectedIndex) {
                0 -> specimen.displayName ?: ""
                1 -> specimen.cp?.toString() ?: ""
                TEAMS_INDEX -> teamsDraft
                else -> ""
            }
            val newText = NicknameTokens.applyKey(currentText, key)
            editedState = when (selectedIndex) {
                0 -> specimen.copy(displayName = newText.ifEmpty { null })
                1 -> if (key == "DELETE" || key.all(Char::isDigit)) specimen.copy(cp = newText.take(5).toIntOrNull()) else specimen
                TEAMS_INDEX -> { teamsDraft = newText; specimen }
                else -> specimen
            }
        } else {
            // Move selection
            if (key != "SPACE" && key != "DELETE") {
                editedState = when (selectedIndex) {
                    5 -> specimen.copy(fastMove = key)
                    6 -> specimen.copy(chargedMove1 = key)
                    7 -> specimen.copy(chargedMove2 = key.takeUnless { it == "NONE" })
                    else -> specimen
                }
            }
        }
    }

    fun activateField(index: Int) {
        selectedIndex = index
        val specimen = editedState ?: return
        when (index) {
            0, 1, TEAMS_INDEX -> {
                keyboardController.updateLayout(if (index == 1) listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("0", "DELETE")
                ) else LettersLayout)
                keyboardController.open()
            }
            5, 6, 7 -> {
                val moves = if (index == 5) species?.fastMoves else species?.chargedMoves
                val choices = moves.orEmpty().map { it.name.uppercase() }.toMutableList()
                if (index == 7) choices += "NONE"
                keyboardController.updateLayout(choices.chunked(2))
                keyboardController.open()
            }
            2 -> editedState = specimen.copy(isShadow = !specimen.isShadow, isPurified = false)
            3 -> editedState = specimen.copy(isPurified = !specimen.isPurified, isShadow = false)
            4 -> editedState = specimen.copy(isShiny = !specimen.isShiny)
            SAVE_BUTTON_INDEX -> handleSave()
        }
    }

    ODXFiShell(
        onUp = {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else if (selectedIndex > 0) {
                selectedIndex--
            }
        },
        onDown = {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else if (selectedIndex < FIELD_COUNT - 1) {
                selectedIndex++
            }
        },
        onLeft = { if (keyboardController.isVisible) keyboardController.handleLeft() },
        onRight = { if (keyboardController.isVisible) keyboardController.handleRight() },
        onA = {
            val specimen = editedState ?: return@ODXFiShell
            if (keyboardController.isVisible) {
                keyboardController.handleA("") { handleActivatedKey(it, specimen) }
            } else {
                activateField(selectedIndex)
            }
        },
        onB = {
            if (!keyboardController.handleB()) {
                onCancel()
            }
        },
        onSelect = {
            if (keyboardController.isVisible && selectedIndex in listOf(0, TEAMS_INDEX)) keyboardController.handleModeSwitch()
        },
        onStart = {
            keyboardController.handleStart()
        },
        onKeyActivated = { key ->
            val specimen = editedState ?: return@ODXFiShell
            if (keyboardController.isVisible) {
                handleActivatedKey(key, specimen)
            }
        },
        viewModel = pokedexViewModel,
        keyboardController = keyboardController,
        keyboardPrompt = when (selectedIndex) {
            0 -> "NICKNAME: ${editedState?.displayName.orEmpty()}"
            1 -> "CP: ${editedState?.cp ?: ""}"
            TEAMS_INDEX -> "TEAMS: $teamsDraft"
            else -> "SELECT MOVE"
        },
        lcdContent = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeIconText(editedState?.displayName ?: species?.name.orEmpty(), color = TerminalGreen, fontSize = 16.sp)
                TerminalText(if (selectedIndex == TEAMS_INDEX) "Separate team names with commas. One card can join several teams." else "[A] EDIT   [B] BACK", fontSize = 11.sp)
            }
        },
        showBattleOverlay = false
    ) {
        TerminalScreen {
            TerminalHeader(text = "edit specimen")
            
            editedState?.let { specimen ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    TerminalText(text = "SPECIES: ${species?.name?.uppercase() ?: "UNKNOWN"}", color = TerminalDimGreen)
                    TerminalText(text = "ID: ${specimen.id.takeLast(8).uppercase()}", fontSize = 10.sp, color = TerminalDimGreen.copy(alpha = 0.5f))
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    TerminalMenuOption(label = "NICKNAME", modifier = Modifier.bringIntoViewRequester(fieldRequesters[0]), selected = selectedIndex == 0, status = specimen.displayName ?: "---") { activateField(0) }
                    TerminalMenuOption(label = "CP", modifier = Modifier.bringIntoViewRequester(fieldRequesters[1]), selected = selectedIndex == 1, status = specimen.cp?.toString() ?: "0") { activateField(1) }
                    
                    TerminalMenuOption(label = "SHADOW", modifier = Modifier.bringIntoViewRequester(fieldRequesters[2]), selected = selectedIndex == 2, status = if (specimen.isShadow) "ON" else "OFF") {
                        editedState = specimen.copy(isShadow = !specimen.isShadow, isPurified = false)
                        selectedIndex = 2
                    }
                    TerminalMenuOption(label = "PURIFIED", modifier = Modifier.bringIntoViewRequester(fieldRequesters[3]), selected = selectedIndex == 3, status = if (specimen.isPurified) "ON" else "OFF") {
                        editedState = specimen.copy(isPurified = !specimen.isPurified, isShadow = false)
                        selectedIndex = 3
                    }
                    TerminalMenuOption(label = "SHINY", modifier = Modifier.bringIntoViewRequester(fieldRequesters[4]), selected = selectedIndex == 4, status = if (specimen.isShiny) "ON" else "OFF") {
                        editedState = specimen.copy(isShiny = !specimen.isShiny)
                        selectedIndex = 4
                    }
                    
                    TerminalMenuOption(label = "FAST MOVE", modifier = Modifier.bringIntoViewRequester(fieldRequesters[5]), selected = selectedIndex == 5, status = specimen.fastMove ?: "---") { activateField(5) }
                    TerminalMenuOption(label = "CHARGED 1", modifier = Modifier.bringIntoViewRequester(fieldRequesters[6]), selected = selectedIndex == 6, status = specimen.chargedMove1 ?: "---") { activateField(6) }
                    TerminalMenuOption(label = "CHARGED 2", modifier = Modifier.bringIntoViewRequester(fieldRequesters[7]), selected = selectedIndex == 7, status = specimen.chargedMove2 ?: "---") { activateField(7) }

                    Column(Modifier.bringIntoViewRequester(fieldRequesters[TEAMS_INDEX])) {
                        TerminalMenuOption(label = "TEAMS", selected = selectedIndex == TEAMS_INDEX) { activateField(TEAMS_INDEX) }
                        TerminalText(teamsDraft.ifBlank { "No teams assigned" }, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                    }

                    // TODO: Notes will be introduced in a future commit.

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    TerminalButton(
                        text = if (saving) "SAVING…" else "SAVE CHANGES",
                        modifier = Modifier.bringIntoViewRequester(fieldRequesters[SAVE_BUTTON_INDEX]),
                        selected = selectedIndex == SAVE_BUTTON_INDEX,
                        onClick = { handleSave() }
                    )
                    saveError?.let { TerminalText(it, color = TerminalPurple) }
                }
            }
        }
    }
}
