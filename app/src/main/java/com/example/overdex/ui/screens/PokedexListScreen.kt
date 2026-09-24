package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Work Order — Pokédex Binder Search
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
    onSelect: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onKeyActivated: ((String) -> Unit) -> Unit = {},
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {}
) {
    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchRequest by viewModel.searchRequest.collectAsState()

    // Work Order — Pokédex Binder Search: Direct item index without legacy index-0 SearchBar focus
    val nav = rememberHandheldNavigationController(
        itemCount = { pokemonItems.itemCount },
        onActivate = { index ->
            if (index in 0 until pokemonItems.itemCount) {
                pokemonItems[index]?.let { onPokemonClick(it.id) }
            }
        }
    )

    val spreadCount = maxOf(1, (pokemonItems.itemCount + 17) / 18)
    val maxSpreadIndex = spreadCount - 1
    var spreadIndex by rememberSaveable { mutableIntStateOf(0) }
    var leftVisibleColumns by remember { mutableStateOf((0..2).toSet()) }
    var rightVisibleColumns by remember { mutableStateOf((0..2).toSet()) }
    var displayedItemIndices by remember { mutableStateOf((0 until 18).toList()) }
    var pageIsTurning by remember { mutableStateOf(false) }
    val pageTurnScope = rememberCoroutineScope()

    LaunchedEffect(spreadCount) {
        spreadIndex = spreadIndex.coerceIn(0, maxSpreadIndex)
        leftVisibleColumns = (0..2).toSet()
        rightVisibleColumns = (0..2).toSet()
        displayedItemIndices = ((spreadIndex * 18) until (spreadIndex * 18 + 18)).toList()
        pageIsTurning = false
    }

    suspend fun refreshColumn(targetSpread: Int, pageOffset: Int, column: Int) {
        if (pageOffset == 0) {
            leftVisibleColumns = leftVisibleColumns - column
        } else {
            rightVisibleColumns = rightVisibleColumns - column
        }
        delay(90)

        val targetStart = targetSpread * 18 + pageOffset
        displayedItemIndices = displayedItemIndices.toMutableList().also { indices ->
            for (row in 0..2) {
                val slot = pageOffset + row * 3 + column
                indices[slot] = targetStart + row * 3 + column
            }
        }

        if (pageOffset == 0) {
            leftVisibleColumns = leftVisibleColumns + column
        } else {
            rightVisibleColumns = rightVisibleColumns + column
        }
        delay(90)
    }

    fun turnPagesLeft() {
        if (pageIsTurning || spreadIndex >= maxSpreadIndex) return
        val nextSpread = spreadIndex + 1
        pageTurnScope.launch {
            pageIsTurning = true
            for (column in 2 downTo 0) {
                refreshColumn(nextSpread, pageOffset = 9, column = column)
            }
            for (column in 2 downTo 0) {
                refreshColumn(nextSpread, pageOffset = 0, column = column)
            }

            spreadIndex = nextSpread
            nav.setIndex(nextSpread * 18)
            pageIsTurning = false
        }
    }

    fun turnPagesRight() {
        if (pageIsTurning || spreadIndex <= 0) return
        val nextSpread = spreadIndex - 1
        pageTurnScope.launch {
            pageIsTurning = true
            for (column in 0..2) {
                refreshColumn(nextSpread, pageOffset = 0, column = column)
            }
            for (column in 0..2) {
                refreshColumn(nextSpread, pageOffset = 9, column = column)
            }

            spreadIndex = nextSpread
            nav.setIndex(nextSpread * 18)
            pageIsTurning = false
        }
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

    // The LCD follows the selected Binder pocket. Search temporarily takes over
    // through TerminalKeyboard, then this detail panel returns when it closes.
    val selectedPokemon = if (nav.selectedIndex in 0 until pokemonItems.itemCount) {
        pokemonItems[nav.selectedIndex]
    } else {
        null
    }
    SideEffect {
        onLcdContentUpdate {
            BinderSelectionDetail(
                pokemon = selectedPokemon,
                query = searchQuery,
                activeFilters = searchRequest.activeFilters.map { it.label },
                onOpenDetail = { onPokemonClick(it.id) }
            )
        }

        onUp {
            if (keyboardController.isVisible) {
                keyboardController.handleUp()
            } else if (!pageIsTurning && nav.selectedIndex > spreadIndex * 18) {
                nav.moveUp()
            }
        }
        onDown {
            if (keyboardController.isVisible) {
                keyboardController.handleDown()
            } else if (!pageIsTurning && nav.selectedIndex < minOf(pokemonItems.itemCount, (spreadIndex + 1) * 18) - 1) {
                nav.moveDown()
            }
        }
        onLeft {
            if (keyboardController.isVisible) {
                keyboardController.handleLeft()
            } else {
                turnPagesRight()
            }
        }
        onRight {
            if (keyboardController.isVisible) {
                keyboardController.handleRight()
            } else {
                turnPagesLeft()
            }
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
        // Work Order — Pokédex Binder Search: SELECT is the single Search invocation mechanism
        onSelect {
            keyboardController.open()
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

    val leftPageSlots = displayedItemIndices.take(9).map { itemIndex ->
        Pair(
            if (itemIndex < pokemonItems.itemCount) pokemonItems[itemIndex] else null,
            nav.selectedIndex == itemIndex
        )
    }
    val rightPageSlots = displayedItemIndices.drop(9).take(9).map { itemIndex ->
        Pair(
            if (itemIndex < pokemonItems.itemCount) pokemonItems[itemIndex] else null,
            nav.selectedIndex == itemIndex
        )
    }

    BinderSpread(
        leftPageCards = leftPageSlots,
        rightPageCards = rightPageSlots,
        leftVisibleColumns = leftVisibleColumns,
        rightVisibleColumns = rightVisibleColumns,
        onCardClick = { pokemon ->
            val visibleIndex = displayedItemIndices
                .firstOrNull { it < pokemonItems.itemCount && pokemonItems[it]?.id == pokemon.id }
            if (!pageIsTurning && visibleIndex != null) nav.handleTouch(visibleIndex)
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** The LCD follows the selected Binder pocket and accepts direct scrolling. */
@Composable
private fun BinderSelectionDetail(
    pokemon: Pokemon?,
    query: String,
    activeFilters: List<String>,
    onOpenDetail: (Pokemon) -> Unit
) {
    if (pokemon == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "SELECT A SPECIMEN",
                color = TerminalGreen.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDetail(pokemon) },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = pokemon.name.uppercase(),
                modifier = Modifier.fillMaxWidth(),
                color = TerminalGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsyncImage(
                    model = pokemon.spriteUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "${pokemon.formattedId}  ${pokemon.genus.ifBlank { pokemon.region }}",
                    modifier = Modifier.weight(1f),
                    color = TerminalDimGreen,
                    fontSize = 9.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            pokemon.types.forEach { type ->
                TypeBadge(type = type, style = TypeIconStyle.OVERDEX, modifier = Modifier.scale(0.7f))
            }
        }

        if (query.isNotEmpty() || activeFilters.isNotEmpty()) {
            Text(
                text = buildString {
                    if (query.isNotEmpty()) append("QUERY: $query")
                    if (activeFilters.isNotEmpty()) {
                        if (isNotEmpty()) append("  ")
                        append(activeFilters.joinToString(" | "))
                    }
                },
                color = TerminalDimGreen,
                fontSize = 8.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        if (pokemon.description.isNotBlank()) {
            Text(
                text = pokemon.description,
                color = TerminalGreen.copy(alpha = 0.8f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        BinderDetailSection("FAST", pokemon.fastMoves.map { it.name })
        BinderDetailSection("CHARGED", pokemon.chargedMoves.map { it.name })
        Text(
            text = "ATK ${pokemon.baseAttack}  DEF ${pokemon.baseDefense}  STA ${pokemon.baseStamina}",
            color = TerminalDimGreen,
            fontSize = 8.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun BinderDetailSection(label: String, moves: List<String>) {
    if (moves.isEmpty()) return
    Text(
        text = "$label: ${moves.joinToString(", ")}",
        color = TerminalGreen.copy(alpha = 0.8f),
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

// Work Order: Brick 1 — Pokédex Binder Presentation
@Composable
fun BinderSpread(
    leftPageCards: List<Pair<Pokemon?, Boolean>>,
    rightPageCards: List<Pair<Pokemon?, Boolean>>,
    leftVisibleColumns: Set<Int> = (0..2).toSet(),
    rightVisibleColumns: Set<Int> = (0..2).toSet(),
    onCardClick: ((Pokemon) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = TerminalBlack,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TerminalDimGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Page
            BinderPage(
                cards = leftPageCards,
                visibleColumns = leftVisibleColumns,
                onCardClick = onCardClick,
                modifier = Modifier.weight(1f)
            )

            // Central Spine Division Line / Binder Rings
            Box(
                modifier = Modifier
                    .width(4.dp)
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
                                .size(width = 6.dp, height = 2.dp)
                                .background(TerminalDimGreen.copy(alpha = 0.4f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            // Right Page
            BinderPage(
                cards = rightPageCards,
                visibleColumns = rightVisibleColumns,
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
    visibleColumns: Set<Int> = (0..2).toSet(),
    onCardClick: ((Pokemon) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0D08))
            .padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (rowIndex in 0 until 3) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (colIndex in 0 until 3) {
                    val slotIndex = rowIndex * 3 + colIndex
                    val (pokemon, selected) = if (slotIndex < cards.size) cards[slotIndex] else Pair(null, false)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val playingCardAspectRatio = 5f / 7f
                        val availableAspectRatio = if (maxHeight.value > 0f) {
                            maxWidth.value / maxHeight.value
                        } else {
                            playingCardAspectRatio
                        }
                        val playingCardModifier = if (availableAspectRatio < playingCardAspectRatio) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(playingCardAspectRatio)
                        } else {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(playingCardAspectRatio)
                        }

                        BinderPocketVisibility(
                            visible = colIndex in visibleColumns,
                            modifier = playingCardModifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                    .padding(1.dp)
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
    }
}

@Composable
private fun BinderPocketVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(80)) + expandHorizontally(
            animationSpec = tween(80),
            expandFrom = Alignment.End
        ),
        exit = fadeOut(tween(80)) + shrinkHorizontally(
            animationSpec = tween(80),
            shrinkTowards = Alignment.Start
        )
    ) {
        content()
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

            // The Binder is a visual index. Names belong to its LCD context and detail view;
            // the reclaimed footer becomes a larger sprite window.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 1.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = pokemon.spriteUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.fillMaxSize().padding(1.dp),
                    contentScale = ContentScale.Fit
                )
            }
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
