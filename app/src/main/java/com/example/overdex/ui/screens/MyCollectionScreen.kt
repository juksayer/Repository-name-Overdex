package com.example.overdex.ui.screens

import androidx.compose.foundation.clickable
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
    val listState = rememberLazyListState()
    var selectedIndex by remember { mutableIntStateOf(0) }

    PokedexFrame(
        onUp = { if (selectedIndex > 0) selectedIndex-- },
        onDown = { if (selectedIndex < ownedPokemon.size - 1) selectedIndex++ },
        onA = {
            if (ownedPokemon.isNotEmpty()) {
                onItemClick(ownedPokemon[selectedIndex].id)
            } else {
                onAddClick()
            }
        },
        onB = onBack,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "my collection")

            if (ownedPokemon.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Pokémon yet.", color = TerminalDimGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("A Add Pokémon", color = TerminalGreen, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ownedPokemon) { index, owned ->
                        var species by remember { mutableStateOf<Pokemon?>(null) }
                        LaunchedEffect(owned.speciesId) {
                            species = pokedexViewModel.getPokemonById(owned.speciesId)
                        }

                        OwnedPokemonListItem(
                            owned = owned,
                            species = species,
                            selected = selectedIndex == index
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OwnedPokemonListItem(
    owned: OwnedPokemon,
    species: Pokemon?,
    selected: Boolean
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
            if (species != null) {
                AsyncImage(
                    model = species.spriteUrl,
                    contentDescription = species.name,
                    modifier = Modifier.size(50.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(modifier = Modifier.size(50.dp))
            }

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
