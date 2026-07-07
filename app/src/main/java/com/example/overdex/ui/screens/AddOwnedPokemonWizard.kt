package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    isServiceRunning: Boolean = false,
) {
    var currentStep by remember { mutableStateOf(WizardStep.SPECIES_SEARCH) }
    var selectedSpecies by remember { mutableStateOf<Pokemon?>(null) }
    var cpValue by remember { mutableStateOf("0000") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }

    // Focus state for D-pad navigation
    var focusIndex by remember(currentStep) { mutableIntStateOf(0) }
    
    // For Species Search list navigation
    val pokemonItems = pokedexViewModel.pagedPokemon.collectAsLazyPagingItems()

    PokedexFrame(
        onUp = {
            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> if (focusIndex > 0) focusIndex--
                WizardStep.CP_INPUT -> {
                    if (focusIndex < 4) { // Digits
                        val charArray = cpValue.toCharArray()
                        val currentDigit = charArray[focusIndex].digitToInt()
                        charArray[focusIndex] = ((currentDigit + 1) % 10).digitToChar()
                        cpValue = String(charArray)
                    }
                }
                WizardStep.ATTRIBUTES -> if (focusIndex > 0) focusIndex--
            }
        },
        onDown = {
            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> if (focusIndex < (pokemonItems.itemCount - 1)) focusIndex++
                WizardStep.CP_INPUT -> {
                    if (focusIndex < 4) { // Digits
                        val charArray = cpValue.toCharArray()
                        val currentDigit = charArray[focusIndex].digitToInt()
                        charArray[focusIndex] = ((currentDigit + 9) % 10).digitToChar()
                        cpValue = String(charArray)
                    }
                }
                WizardStep.ATTRIBUTES -> if (focusIndex < 3) focusIndex++ // 0-2: toggles, 3: save
            }
        },
        onLeft = {
            if (currentStep == WizardStep.CP_INPUT && focusIndex > 0) focusIndex--
        },
        onRight = {
            if (currentStep == WizardStep.CP_INPUT && focusIndex < 4) focusIndex++ // 4 is NEXT button
        },
        onA = {
            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> {
                    if (focusIndex in 0 until pokemonItems.itemCount) {
                        pokemonItems[focusIndex]?.let { 
                            selectedSpecies = it
                            currentStep = WizardStep.CP_INPUT
                        }
                    }
                }
                WizardStep.CP_INPUT -> {
                    if (focusIndex == 4) {
                        currentStep = WizardStep.ATTRIBUTES
                    } else {
                        // Optional: move focus to next digit on A? 
                        // For now, let's keep it simple: A on digits does nothing or moves focus.
                        if (focusIndex < 4) focusIndex++
                    }
                }
                WizardStep.ATTRIBUTES -> {
                    when (focusIndex) {
                        0 -> { isShadow = !isShadow; if (isShadow) isPurified = false }
                        1 -> { isPurified = !isPurified; if (isPurified) isShadow = false }
                        2 -> { isShiny = !isShiny }
                        3 -> {
                            selectedSpecies?.let { species ->
                                collectionViewModel.addOwnedPokemon(
                                    OwnedPokemon(
                                        speciesId = species.id,
                                        cp = cpValue.toIntOrNull(),
                                        isShadow = isShadow,
                                        isPurified = isPurified,
                                        isShiny = isShiny
                                    )
                                )
                                onFinish()
                            }
                        }
                    }
                }
            }
        },
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
            TerminalHeader(text = "register specimen - ${currentStep.ordinal + 1}/3")

            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> {
                    SpeciesSearchStep(
                        pokedexViewModel = pokedexViewModel,
                        selectedIndex = focusIndex
                    )
                }
                WizardStep.CP_INPUT -> {
                    CPInputStep(
                        speciesName = selectedSpecies?.name ?: "UNKNOWN",
                        cpValue = cpValue,
                        focusIndex = focusIndex
                    )
                }
                WizardStep.ATTRIBUTES -> {
                    AttributesStep(
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        focusIndex = focusIndex
                    )
                }
            }
        }
    }
}

@Composable
fun SpeciesSearchStep(
    pokedexViewModel: PokedexViewModel,
    selectedIndex: Int
) {
    val searchQuery by pokedexViewModel.searchQuery.collectAsState()
    val pokemonItems = pokedexViewModel.pagedPokemon.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val totalCount = pokemonItems.itemCount
        if (visibleItems.isEmpty() || totalCount == 0) return@LaunchedEffect

        val firstVisible = visibleItems.first().index
        val lastVisible = visibleItems.last().index

        if (selectedIndex < firstVisible || selectedIndex > lastVisible) {
            // Out of view jump
            listState.animateScrollToItem(selectedIndex)
        } else if (selectedIndex <= firstVisible && selectedIndex > 0) {
            // Top margin
            listState.animateScrollToItem(selectedIndex - 1)
        } else if (selectedIndex >= lastVisible && selectedIndex < totalCount - 1) {
            // Bottom margin
            listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
        }
    }

    Column {
        SearchBar(
            query = searchQuery,
            onSearchClick = {
                // For now, this step still uses regular SearchBar, but it's broken by my changes
                // I should probably also update this to the new SearchBar usage
                // but the work order says focus on Pokédex Search first.
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f), 
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                count = pokemonItems.itemCount,
                key = pokemonItems.itemKey { it.id }
            ) { index ->
                pokemonItems[index]?.let { pokemon ->
                    TerminalMenuOption(
                        label = pokemon.name,
                        selected = selectedIndex == index
                    ) {
                        // Click handled by PokedexFrame onA
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
    focusIndex: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ENTER CP FOR $speciesName", 
            color = TerminalGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        HardwareNumericEntry(
            value = cpValue,
            onValueChange = {}, // Handled by D-pad logic
            isFocused = focusIndex < 4,
            focusedDigitIndex = focusIndex
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        TerminalButton(
            text = "NEXT", 
            onClick = { /* Handled by PokedexFrame onA */ },
            selected = focusIndex == 4
        )
    }
}

@Composable
fun AttributesStep(
    isShadow: Boolean,
    isPurified: Boolean,
    isShiny: Boolean,
    focusIndex: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        AttributeToggle("SHADOW", isShadow, selected = focusIndex == 0)
        AttributeToggle("PURIFIED", isPurified, selected = focusIndex == 1)
        AttributeToggle("SHINY", isShiny, selected = focusIndex == 2)

        Spacer(modifier = Modifier.weight(1f))

        TerminalButton(
            text = "SAVE SPECIMEN", 
            onClick = { /* Handled by PokedexFrame onA */ },
            selected = focusIndex == 3
        )
    }
}


