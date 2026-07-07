package com.example.overdex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.PokedexFrame
import com.example.overdex.ui.components.SearchBar
import com.example.overdex.ui.components.TypeBadge
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun PokedexListScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onPokemonClick: (Int) -> Unit,
    isServiceRunning: Boolean = false,
) {
    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchRequest by viewModel.searchRequest.collectAsState()
    val listState = rememberLazyListState()

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val keyboardController = rememberTerminalKeyboardController()

    LaunchedEffect(selectedIndex) {
        if (!keyboardController.isVisible) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalCount = pokemonItems.itemCount

            if (visibleItems.isEmpty() || totalCount == 0) return@LaunchedEffect

            val firstVisible = visibleItems.first().index
            val lastVisible = visibleItems.last().index

            if (selectedIndex < firstVisible || selectedIndex > lastVisible) {
                // Out of view jump (e.g. search reset or initial load)
                listState.animateScrollToItem(selectedIndex)
            } else if (selectedIndex <= firstVisible && selectedIndex > 0) {
                // Top margin: scroll up to keep selection from hitting the absolute top
                listState.animateScrollToItem(selectedIndex - 1)
            } else if (selectedIndex >= lastVisible && selectedIndex < totalCount - 1) {
                // Bottom margin: scroll down to keep selection from hitting the absolute bottom
                listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
            }
        }
    }

    LaunchedEffect(searchQuery, searchRequest) {
        selectedIndex = 0
    }

    PokedexFrame(
        onUp = {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else {
                if (selectedIndex > 0) selectedIndex--
            }
        },
        onDown = {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else {
                if (selectedIndex < pokemonItems.itemCount) selectedIndex++
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
                keyboardController.handleA(searchQuery) { viewModel.updateSearchQuery(it) }
            } else {
                if (selectedIndex == 0) {
                    keyboardController.open()
                } else if (selectedIndex > 0 && selectedIndex <= pokemonItems.itemCount) {
                    pokemonItems[selectedIndex - 1]?.let { onPokemonClick(it.id) }
                }
            }
        },
        onB = {
            if (!keyboardController.handleB()) onBack()
        },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onSelect = onSelect,
        onStart = onStart,
        viewModel = viewModel,
        isServiceRunning = isServiceRunning
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (keyboardController.isVisible) {
                TerminalHeader("input module: search")
                SearchBar(query = searchQuery, selected = false)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TerminalKeyboard(
                    layout = keyboardController.layout,
                    currentRow = keyboardController.currentRow,
                    currentColumn = keyboardController.currentCol,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SearchBar(
                    query = searchQuery, 
                    selected = selectedIndex == 0,
                    onSearchClick = { 
                        keyboardController.open()
                        selectedIndex = 0
                    }
                )
                
                searchRequest.activeFilters.forEach { filter ->
                    AssistChip(
                        onClick = {
                            viewModel.removeFilter(filter)
                        },
                        label = {
                            Text(filter.label)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = pokemonItems.itemCount,
                        key = pokemonItems.itemKey { it.id },
                        contentType = pokemonItems.itemContentType { "pokemon" }
                    ) { index ->
                        // Offset by 1 for the SearchBar
                        val pokemonIndex = index
                        pokemonItems[pokemonIndex]?.let { pokemon ->
                            PokemonListItem(
                                pokemon = pokemon,
                                selected = selectedIndex == (index + 1)
                            ) {
                                selectedIndex = index + 1
                                onPokemonClick(pokemon.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PokemonListItem(pokemon: Pokemon, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen else TerminalBlack,
            contentColor = if (selected) TerminalBlack else TerminalGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(0.dp) // Sharp corners for hardware feel
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "▶" else " ",
                color = if (selected) TerminalBlack else TerminalGreen,
                fontSize = 14.sp,
                modifier = Modifier.width(20.dp)
            )

            // Sprite
            AsyncImage(
                model = pokemon.spriteUrl,
                contentDescription = pokemon.name,
                modifier = Modifier.size(50.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = pokemon.formattedId,
                fontSize = 12.sp,
                color = if (selected) TerminalBlack else TerminalDimGreen,
                modifier = Modifier.width(40.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pokemon.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) TerminalBlack else TerminalGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pokemon.region,
                    fontSize = 11.sp,
                    color = if (selected) TerminalBlack else TerminalDimGreen
                )
            }
            
            Row {
                pokemon.types.forEach { type ->
                    TypeBadge(
                        type = type, 
                        style = TypeIconStyle.OVERDEX,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}
