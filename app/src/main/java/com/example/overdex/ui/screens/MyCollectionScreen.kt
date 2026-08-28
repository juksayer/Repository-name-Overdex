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
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    keyboardController: TerminalKeyboardController
) {
    val ownedPokemon by collectionViewModel.ownedPokemon.collectAsState()
    val searchQuery by collectionViewModel.searchQuery.collectAsState()
    val selectedIndex by collectionViewModel.selectedIndex.collectAsState()
    val listState = rememberLazyListState()

    val nav = rememberHandheldNavigationController(
        initialIndex = selectedIndex,
        itemCount = { ownedPokemon.size + 2 }, // SearchBar + Register + List
        onActivate = { index ->
            when (index) {
                0 -> keyboardController.open()
                1 -> onAddClick()
                else -> {
                    val actualIndex = index - 2
                    if (actualIndex in ownedPokemon.indices) {
                        onItemClick(ownedPokemon[actualIndex].id)
                    }
                }
            }
        }
    )
    SideEffect {
        onUp {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else {
                nav.moveUp()
            }
        }

        onDown {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else {
                nav.moveDown()
            }
        }

        onLeft {
            if (keyboardController.isVisible) {
                keyboardController.handleLeft()
            }
        }

        onRight {
            if (keyboardController.isVisible) {
                keyboardController.handleRight()
            }
        }

        onA {
            if (keyboardController.isVisible) {
                keyboardController.handleA(searchQuery) { key ->
                    val currentQuery = collectionViewModel.searchQuery.value
                    when (key) {
                        "SPACE" -> collectionViewModel.updateSearchQuery(currentQuery + " ")
                        "DELETE" -> {
                            if (currentQuery.isNotEmpty()) {
                                collectionViewModel.updateSearchQuery(currentQuery.dropLast(1))
                            }
                        }
                        else -> {
                            collectionViewModel.updateSearchQuery(currentQuery + key)
                        }
                    }
                }
            } else {
                nav.activate()
            }
        }

        onB {
            if (keyboardController.isVisible) {
                keyboardController.close()
                nav.setIndex(0) // Return focus to search bar
            } else {
                onBack()
            }
        }
    }
    // Sync UI selection state back to ViewModel if needed for state restoration
    LaunchedEffect(nav.selectedIndex) {
        collectionViewModel.updateSelectedIndex(nav.selectedIndex)
    }

    HandheldListSync(
        listState = listState,
        selectedIndex = nav.selectedIndex,
        listIndexMapping = { if (it == 0) null else it - 1 },
        totalItems = ownedPokemon.size + 1
    )


        Column(modifier = Modifier.fillMaxSize()) {
                TerminalPathIndicator(path = "/BATTLE/Roster")
                TerminalHeader(text = "my collection")

                SearchBar(
                    query = searchQuery,
                    selected = nav.selectedIndex == 0
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
                        RegisterSpecimenItem(
                            selected = nav.selectedIndex == 1
                        )
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
                            selected = nav.selectedIndex == index + 2,
                            pokedexViewModel = pokedexViewModel
                        )
                    }
                }
        }
    }


@Composable
fun RegisterSpecimenItem(selected: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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

            // Sprite resolution through unified pipeline
            val spriteUrl = pokedexViewModel.spriteProvider.getSpriteUrl(
                id = owned.speciesId,
                isShiny = owned.isShiny,
                isShadow = owned.isShadow,
                isPurified = owned.isPurified
            )
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
