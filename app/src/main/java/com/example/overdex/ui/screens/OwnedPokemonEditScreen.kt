package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun OwnedPokemonEditScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSaveSuccess: () -> Unit,
    onCancel: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    
    // Mutable editing state
    var nickname by remember { mutableStateOf("") }
    var cpInput by remember { mutableStateOf("") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }

    LaunchedEffect(ownedPokemon) {
        ownedPokemon?.let { owned ->
            nickname = owned.displayName ?: ""
            cpInput = owned.cp?.toString() ?: ""
            isShadow = owned.isShadow
            isPurified = owned.isPurified
            isShiny = owned.isShiny
            
            species = pokedexViewModel.getPokemonById(owned.speciesId)
        }
    }

    PokedexFrame(
        onB = onCancel,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        if (ownedPokemon == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerminalText(text = "RECORD NOT FOUND")
            }
            return@PokedexFrame
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TerminalHeader(text = "edit pokemon")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Species: ${species?.name ?: "..."}", color = TerminalDimGreen, fontSize = 12.sp)
            Text(text = "Owned ID: #${ownedPokemon!!.id.takeLast(6).uppercase()}", color = TerminalDimGreen, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // Nickname
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname", color = TerminalDimGreen) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TerminalGreen,
                    unfocusedTextColor = TerminalGreen,
                    cursorColor = TerminalGreen,
                    focusedBorderColor = TerminalGreen,
                    unfocusedBorderColor = TerminalDimGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CP
            OutlinedTextField(
                value = cpInput,
                onValueChange = { if (it.length <= 4) cpInput = it.filter { c -> c.isDigit() } },
                label = { Text("CP", color = TerminalDimGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TerminalGreen,
                    unfocusedTextColor = TerminalGreen,
                    cursorColor = TerminalGreen,
                    focusedBorderColor = TerminalGreen,
                    unfocusedBorderColor = TerminalDimGreen
                ),
                modifier = Modifier.width(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Toggles
            AttributeToggle("Shadow", isShadow, onClick = { 
                isShadow = !isShadow
                if (isShadow) isPurified = false 
            })
            AttributeToggle("Purified", isPurified, onClick = { 
                isPurified = !isPurified
                if (isPurified) isShadow = false 
            })
            AttributeToggle("Shiny", isShiny, onClick = { isShiny = !isShiny })

            Spacer(modifier = Modifier.weight(1f))
            
            Spacer(modifier = Modifier.height(32.dp))

            TerminalButton(
                text = "SAVE CHANGES",
                onClick = {
                    val updated = ownedPokemon!!.copy(
                        displayName = nickname.ifBlank { null },
                        cp = cpInput.toIntOrNull(),
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        updatedAt = System.currentTimeMillis()
                    )
                    collectionViewModel.updateOwnedPokemon(updated)
                    onSaveSuccess()
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TerminalButton(
                text = "CANCEL",
                onClick = onCancel
            )
        }
    }
}
