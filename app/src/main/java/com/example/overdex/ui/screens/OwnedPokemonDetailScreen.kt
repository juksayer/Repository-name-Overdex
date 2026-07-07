package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun OwnedPokemonDetailScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onEditClick: () -> Unit,
    onDeleteSuccess: () -> Unit,
    onBack: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var actionIndex by remember { mutableIntStateOf(0) }
    
    val actions = listOf("EDIT", "DELETE", "BACK")

    LaunchedEffect(ownedPokemon?.speciesId) {
        ownedPokemon?.speciesId?.let { id ->
            species = pokedexViewModel.getPokemonById(id)
        }
    }

    PokedexFrame(
        onUp = { if (actionIndex > 0) actionIndex-- },
        onDown = { if (actionIndex < actions.size - 1) actionIndex++ },
        onA = {
            if (showDeleteConfirm) {
                collectionViewModel.removeOwnedPokemon(ownedId)
                onDeleteSuccess()
            } else {
                when (actions[actionIndex]) {
                    "EDIT" -> onEditClick()
                    "DELETE" -> showDeleteConfirm = true
                    "BACK" -> onBack()
                }
            }
        },
        onB = {
            if (showDeleteConfirm) {
                showDeleteConfirm = false
            } else {
                onBack()
            }
        },
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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                val identity = ownedPokemon!!.displayName?.ifBlank { species?.name } ?: species?.name ?: "UNKNOWN"
                TerminalHeader(text = identity)
                
                Spacer(modifier = Modifier.height(16.dp))

                DetailField(label = "Species", value = species?.name ?: "Loading...")
                DetailField(label = "Nickname", value = ownedPokemon!!.displayName ?: "[NONE]")
                DetailField(label = "Owned ID", value = "#${ownedPokemon!!.id.takeLast(6).uppercase()}")
                
                Spacer(modifier = Modifier.height(16.dp))

                DetailField(label = "CP", value = ownedPokemon!!.cp?.toString() ?: "[NONE]")
                DetailField(label = "Shadow", value = if (ownedPokemon!!.isShadow) "[YES]" else "[NO]")
                DetailField(label = "Purified", value = if (ownedPokemon!!.isPurified) "[YES]" else "[NO]")
                DetailField(label = "Shiny", value = if (ownedPokemon!!.isShiny) "[YES]" else "[NO]")

                Spacer(modifier = Modifier.weight(1f))
                
                HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                actions.forEachIndexed { index, label ->
                    TerminalMenuOption(
                        label = label,
                        selected = actionIndex == index && !showDeleteConfirm,
                        onClick = {
                            if (!showDeleteConfirm) {
                                actionIndex = index
                                when (label) {
                                    "EDIT" -> onEditClick()
                                    "DELETE" -> showDeleteConfirm = true
                                    "BACK" -> onBack()
                                }
                            }
                        }
                    )
                }
            }

            if (showDeleteConfirm) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TerminalBlack.copy(alpha = 0.8f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, TerminalPurple, RoundedCornerShape(4.dp))
                            .background(TerminalBlack)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DELETE THIS POKEMON?",
                            color = TerminalPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Text(text = "A YES", color = Color.Red, fontWeight = FontWeight.Bold)
                            Text(text = "B NO", color = TerminalGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = TerminalDimGreen, fontSize = 12.sp)
        Text(text = value, color = TerminalGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
