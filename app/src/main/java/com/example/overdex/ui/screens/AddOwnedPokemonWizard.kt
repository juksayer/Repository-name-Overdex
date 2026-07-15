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

    fun handleAction(index: Int) {
        when (currentStep) {
            WizardStep.SPECIES_SEARCH -> {
                if (index in 0 until pokemonItems.itemCount) {
                    pokemonItems[index]?.let {
                        selectedSpecies = it
                        currentStep = WizardStep.CP_INPUT
                    }
                }
            }
            WizardStep.CP_INPUT -> {
                if (index == 4) {
                    currentStep = WizardStep.ATTRIBUTES
                } else {
                    if (index < 4) focusIndex++
                }
            }
            WizardStep.ATTRIBUTES -> {
                when (index) {
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
    }

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
            handleAction(focusIndex)
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
                        initialIndex = focusIndex,
                        onIndexChange = { focusIndex = it },
                        onSpeciesSelected = { 
                            selectedSpecies = it
                            currentStep = WizardStep.CP_INPUT
                        }
                    )
                }
                WizardStep.CP_INPUT -> {
                    CPInputStep(
                        speciesName = selectedSpecies?.name ?: "UNKNOWN",
                        cpValue = cpValue,
                        focusIndex = focusIndex,
                        onFocusChange = { focusIndex = it },
                        onNext = { handleAction(4) }
                    )
                }
                WizardStep.ATTRIBUTES -> {
                    AttributesStep(
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        focusIndex = focusIndex,
                        onFocusChange = { focusIndex = it },
                        onAction = { handleAction(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeciesSearchStep(
    pokedexViewModel: PokedexViewModel,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    onSpeciesSelected: (Pokemon) -> Unit
) {
    val searchQuery by pokedexViewModel.searchQuery.collectAsState()
    val pokemonItems = pokedexViewModel.pagedPokemon.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    val nav = rememberHandheldNavigationController(
        initialIndex = initialIndex,
        itemCount = { pokemonItems.itemCount + 1 }, // SearchBar + List
        onActivate = { index ->
            if (index == 0) {
                // Future: Focus search bar
            } else {
                pokemonItems[index - 1]?.let { onSpeciesSelected(it) }
            }
        }
    )

    // Sync back to parent
    LaunchedEffect(nav.selectedIndex) {
        onIndexChange(nav.selectedIndex)
    }

    HandheldListSync(
        listState = listState,
        selectedIndex = nav.selectedIndex,
        listIndexMapping = { if (it == 0) null else it - 1 },
        totalItems = pokemonItems.itemCount
    )

    Column {
        SearchBar(
            query = searchQuery,
            selected = nav.selectedIndex == 0,
            onSearchClick = {
                nav.handleTouch(0)
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
                        selected = nav.selectedIndex == index + 1
                    ) {
                        nav.handleTouch(index + 1)
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
    focusIndex: Int,
    onFocusChange: (Int) -> Unit,
    onNext: () -> Unit
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
            focusedDigitIndex = focusIndex,
            onDigitFocusChange = onFocusChange
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        TerminalButton(
            text = "NEXT", 
            onClick = { 
                onFocusChange(4)
                onNext()
            },
            selected = focusIndex == 4
        )
    }
}

@Composable
fun AttributesStep(
    isShadow: Boolean,
    isPurified: Boolean,
    isShiny: Boolean,
    focusIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAction: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        AttributeToggle(
            label = "SHADOW", 
            value = isShadow, 
            selected = focusIndex == 0,
            onClick = {
                onFocusChange(0)
                onAction(0)
            }
        )
        AttributeToggle(
            label = "PURIFIED", 
            value = isPurified, 
            selected = focusIndex == 1,
            onClick = {
                onFocusChange(1)
                onAction(1)
            }
        )
        AttributeToggle(
            label = "SHINY", 
            value = isShiny, 
            selected = focusIndex == 2,
            onClick = {
                onFocusChange(2)
                onAction(2)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        TerminalButton(
            text = "SAVE SPECIMEN", 
            onClick = { 
                onFocusChange(3)
                onAction(3)
            },
            selected = focusIndex == 3
        )
    }
}


