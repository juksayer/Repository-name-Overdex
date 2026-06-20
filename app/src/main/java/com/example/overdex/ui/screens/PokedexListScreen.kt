package com.example.overdex.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PokedexListScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSelect: () -> Unit,
    onPokemonClick: (Int) -> Unit,
) {
    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    PokedexFrame(
        onUp = {
            scope.launch {
                listState.animateScrollBy(-500f)
            }
        },
        onDown = {
            scope.launch {
                listState.animateScrollBy(500f)
            }
        },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onSelect = onSelect,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(query = searchQuery) {
                viewModel.updateSearchQuery(it)
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
                    pokemonItems[index]?.let { pokemon ->
                        PokemonListItem(pokemon = pokemon) { onPokemonClick(pokemon.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun PokemonListItem(pokemon: Pokemon, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TerminalDimGreen, CardDefaults.shape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = TerminalBlack,
            contentColor = TerminalGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sprite
            AsyncImage(
                model = pokemon.spriteUrl,
                contentDescription = pokemon.name,
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = pokemon.formattedId,
                fontSize = 12.sp,
                color = TerminalDimGreen,
                modifier = Modifier.width(40.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pokemon.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalGreen
                )
                Text(
                    text = pokemon.region,
                    fontSize = 11.sp,
                    color = TerminalDimGreen
                )
            }
            
            Row {
                pokemon.types.forEach { type ->
                    TypeBadge(type = type)
                }
            }
        }
    }
}
