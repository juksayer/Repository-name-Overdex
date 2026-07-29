package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

private const val FIELD_COUNT = 9
private const val SAVE_BUTTON_INDEX = 8

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

    val keyboardController = rememberTerminalKeyboardController()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // Initialize state
    LaunchedEffect(ownedPokemon) {
        if (editedState == null) {
            ownedPokemon?.let {
                editedState = it
                species = pokedexViewModel.getPokemonById(it.speciesId)
            }
        }
    }

    fun handleSave() {
        editedState?.let {
            collectionViewModel.updateOwnedPokemon(
                it.copy(updatedAt = System.currentTimeMillis())
            )
            onFinish()
        }
    }

    fun handleActivatedKey(key: String, specimen: OwnedPokemon) {
        if (selectedIndex == 0 || selectedIndex == 1) {
            val currentText = when (selectedIndex) {
                0 -> specimen.displayName ?: ""
                1 -> specimen.cp?.toString() ?: ""
                else -> ""
            }
            val newText = when (key) {
                "SPACE" -> currentText + " "
                "DELETE" -> if (currentText.isNotEmpty()) currentText.dropLast(1) else currentText
                else -> currentText + key
            }
            editedState = when (selectedIndex) {
                0 -> specimen.copy(displayName = newText.ifEmpty { null })
                1 -> specimen.copy(cp = newText.toIntOrNull())
                else -> specimen
            }
        } else {
            // Move selection
            if (key != "SPACE" && key != "DELETE") {
                editedState = when (selectedIndex) {
                    5 -> specimen.copy(fastMove = key)
                    6 -> specimen.copy(chargedMove1 = key)
                    7 -> specimen.copy(chargedMove2 = key)
                    else -> specimen
                }
            }
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
                when (selectedIndex) {
                    0, 1 -> {
                        keyboardController.updateLayout(LettersLayout)
                        keyboardController.open()
                    }
                    5, 6, 7 -> {
                        val moves = if (selectedIndex == 5) species?.fastMoves else species?.chargedMoves
                        val moveNames = moves?.map { it.name.uppercase() } ?: emptyList()
                        val moveLayout = moveNames.chunked(2).toMutableList()
                        keyboardController.updateLayout(moveLayout)
                        keyboardController.open()
                    }
                    2 -> editedState = specimen.copy(isShadow = !specimen.isShadow, isPurified = false)
                    3 -> editedState = specimen.copy(isPurified = !specimen.isPurified, isShadow = false)
                    4 -> editedState = specimen.copy(isShiny = !specimen.isShiny)
                    SAVE_BUTTON_INDEX -> handleSave()
                }
            }
        },
        onB = {
            if (!keyboardController.handleB()) {
                onCancel()
            }
        },
        onSelect = {
            /* No change in this brick */
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
        keyboardController = keyboardController
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

                    TerminalMenuOption(label = "NICKNAME", selected = selectedIndex == 0, status = specimen.displayName ?: "---") { selectedIndex = 0; keyboardController.open() }
                    TerminalMenuOption(label = "CP", selected = selectedIndex == 1, status = specimen.cp?.toString() ?: "0") { selectedIndex = 1; keyboardController.open() }
                    
                    TerminalMenuOption(label = "SHADOW", selected = selectedIndex == 2, status = if (specimen.isShadow) "ON" else "OFF") { 
                        editedState = specimen.copy(isShadow = !specimen.isShadow, isPurified = false)
                        selectedIndex = 2
                    }
                    TerminalMenuOption(label = "PURIFIED", selected = selectedIndex == 3, status = if (specimen.isPurified) "ON" else "OFF") {
                        editedState = specimen.copy(isPurified = !specimen.isPurified, isShadow = false)
                        selectedIndex = 3
                    }
                    TerminalMenuOption(label = "SHINY", selected = selectedIndex == 4, status = if (specimen.isShiny) "ON" else "OFF") {
                        editedState = specimen.copy(isShiny = !specimen.isShiny)
                        selectedIndex = 4
                    }
                    
                    TerminalMenuOption(label = "FAST MOVE", selected = selectedIndex == 5, status = specimen.fastMove ?: "---") { selectedIndex = 5; keyboardController.open() }
                    TerminalMenuOption(label = "CHARGED 1", selected = selectedIndex == 6, status = specimen.chargedMove1 ?: "---") { selectedIndex = 6; keyboardController.open() }
                    TerminalMenuOption(label = "CHARGED 2", selected = selectedIndex == 7, status = specimen.chargedMove2 ?: "---") { selectedIndex = 7; keyboardController.open() }

                    // TODO: Notes will be introduced in a future commit.

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    TerminalButton(
                        text = "SAVE CHANGES",
                        selected = selectedIndex == SAVE_BUTTON_INDEX,
                        onClick = { handleSave() }
                    )
                }
            }
        }
    }
}
