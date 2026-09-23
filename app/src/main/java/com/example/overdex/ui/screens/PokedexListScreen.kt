package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.SearchBar
import com.example.overdex.ui.components.TypeBadge
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun PokedexListScreen(
    viewModel: PokedexViewModel,
    onBack: () -> Unit,
    onPokemonClick: (Int) -> Unit,
    keyboardController: TerminalKeyboardController,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onKeyActivated: ((String) -> Unit) -> Unit = {}
) {
    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchRequest by viewModel.searchRequest.collectAsState()
    val listState = rememberLazyListState()

    val nav = rememberHandheldNavigationController(
        itemCount = { pokemonItems.itemCount + 1 }, // +1 for SearchBar
        onActivate = { index ->
            if (index == 0) {
                keyboardController.open()
            } else {
                pokemonItems[index - 1]?.let { onPokemonClick(it.id) }
            }
        }
    )

    HandheldListSync(
        listState = listState,
        selectedIndex = nav.selectedIndex,
        listIndexMapping = { if (it == 0) null else it - 1 },
        totalItems = pokemonItems.itemCount
    )

    LaunchedEffect(searchQuery, searchRequest) {
        nav.setIndex(0)
    }

    fun handleActivatedKey(key: String) {
        when (key) {
            "SPACE" -> viewModel.updateSearchQuery(searchQuery + " ")
            "DELETE" -> {
                if (searchQuery.isNotEmpty()) {
                    viewModel.updateSearchQuery(searchQuery.dropLast(1))
                }
            }
            else -> {
                if (key.startsWith("TYPE:")) {
                    val typeName = key.removePrefix("TYPE:")
                    viewModel.updateSearchQuery(searchQuery + typeName + " ")
                } else {
                    viewModel.updateSearchQuery(searchQuery + key)
                }
            }
        }
    }

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
            if (keyboardController.isVisible) keyboardController.handleLeft()
        }
        onRight {
            if (keyboardController.isVisible) keyboardController.handleRight()
        }
        onA {
            if (keyboardController.isVisible) {
                keyboardController.handleA(searchQuery) { handleActivatedKey(it) }
            } else {
                nav.activate()
            }
        }
        onB {
            if (!keyboardController.handleB()) {
                onBack()
            }
        }
        onStart {
            if (!keyboardController.handleStart()) {
                viewModel.startObservation()
            }
        }
        onKeyActivated { key ->
            if (keyboardController.isVisible) {
                handleActivatedKey(key)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TerminalPathIndicator(path = "/OVERDEX")
        
        SearchBar(
            query = searchQuery, 
            selected = nav.selectedIndex == 0
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

        // Work Order: Brick 1 — Pokédex Binder Presentation
        val spreadCount = maxOf(1, (pokemonItems.itemCount + 23) / 24)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = spreadCount,
                key = { spreadIndex -> "spread_$spreadIndex" },
                contentType = { "binder_spread" }
            ) { spreadIndex ->
                val startIndex = spreadIndex * 24

                val leftPageSlots = (0 until 12).map { offset ->
                    val itemIndex = startIndex + offset
                    if (itemIndex < pokemonItems.itemCount) {
                        Pair(pokemonItems[itemIndex], nav.selectedIndex == (itemIndex + 1))
                    } else {
                        Pair(null, false)
                    }
                }

                val rightPageSlots = (0 until 12).map { offset ->
                    val itemIndex = startIndex + 12 + offset
                    if (itemIndex < pokemonItems.itemCount) {
                        Pair(pokemonItems[itemIndex], nav.selectedIndex == (itemIndex + 1))
                    } else {
                        Pair(null, false)
                    }
                }

                BinderSpread(
                    leftPageCards = leftPageSlots,
                    rightPageCards = rightPageSlots,
                    onCardClick = { pokemon -> onPokemonClick(pokemon.id) }
                )
            }
        }
    }
}

// Work Order: Brick 1 — Pokédex Binder Presentation
@Composable
fun BinderSpread(
    leftPageCards: List<Pair<Pokemon?, Boolean>>,
    rightPageCards: List<Pair<Pokemon?, Boolean>>,
    onCardClick: ((Pokemon) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalBlack,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TerminalDimGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Page
            BinderPage(
                cards = leftPageCards,
                onCardClick = onCardClick,
                modifier = Modifier.weight(1f)
            )

            // Central Spine Division Line / Binder Rings
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 3.dp)
                                .background(TerminalDimGreen.copy(alpha = 0.4f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            // Right Page
            BinderPage(
                cards = rightPageCards,
                onCardClick = onCardClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Work Order: Brick 1 — Pokédex Binder Presentation
@Composable
fun BinderPage(
    cards: List<Pair<Pokemon?, Boolean>>,
    onCardClick: ((Pokemon) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0D08))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (rowIndex in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (colIndex in 0 until 3) {
                    val slotIndex = rowIndex * 3 + colIndex
                    val (pokemon, selected) = if (slotIndex < cards.size) cards[slotIndex] else Pair(null, false)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .border(1.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(2.dp)
                    ) {
                        if (pokemon != null) {
                            PokedexCard(
                                pokemon = pokemon,
                                selected = selected,
                                modifier = if (onCardClick != null) {
                                    Modifier.clickable { onCardClick(pokemon) }
                                } else Modifier
                            )
                        } else {
                            EmptyCardPocket()
                        }
                    }
                }
            }
        }
    }
}

// Work Order: Brick 1 — Pokédex Binder Presentation
@Composable
fun PokedexCard(
    pokemon: Pokemon,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TerminalGreen else TerminalDimGreen.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen.copy(alpha = 0.15f) else TerminalBlack,
            contentColor = TerminalGreen
        ),
        shape = RoundedCornerShape(3.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Dex # and Type Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pokemon.formattedId,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) TerminalGreen else TerminalDimGreen
                )
                Row {
                    pokemon.types.forEach { type ->
                        TypeBadge(
                            type = type,
                            style = TypeIconStyle.OVERDEX,
                            modifier = Modifier.scale(0.65f)
                        )
                    }
                }
            }

            // Sprite Art Window
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = pokemon.spriteUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Footer: Species Name
            Text(
                text = pokemon.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) TerminalGreen else TerminalGreen.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Work Order: Brick 1 — Pokédex Binder Presentation
@Composable
fun EmptyCardPocket(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 0.5.dp,
                color = TerminalDimGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp)
            )
            .background(Color.Black.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "·",
            color = TerminalDimGreen.copy(alpha = 0.2f),
            fontSize = 10.sp
        )
    }
}

@Composable
fun PokemonListItem(pokemon: Pokemon, selected: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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
                text = if (selected) ">" else " ",
                color = if (selected) TerminalBlack else TerminalGreen,
                fontSize = 14.sp,
                modifier = Modifier.width(20.dp)
            )

            // Sprite resolution through unified pipeline
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
