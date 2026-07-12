package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun MyCollectionScreen(
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onItemClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onBack: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val ownedPokemon by collectionViewModel.ownedPokemon.collectAsState()
    val searchQuery by collectionViewModel.searchQuery.collectAsState()
    val selectedIndex by collectionViewModel.selectedIndex.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = rememberTerminalKeyboardController()

    // Index 0: SearchBar
    // Index 1: [REGISTER SPECIMEN]
    // Index 2+: Specimen list
    val totalItems = ownedPokemon.size + 2

    LaunchedEffect(selectedIndex) {
        if (!keyboardController.isVisible) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || totalItems == 0) return@LaunchedEffect

            // Normalize coordinate system: listIndex is the item's index inside the LazyColumn
            val listIndex = if (selectedIndex == 0) null else selectedIndex - 1

            if (listIndex != null) {
                val firstVisible = visibleItems.first().index
                val lastVisible = visibleItems.last().index

                if (listIndex < firstVisible || listIndex > lastVisible) {
                    // Out of view jump
                    listState.animateScrollToItem(listIndex)
                } else if (listIndex <= firstVisible && listIndex > 0) {
                    // Top margin
                    listState.animateScrollToItem(listIndex - 1)
                } else if (listIndex >= lastVisible && selectedIndex < totalItems - 1) {
                    // Bottom margin
                    listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
                }
            }
        }
    }

    PokedexFrame(
        onUp = {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else if (selectedIndex > 0) {
                collectionViewModel.updateSelectedIndex(selectedIndex - 1)
            }
        },
        onDown = {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else if (selectedIndex < totalItems - 1) {
                collectionViewModel.updateSelectedIndex(selectedIndex + 1)
            }
        },
        onLeft = {
            if (keyboardController.isVisible) keyboardController.handleLeft()
        },
        onRight = {
            if (keyboardController.isVisible) keyboardController.handleRight()
        },
        onA = {
            if (keyboardController.isVisible) {
                keyboardController.handleA(searchQuery) { collectionViewModel.updateSearchQuery(it) }
            } else {
                when (selectedIndex) {
                    0 -> keyboardController.open()
                    1 -> onAddClick()
                    else -> {
                        val actualIndex = selectedIndex - 2
                        if (actualIndex in ownedPokemon.indices) {
                            onItemClick(ownedPokemon[actualIndex].id)
                        }
                    }
                }
            }
        },
        onB = {
            if (!keyboardController.handleB()) onBack()
        },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (keyboardController.isVisible) {
                TerminalHeader(text = "input module: search")
                SearchBar(query = searchQuery, selected = false)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TerminalKeyboard(
                    layout = keyboardController.layout,
                    currentRow = keyboardController.currentRow,
                    currentColumn = keyboardController.currentCol,
                    modifier = Modifier.weight(1f)
                )
            } else {
                TerminalHeader(text = "my collection")

                SearchBar(
                    query = searchQuery,
                    selected = selectedIndex == 0,
                    onSearchClick = {
                        keyboardController.open()
                        collectionViewModel.updateSelectedIndex(0)
                    }
                )

                Text(
                    text = "${ownedPokemon.size} SPECIMENS",
                    color = TerminalDimGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Persistent Registration Entry
                    item {
                        RegisterSpecimenItem(selected = selectedIndex == 1)
                    }

                    // Specimen List
                    itemsIndexed(ownedPokemon) { index, owned ->
                        var species by remember(owned.speciesId) { mutableStateOf<Pokemon?>(null) }
                        LaunchedEffect(owned.speciesId) {
                            species = pokedexViewModel.getPokemonById(owned.speciesId)
                        }

                        OwnedPokemonListItem(
                            owned = owned,
                            species = species,
                            selected = selectedIndex == index + 2,
                            pokedexViewModel = pokedexViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterSpecimenItem(selected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen else TerminalBlack,
            contentColor = if (selected) TerminalBlack else TerminalGreen
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "▶" else " ",
                color = if (selected) TerminalBlack else TerminalGreen,
                fontSize = 14.sp,
                modifier = Modifier.width(20.dp)
            )
            
            Text(
                text = "[+] REGISTER SPECIMEN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun OwnedPokemonListItem(
    owned: OwnedPokemon,
    species: Pokemon?,
    selected: Boolean,
    pokedexViewModel: PokedexViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen else TerminalBlack,
            contentColor = if (selected) TerminalBlack else TerminalGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "▶" else " ",
                color = if (selected) TerminalBlack else TerminalGreen,
                fontSize = 14.sp,
                modifier = Modifier.width(20.dp)
            )

            // Sprite
            val spriteUrl = pokedexViewModel.spriteProvider.getSpriteUrl(owned.speciesId)
            AsyncImage(
                model = spriteUrl,
                contentDescription = species?.name,
                modifier = Modifier.size(50.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = owned.displayName?.ifBlank { species?.name } ?: species?.name ?: "Unknown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) TerminalBlack else if (owned.isShiny) TerminalPurple else TerminalGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (owned.isShadow) {
                        Text("SHADOW ", color = if (selected) TerminalBlack else Color(0xFFBC13FE), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    if (owned.isPurified) {
                        Text("PURIFIED ", color = if (selected) TerminalBlack else Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    if (owned.cp != null) {
                        Text("CP ${owned.cp}", fontSize = 12.sp, color = if (selected) TerminalBlack else TerminalDimGreen)
                    }
                }
            }

            if (owned.isFavorite) {
                Text("★", color = if (selected) TerminalBlack else Color.Yellow, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (owned.isShiny) {
                Text("✨", fontSize = 14.sp)
            }
            
            if (species != null) {
                Row {
                    species.types.forEach { type ->
                        PokemonTypeIcon(
                            type = type, 
                            style = TypeIconStyle.OVERDEX, 
                            modifier = Modifier.size(14.dp).padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}
