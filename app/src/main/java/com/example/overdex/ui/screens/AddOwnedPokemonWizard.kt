package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import android.os.SystemClock
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

    val keyboardController = rememberTerminalKeyboardController(keepVisibleAfterInput = true)
    val localSearchQueryFlow = remember { MutableStateFlow("") }
    val localSearchQuery by localSearchQueryFlow.collectAsState()
    val regionFlow = remember { MutableStateFlow(RegistrationRegion.ALL) }
    val region by regionFlow.collectAsState()
    var heldDirection by remember { mutableIntStateOf(0) }
    var travelDirection by remember { mutableIntStateOf(1) }

    // For Species Search list navigation
    val pokemonItems = remember(localSearchQueryFlow) {
        pokedexViewModel.createSearchFlow(localSearchQueryFlow, dexRangeFlow = regionFlow.map { it.dexRange })
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

    fun moveSpecies(direction: Int, steps: Int = 1) {
        travelDirection = direction
        val next = (nav.selectedIndex + direction * steps).coerceIn(0, pokemonItems.itemCount)
        // Accessing the advancing row drives Paging's prefetch, including across holds.
        if (next > 0) pokemonItems[next - 1]
        nav.setIndex(next)
    }

    fun changeRegion(direction: Int) {
        heldDirection = 0
        regionFlow.value = RegistrationRegion.entries[(region.ordinal + direction).mod(RegistrationRegion.entries.size)]
        nav.setIndex(0)
    }

    fun toggleSearchFocus() {
        heldDirection = 0
        if (keyboardController.isFocused) keyboardController.handleStart() else keyboardController.open()
    }

    LaunchedEffect(localSearchQuery, region) { nav.setIndex(0); heldDirection = 0 }
    LaunchedEffect(keyboardController.isFocused) {
        heldDirection = 0
        if (keyboardController.isVisible && !keyboardController.isFocused && nav.selectedIndex == 0 && pokemonItems.itemCount > 0) {
            nav.setIndex(1)
        }
    }
    LaunchedEffect(heldDirection, currentStep, keyboardController.isFocused) {
        if (heldDirection == 0 || currentStep != WizardStep.SPECIES_SEARCH || keyboardController.isFocused) return@LaunchedEffect
        val direction = heldDirection
        val started = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - started
            moveSpecies(direction, SpeciesBrowserNavigation.repeatStep(elapsed))
            delay(SpeciesBrowserNavigation.repeatDelayMillis(elapsed))
        }
    }

    fun handleActivate(index: Int) {
        when (currentStep) {
            WizardStep.SPECIES_SEARCH -> {
                if (index == 0) {
                    keyboardController.open()
                } else if (index > 0 && index <= pokemonItems.itemCount) {
                    pokemonItems[index - 1]?.let {
                        selectedSpecies = it
                        keyboardController.close()
                        heldDirection = 0
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
                                    speciesName = species.name,
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

    val lcdLines = when (currentStep) {
        WizardStep.SPECIES_SEARCH -> listOf("STEP 1: SPECIES", "← → ${region.label.uppercase()}", "↑ ↓ BROWSE · HOLD TO SPEED UP", "START / SELECT: TYPE", "A: CHOOSE   B: BACK")
        WizardStep.FAST_MOVE_SELECTION -> moveSelectionLcdLines(
            move = selectedSpecies?.fastMoves?.getOrNull(nav.selectedIndex),
        )
        WizardStep.CHARGED_MOVE_SELECTION -> {
            val moves = selectedSpecies?.chargedMoves.orEmpty()
            val highlighted = moves.getOrNull(nav.selectedIndex)
            if (highlighted != null) {
                moveSelectionLcdLines(
                    move = highlighted,
                    selected = highlighted.name in selectedChargedMoves,
                )
            } else {
                listOf(
                    "NEXT: CP INPUT",
                    selectedSpecies?.name?.uppercase() ?: "UNKNOWN SPECIES",
                    "FAST ${selectedFastMove ?: "---"}",
                    "CHARGED ${selectedChargedMoves.size}/2",
                ) + selectedChargedMoves.map(String::uppercase)
            }
        }
        WizardStep.CP_INPUT -> listOf("REGISTRATION", "STEP 4: CP INPUT")
        WizardStep.ATTRIBUTES -> listOf("REGISTRATION", "STEP 5: ATTRIBUTES")
    }

    ODXFiShell(
        viewModel = pokedexViewModel,
        lcdLines = lcdLines,
        onUp = {
            if (keyboardController.isFocused) {
                keyboardController.handleUp()
            } else if (currentStep == WizardStep.SPECIES_SEARCH) {
                moveSpecies(-1)
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                val charArray = cpValue.toCharArray()
                val currentDigit = charArray[nav.selectedIndex].digitToInt()
                charArray[nav.selectedIndex] = ((currentDigit + 1) % 10).digitToChar()
                cpValue = String(charArray)
            } else {
                nav.moveUp()
            }
        },
        onUpLong = { if (currentStep == WizardStep.SPECIES_SEARCH && !keyboardController.isFocused) heldDirection = -1 },
        onUpPressChanged = { pressed -> if (!pressed && heldDirection == -1) heldDirection = 0 },
        onDown = {
            if (keyboardController.isFocused) {
                keyboardController.handleDown()
            } else if (currentStep == WizardStep.SPECIES_SEARCH) {
                moveSpecies(1)
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                val charArray = cpValue.toCharArray()
                val currentDigit = charArray[nav.selectedIndex].digitToInt()
                charArray[nav.selectedIndex] = ((currentDigit + 9) % 10).digitToChar()
                cpValue = String(charArray)
            } else {
                nav.moveDown()
            }
        },
        onDownLong = { if (currentStep == WizardStep.SPECIES_SEARCH && !keyboardController.isFocused) heldDirection = 1 },
        onDownPressChanged = { pressed -> if (!pressed && heldDirection == 1) heldDirection = 0 },
        onLeft = {
            if (keyboardController.isFocused) {
                keyboardController.handleLeft()
            } else if (currentStep == WizardStep.SPECIES_SEARCH) {
                changeRegion(-1)
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex > 0) {
                nav.moveUp()
            }
        },
        onRight = {
            if (keyboardController.isFocused) {
                keyboardController.handleRight()
            } else if (currentStep == WizardStep.SPECIES_SEARCH) {
                changeRegion(1)
            } else if (currentStep == WizardStep.CP_INPUT && nav.selectedIndex < 4) {
                nav.moveDown()
            }
        },
        onA = {
            if (keyboardController.isFocused) {
                keyboardController.handleA(localSearchQuery) { handleActivatedKey(it) }
            } else {
                handleActivate(nav.selectedIndex)
            }
        },
        onStart = { if (currentStep == WizardStep.SPECIES_SEARCH) toggleSearchFocus() },
        onSelect = { if (currentStep == WizardStep.SPECIES_SEARCH) toggleSearchFocus() },
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
        keyboardPrompt = if (keyboardController.isFocused) "TYPE: $localSearchQuery  ·  END: RESULTS" else "↑↓ BROWSE · ←→ REGION · SELECT: TYPE",
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
                        region = region,
                        travelDirection = travelDirection,
                        fastScrolling = heldDirection != 0,
                        queryFocused = keyboardController.isFocused || nav.selectedIndex == 0,
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
    nextLabel: String = "NEXT",
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
                        text = nextLabel,
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
    region: RegistrationRegion? = null,
    travelDirection: Int = 1,
    fastScrolling: Boolean = false,
    queryFocused: Boolean = selectedIndex == 0,
    onSelectedIndexChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        if (region != null) {
            val tabs = rememberLazyListState()
            LaunchedEffect(region) { tabs.scrollToItem((region.ordinal - 1).coerceAtLeast(0)) }
            LazyRow(state = tabs, horizontalArrangement = Arrangement.spacedBy(4.dp), userScrollEnabled = false) {
                items(RegistrationRegion.entries.size) { index ->
                    val tab = RegistrationRegion.entries[index]
                    Text(tab.label.uppercase(), color = if (tab == region) TerminalGreen else TerminalDimGreen,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(76.dp).border(1.dp, if (tab == region) TerminalGreen else TerminalDimGreen)
                            .padding(horizontal = 5.dp, vertical = 6.dp).semantics { selected = tab == region })
                }
            }
        }
        SearchBar(
            query = searchQuery,
            selected = queryFocused
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (pokemonItems.itemCount == 0) {
            Text(if (pokemonItems.loadState.refresh is LoadState.Loading) "Loading Pokémon…" else "No Pokémon found. Try another region or name.",
                color = TerminalDimGreen, modifier = Modifier.padding(8.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowHeight = 36.dp
            val rowGap = 4.dp
            val visibleRows = ((maxHeight + rowGap) / (rowHeight + rowGap)).toInt().coerceAtLeast(1)
            val target = SpeciesBrowserNavigation.firstVisible(selectedIndex - 1, listState.firstVisibleItemIndex,
                visibleRows, pokemonItems.itemCount, travelDirection, fastScrolling)
            SideEffect {
                // Apply selection and position to the same layout pass. Launching a
                // scrolling coroutine lets the highlight get a frame ahead on a hold.
                if (target != listState.firstVisibleItemIndex || listState.firstVisibleItemScrollOffset != 0) {
                    listState.requestScrollToItem(target)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                items(
                    count = pokemonItems.itemCount,
                    key = pokemonItems.itemKey { it.id }
                ) { index ->
                    pokemonItems[index]?.let { pokemon ->
                        val isSelected = selectedIndex == index + 1
                        Row(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = 8.dp)
                            .semantics { selected = isSelected }, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isSelected) "> " else "  ", color = TerminalGreen, fontWeight = FontWeight.Bold)
                            Text(pokemon.id.toString().padStart(4, '0'), color = TerminalDimGreen, fontSize = 11.sp,
                                modifier = Modifier.width(42.dp))
                            Text(pokemon.name, color = if (isSelected) TerminalGreen else TerminalDimGreen,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
