package com.example.overdex.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

enum class WizardStep {
    SPECIES_SEARCH,
    CP_INPUT,
    ATTRIBUTES
}

@Composable
fun AddOwnedPokemonWizard(
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    isServiceRunning: Boolean = false
) {
    var currentStep by remember { mutableStateOf(WizardStep.SPECIES_SEARCH) }
    var selectedSpecies by remember { mutableStateOf<Pokemon?>(null) }
    var cpInput by remember { mutableStateOf("") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }

    PokedexFrame(
        onUp = {}, // Handle step specific navigation
        onDown = {},
        onB = {
            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> onCancel()
                WizardStep.CP_INPUT -> currentStep = WizardStep.SPECIES_SEARCH
                WizardStep.ATTRIBUTES -> currentStep = WizardStep.CP_INPUT
            }
        },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "add pokemon - step ${currentStep.ordinal + 1}/3")

            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> {
                    SpeciesSearchStep(
                        pokedexViewModel = pokedexViewModel,
                        onSpeciesSelected = {
                            selectedSpecies = it
                            currentStep = WizardStep.CP_INPUT
                        }
                    )
                }
                WizardStep.CP_INPUT -> {
                    CPInputStep(
                        speciesName = selectedSpecies?.name ?: "Unknown",
                        cpValue = cpInput,
                        onCPChange = { cpInput = it },
                        onNext = { currentStep = WizardStep.ATTRIBUTES }
                    )
                }
                WizardStep.ATTRIBUTES -> {
                    AttributesStep(
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        onShadowChange = { isShadow = it; if (it) isPurified = false },
                        onPurifiedChange = { isPurified = it; if (it) isShadow = false },
                        onShinyChange = { isShiny = it },
                        onSave = {
                            selectedSpecies?.let { species ->
                                collectionViewModel.addOwnedPokemon(
                                    OwnedPokemon(
                                        speciesId = species.id,
                                        cp = cpInput.toIntOrNull(),
                                        isShadow = isShadow,
                                        isPurified = isPurified,
                                        isShiny = isShiny
                                    )
                                )
                                onFinish()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeciesSearchStep(
    pokedexViewModel: PokedexViewModel,
    onSpeciesSelected: (Pokemon) -> Unit
) {
    val searchQuery by pokedexViewModel.searchQuery.collectAsState()
    val pokemonItems = pokedexViewModel.pagedPokemon.collectAsLazyPagingItems()

    Column {
        SearchBar(query = searchQuery) {
            pokedexViewModel.updateSearchQuery(it)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(
                count = pokemonItems.itemCount,
                key = pokemonItems.itemKey { it.id }
            ) { index ->
                pokemonItems[index]?.let { pokemon ->
                    TerminalMenuOption(label = pokemon.name) {
                        onSpeciesSelected(pokemon)
                    }
                }
            }
        }
    }
}

@Composable
fun CPInputStep(
    speciesName: String,
    cpValue: String,
    onCPChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter CP for $speciesName", color = TerminalGreen)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = cpValue,
            onValueChange = { if (it.length <= 4) onCPChange(it.filter { c -> c.isDigit() }) },
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
        
        TerminalButton(text = "NEXT", onClick = onNext)
    }
}

@Composable
fun AttributesStep(
    isShadow: Boolean,
    isPurified: Boolean,
    isShiny: Boolean,
    onShadowChange: (Boolean) -> Unit,
    onPurifiedChange: (Boolean) -> Unit,
    onShinyChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        AttributeToggle("Shadow", isShadow, onShadowChange)
        AttributeToggle("Purified", isPurified, onPurifiedChange)
        AttributeToggle("Shiny", isShiny, onShinyChange)

        Spacer(modifier = Modifier.weight(1f))

        TerminalButton(text = "SAVE POKEMON", onClick = onSave)
    }
}

@Composable
fun AttributeToggle(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (value) "[ X ]" else "[   ]",
            color = TerminalGreen,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = TerminalGreen, fontSize = 16.sp)
    }
}
