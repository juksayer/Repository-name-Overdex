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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun SpecimensScreen(
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onItemClick: (String) -> Unit,
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
            if (selectedIndex in ownedPokemon.indices) {
                onItemClick(ownedPokemon[selectedIndex].id)
            }
        },
        onB = { onBack() },
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "specimens")

            Text(
                text = "${ownedPokemon.size} REGISTERED",
                color = TerminalDimGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(ownedPokemon) { index, owned ->
                    var species by remember(owned.speciesId) { mutableStateOf<Pokemon?>(null) }
                    LaunchedEffect(owned.speciesId) {
                        species = pokedexViewModel.getPokemonById(owned.speciesId)
                    }

                    SpecimenListItem(
                        owned = owned,
                        species = species,
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            onItemClick(owned.id)
                        }
                    )
                }
            }
        }
    }
    
    LaunchedEffect(selectedIndex) {
        if (ownedPokemon.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
}

@Composable
fun SpecimenListItem(
    owned: OwnedPokemon,
    species: Pokemon?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen else TerminalBlack,
            contentColor = if (selected) TerminalBlack else TerminalGreen
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selected) "▶" else " ",
                    color = if (selected) TerminalBlack else TerminalGreen,
                    fontSize = 14.sp,
                    modifier = Modifier.width(20.dp)
                )

                Text(
                    text = species?.name ?: "Unknown",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (owned.cp != null) {
                    Text(
                        text = "CP ${owned.cp}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier.padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (owned.isShadow) {
                    Text(
                        text = "SHADOW",
                        color = if (selected) TerminalBlack else Color(0xFFBC13FE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column {
                    if (!owned.fastMove.isNullOrBlank()) {
                        Text(
                            text = "FAST: ${owned.fastMove}",
                            fontSize = 12.sp,
                            color = if (selected) TerminalBlack else TerminalDimGreen
                        )
                    }
                    
                    val chargedMoves = listOfNotNull(owned.chargedMove1, owned.chargedMove2)
                        .filter { it.isNotBlank() }
                    
                    if (chargedMoves.isNotEmpty()) {
                        Text(
                            text = "CHARGED: ${chargedMoves.joinToString(" / ")}",
                            fontSize = 12.sp,
                            color = if (selected) TerminalBlack else TerminalDimGreen
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SpecimenListItemPreview() {
    SpecimenListItem(
        owned = OwnedPokemon(
            speciesId = 1,
            cp = 1496,
            isShadow = true,
            fastMove = "Vine Whip",
            chargedMove1 = "Frenzy Plant",
            chargedMove2 = "Sludge Bomb"
        ),
        species = null, // simplified for preview
        selected = true,
        onClick = {}
    )
}
