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
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

enum class WizardStep {
    SPECIES_SEARCH,
    FAST_MOVE_SELECTION,
    CHARGED_MOVE_SELECTION,
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
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableStateOf(WizardStep.SPECIES_SEARCH) }
    var selectedSpecies by remember { mutableStateOf<Pokemon?>(null) }
    var selectedFastMove by remember { mutableStateOf<String?>(null) }
    var selectedChargedMoves by remember { mutableStateOf(setOf<String>()) }
    var cpValue by remember { mutableStateOf("0000") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }

    val keyboardController = rememberTerminalKeyboardController()
    val localSearchQueryFlow = remember { MutableStateFlow("") }
    val localSearchQuery by localSearchQueryFlow.collectAsState()

    // For Species Search list navigation
    val pokemonItems = remember(localSearchQueryFlow) {
        pokedexViewModel.createSearchFlow(localSearchQueryFlow)
    }.collectAsLazyPagingItems()

    val nav = rememberHandheldNavigationController(
        itemCount = {
            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> pokemonItems.itemCount + 1 // SearchBar + List
                WizardStep.FAST_MOVE_SELECTION -> selectedSpecies?.fastMoves?.size ?: 0
                WizardStep.CHARGED_MOVE_SELECTION -> (selectedSpecies?.chargedMoves?.size ?: 0) + 1 // Moves + NEXT
                WizardStep.CP_INPUT -> 5 // 4 digits + NEXT
                WizardStep.ATTRIBUTES -> 4 // 3 toggles + SAVE
            }
        }
    )

    fun handleActivate(index: Int) {
        when (currentStep) {
            WizardStep.SPECIES_SEARCH -> {
                if (index == 0) {
                    keyboardController.open()
                } else if (index > 0 && index <= pokemonItems.itemCount) {
                    pokemonItems[index - 1]?.let {
                        selectedSpecies = it
                        currentStep = WizardStep.FAST_MOVE_SELECTION
                        nav.setIndex(0) // Reset focus for next step
                    }
                }
            }
            WizardStep.FAST_MOVE_SELECTION -> {
                selectedSpecies?.fastMoves?.getOrNull(index)?.let {
                    selectedFastMove = it.name
                    currentStep = WizardStep.CHARGED_MOVE_SELECTION
                    nav.setIndex(0)
                }
            }
            WizardStep.CHARGED_MOVE_SELECTION -> {
                val chargedMovesCount = selectedSpecies?.chargedMoves?.size ?: 0
                if (index < chargedMovesCount) {
                    selectedSpecies?.chargedMoves?.getOrNull(index)?.let { move ->
                        if (selectedChargedMoves.contains(move.name)) {
                            selectedChargedMoves = selectedChargedMoves - move.name
                        } else if (selectedChargedMoves.size < 2) {
                            selectedChargedMoves = selectedChargedMoves + move.name
                        }
                    }
                } else if (index == chargedMovesCount) {
                    // NEXT button
                    currentStep = WizardStep.CP_INPUT
                    nav.setIndex(0)
                }
            }
            WizardStep.CP_INPUT -> {
                if (index == 4) {
                    currentStep = WizardStep.ATTRIBUTES
                    nav.setIndex(0)
                } else {
                    nav.moveDown() // Move to next digit
                }
            }
            WizardStep.ATTRIBUTES -> {
                when (index) {
                    0 -> { isShadow = !isShadow; if (isShadow) isPurified = false }
                    1 -> { isPurified = !isPurified; if (isPurified) isShadow = false }
                    2 -> { isShiny = !isShiny }
                    3 -> {
                        selectedSpecies?.let { species ->
                            val chargedList = selectedChargedMoves.toList()
                            collectionViewModel.addOwnedPokemon(
                                OwnedPokemon(
                                    speciesId = species.id,
                                    cp = cpValue.toIntOrNull(),
                                    isShadow = isShadow,
                                    isPurified = isPurified,
                                    isShiny = isShiny,
                                    fastMove = selectedFastMove,
                                    chargedMove1 = chargedList.getOrNull(0),
                                    chargedMove2 = chargedList.getOrNull(1)
                                )
                            )
                            onFinish()
                        }
                    }
                }
            }
        }
    }

    fun handleActivatedKey(key: String) {
        when (key) {
            "SPACE" -> localSearchQueryFlow.value += " "
            "DELETE" -> {
                if (localSearchQuery.isNotEmpty()) {
                    localSearchQueryFlow.value = localSearchQuery.dropLast(1)
                }
            }
            else -> {
                localSearchQueryFlow.value += key
            }
        }
    }

    val lcdLine1 = "REGISTRATION"
    val lcdLine2 = when (currentStep) {
        WizardStep.SPECIES_SEARCH -> "STEP 1: SPECIES"
        WizardStep.FAST_MOVE_SELECTION -> "STEP 2: FAST MOVE"
        WizardStep.CHARGED_MOVE_SELECTION -> "STEP 3: CHARGED MOVES"
        WizardStep.CP_INPUT -> "STEP 4: CP INPUT"
        WizardStep.ATTRIBUTES -> "STEP 5: ATTRIBUTES"
    }

    ODXFiShell(
        viewModel = pokedexViewModel,
        lcdLine1 = lcdLine1,
        lcdLine2 = lcdLine2,
        onUp = {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                val charArray = cpValue.toCharArray()
                val currentDigit = charArray[nav.selectedIndex].digitToInt()
                charArray[nav.selectedIndex] = ((currentDigit + 1) % 10).digitToChar()
                cpValue = String(charArray)
            } else {
                nav.moveUp()
            }
        },
        onDown = {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                val charArray = cpValue.toCharArray()
                val currentDigit = charArray[nav.selectedIndex].digitToInt()
                charArray[nav.selectedIndex] = ((currentDigit + 9) % 10).digitToChar()
                cpValue = String(charArray)
            } else {
                nav.moveDown()
            }
        },
        onLeft = {
            if (keyboardController.isVisible) {
                keyboardController.handleLeft()
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex > 0) {
                nav.moveUp()
            }
        },
        onRight = {
            if (keyboardController.isVisible) {
                keyboardController.handleRight()
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                nav.moveDown()
            }
        },
        onA = {
            if (keyboardController.isVisible) {
                keyboardController.handleA(localSearchQuery) { handleActivatedKey(it) }
            } else {
                handleActivate(nav.selectedIndex)
            }
        },
        onB = {
            if (keyboardController.isVisible) {
                keyboardController.handleB()
            } else {
                when (currentStep) {
                    WizardStep.SPECIES_SEARCH -> onCancel()
                    WizardStep.FAST_MOVE_SELECTION -> {
                        currentStep = WizardStep.SPECIES_SEARCH
                        nav.setIndex(0)
                    }
                    WizardStep.CHARGED_MOVE_SELECTION -> {
                        currentStep = WizardStep.FAST_MOVE_SELECTION
                        nav.setIndex(0)
                    }
                    WizardStep.CP_INPUT -> {
                        currentStep = WizardStep.CHARGED_MOVE_SELECTION
                        nav.setIndex(0)
                    }
                    WizardStep.ATTRIBUTES -> {
                        currentStep = WizardStep.CP_INPUT
                        nav.setIndex(0)
                    }
                }
            }
        },
        keyboardController = keyboardController,
        onKeyActivated = { key ->
            if (keyboardController.isVisible) {
                handleActivatedKey(key)
            }
        },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "register specimen - ${currentStep.ordinal + 1}/${WizardStep.entries.size}")

            when (currentStep) {
                WizardStep.SPECIES_SEARCH -> {
                    SpeciesSearchStep(
                        searchQuery = localSearchQuery,
                        pokemonItems = pokemonItems,
                        selectedIndex = nav.selectedIndex,
                        onSelectedIndexChange = { }
                    )
                }
                WizardStep.FAST_MOVE_SELECTION -> {
                    MoveSelectionStep(
                        title = "SELECT FAST MOVE",
                        moves = selectedSpecies?.fastMoves ?: emptyList(),
                        selectedMoves = setOfNotNull(selectedFastMove),
                        selectedIndex = nav.selectedIndex,
                        onSelectedIndexChange = { }
                    )
                }
                WizardStep.CHARGED_MOVE_SELECTION -> {
                    MoveSelectionStep(
                        title = "SELECT CHARGED MOVES (1-2)",
                        moves = selectedSpecies?.chargedMoves ?: emptyList(),
                        selectedMoves = selectedChargedMoves,
                        selectedIndex = nav.selectedIndex,
                        showNext = true,
                        onSelectedIndexChange = { }
                    )
                }
                WizardStep.CP_INPUT -> {
                    CPInputStep(
                        speciesName = selectedSpecies?.name ?: "UNKNOWN",
                        cpValue = cpValue,
                        focusIndex = nav.selectedIndex,
                        onFocusChange = { },
                        onNext = { handleActivate(4) }
                    )
                }
                WizardStep.ATTRIBUTES -> {
                    AttributesStep(
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        focusIndex = nav.selectedIndex,
                        onFocusChange = { },
                        onAction = { handleActivate(it) }
                    )
                }
            }
        }
    }
}


@Composable
fun MoveSelectionStep(
    title: String,
    moves: List<com.example.overdex.model.Move>,
    selectedMoves: Set<String>,
    selectedIndex: Int,
    showNext: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = title,
            color = TerminalPurple,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(moves.size) { index ->
                val move = moves[index]
                TerminalMenuOption(
                    label = move.name,
                    selected = selectedIndex == index,
                    status = if (selectedMoves.contains(move.name)) "SELECTED" else null
                )
            }
            
            if (showNext) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    TerminalButton(
                        text = "NEXT",
                        selected = selectedIndex == moves.size
                    )
                }
            }
        }
    }
}



@Composable
fun SpeciesSearchStep(
    searchQuery: String,
    pokemonItems: androidx.paging.compose.LazyPagingItems<Pokemon>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    HandheldListSync(
        listState = listState,
        selectedIndex = selectedIndex,
        listIndexMapping = { if (it == 0) null else it - 1 },
        totalItems = pokemonItems.itemCount
    )

    Column {
        SearchBar(
            query = searchQuery,
            selected = selectedIndex == 0
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
                        selected = selectedIndex == index + 1
                    )
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
            focusedDigitIndex = focusIndex
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        TerminalButton(
            text = "NEXT", 
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
            selected = focusIndex == 0
        )
        AttributeToggle(
            label = "PURIFIED", 
            value = isPurified, 
            selected = focusIndex == 1
        )
        AttributeToggle(
            label = "SHINY", 
            value = isShiny, 
            selected = focusIndex == 2
        )

        Spacer(modifier = Modifier.weight(1f))

        TerminalButton(
            text = "SAVE SPECIMEN", 
            selected = focusIndex == 3
        )
    }
}
