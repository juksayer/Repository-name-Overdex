package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.OwnedPokemonBinder
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.PokemonTypeIcon
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.components.rememberHandheldNavigationController
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OwnedBinderTurnDirection {
    PREVIOUS,
    NEXT,
}

private data class OwnedBinderCardData(
    val owned: OwnedPokemon,
    val species: Pokemon?,
    val spriteUrl: String,
)

/**
 * The trainer's owned cards presented through the same 18-pocket binder used by
 * the Pokédex. Each pocket points to one stable [OwnedPokemon.id].
 */
@Composable
fun MyCollectionScreen(
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    binder: OwnedPokemonBinder,
    onItemClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onBack: () -> Unit,
    keyboardController: TerminalKeyboardController,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onLeftLong: (() -> Unit) -> Unit = {},
    onLeftPressChanged: ((Boolean) -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onRightLong: (() -> Unit) -> Unit = {},
    onRightPressChanged: ((Boolean) -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onKeyActivated: ((String) -> Unit) -> Unit = {},
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
) {
    val allOwnedPokemon by collectionViewModel.ownedPokemon.collectAsState()
    val searchQuery by collectionViewModel.searchQuery.collectAsState()

    val speciesIds = remember(allOwnedPokemon) {
        allOwnedPokemon.map { it.speciesId }.distinct()
    }
    var speciesById by remember { mutableStateOf<Map<Int, Pokemon>>(emptyMap()) }
    LaunchedEffect(speciesIds) {
        speciesById = speciesIds.mapNotNull { id ->
            pokedexViewModel.getPokemonById(id)?.let { id to it }
        }.toMap()
    }

    val cards = remember(allOwnedPokemon, speciesById, binder) {
        allOwnedPokemon
            .filter(binder::contains)
            .map { owned ->
                OwnedBinderCardData(
                    owned = owned,
                    species = speciesById[owned.speciesId],
                    spriteUrl = pokedexViewModel.spriteProvider.getSpriteUrl(
                        id = owned.speciesId,
                        isShiny = owned.isShiny,
                        isShadow = owned.isShadow,
                        isPurified = owned.isPurified,
                    ),
                )
            }
    }

    val nav = rememberHandheldNavigationController(
        key = binder.routeKey,
        itemCount = { cards.size },
        onActivate = { index ->
            cards.getOrNull(index)?.let { onItemClick(it.owned.id) }
        },
    )

    val spreadCount = maxOf(1, (cards.size + 17) / 18)
    val maxSpreadIndex = spreadCount - 1
    var spreadIndex by rememberSaveable(binder.routeKey) { mutableIntStateOf(0) }
    var leftVisibleColumns by remember { mutableStateOf((0..2).toSet()) }
    var rightVisibleColumns by remember { mutableStateOf((0..2).toSet()) }
    var displayedItemIndices by remember { mutableStateOf((0 until 18).toList()) }
    var pageIsTurning by remember { mutableStateOf(false) }
    var pageAnimationMillis by remember { mutableIntStateOf(80) }
    var acceleratedDirection by remember { mutableStateOf<OwnedBinderTurnDirection?>(null) }
    var acceleratedStopRequested by remember { mutableStateOf(false) }
    val pageTurnScope = rememberCoroutineScope()

    LaunchedEffect(spreadCount) {
        spreadIndex = spreadIndex.coerceIn(0, maxSpreadIndex)
        displayedItemIndices = ((spreadIndex * 18) until (spreadIndex * 18 + 18)).toList()
        leftVisibleColumns = (0..2).toSet()
        rightVisibleColumns = (0..2).toSet()
        pageIsTurning = false
        if (cards.isNotEmpty()) {
            nav.setIndex(
                nav.selectedIndex.coerceIn(
                    spreadIndex * 18,
                    minOf(cards.lastIndex, spreadIndex * 18 + 17),
                ),
            )
        }
    }

    LaunchedEffect(searchQuery, binder) {
        spreadIndex = 0
        displayedItemIndices = (0 until 18).toList()
        leftVisibleColumns = (0..2).toSet()
        rightVisibleColumns = (0..2).toSet()
        pageIsTurning = false
        if (cards.isNotEmpty()) nav.setIndex(0)
    }

    suspend fun refreshColumn(
        targetSpread: Int,
        pageOffset: Int,
        column: Int,
        transitionMillis: Int = 80,
    ) {
        pageAnimationMillis = transitionMillis.coerceAtLeast(18)
        if (pageOffset == 0) {
            leftVisibleColumns = leftVisibleColumns - column
        } else {
            rightVisibleColumns = rightVisibleColumns - column
        }
        delay(pageAnimationMillis.toLong())

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
        delay(pageAnimationMillis.toLong())
    }

    suspend fun refreshPage(
        targetSpread: Int,
        pageOffset: Int,
        columns: IntProgression,
        transitionMillis: Int = 80,
    ) {
        columns.forEach { column ->
            refreshColumn(targetSpread, pageOffset, column, transitionMillis)
        }
    }

    fun turnToNextSpread() {
        if (pageIsTurning || spreadIndex >= maxSpreadIndex) return
        val nextSpread = spreadIndex + 1
        pageTurnScope.launch {
            pageIsTurning = true
            refreshPage(nextSpread, pageOffset = 9, columns = 2 downTo 0)
            refreshPage(nextSpread, pageOffset = 0, columns = 2 downTo 0)
            spreadIndex = nextSpread
            if (cards.isNotEmpty()) nav.setIndex(nextSpread * 18)
            pageAnimationMillis = 80
            pageIsTurning = false
        }
    }

    fun turnToPreviousSpread() {
        if (pageIsTurning || spreadIndex <= 0) return
        val nextSpread = spreadIndex - 1
        pageTurnScope.launch {
            pageIsTurning = true
            refreshPage(nextSpread, pageOffset = 0, columns = 0..2)
            refreshPage(nextSpread, pageOffset = 9, columns = 0..2)
            spreadIndex = nextSpread
            if (cards.isNotEmpty()) nav.setIndex(nextSpread * 18)
            pageAnimationMillis = 80
            pageIsTurning = false
        }
    }

    fun beginAcceleratedPaging(direction: OwnedBinderTurnDirection) {
        if (keyboardController.isVisible || pageIsTurning || acceleratedDirection != null) return
        val canAdvance = when (direction) {
            OwnedBinderTurnDirection.NEXT -> spreadIndex < maxSpreadIndex
            OwnedBinderTurnDirection.PREVIOUS -> spreadIndex > 0
        }
        if (!canAdvance) return

        acceleratedDirection = direction
        acceleratedStopRequested = false
        pageIsTurning = true
        pageTurnScope.launch {
            var transitionMillis = 72
            var moved = false
            try {
                while (!acceleratedStopRequested) {
                    val targetSpread = when (direction) {
                        OwnedBinderTurnDirection.NEXT -> spreadIndex + 1
                        OwnedBinderTurnDirection.PREVIOUS -> spreadIndex - 1
                    }
                    if (targetSpread !in 0..maxSpreadIndex) break

                    val turningPageOffset = if (direction == OwnedBinderTurnDirection.NEXT) 9 else 0
                    val turningColumns = if (direction == OwnedBinderTurnDirection.NEXT) 2 downTo 0 else 0..2
                    refreshPage(targetSpread, turningPageOffset, turningColumns, transitionMillis)
                    spreadIndex = targetSpread
                    moved = true
                    transitionMillis = (transitionMillis * 0.76f).toInt().coerceAtLeast(18)
                    if (!acceleratedStopRequested) {
                        delay((transitionMillis * 2L).coerceAtLeast(28L))
                    }
                }

                if (moved) {
                    val restingPageOffset = if (direction == OwnedBinderTurnDirection.NEXT) 0 else 9
                    val restingColumns = if (direction == OwnedBinderTurnDirection.NEXT) 2 downTo 0 else 0..2
                    refreshPage(spreadIndex, restingPageOffset, restingColumns, transitionMillis = 55)
                    if (cards.isNotEmpty()) nav.setIndex(spreadIndex * 18)
                }
            } finally {
                leftVisibleColumns = (0..2).toSet()
                rightVisibleColumns = (0..2).toSet()
                pageAnimationMillis = 80
                pageIsTurning = false
                acceleratedDirection = null
                acceleratedStopRequested = false
            }
        }
    }

    fun stopAcceleratedPaging(direction: OwnedBinderTurnDirection) {
        if (acceleratedDirection == direction) acceleratedStopRequested = true
    }

    fun handleActivatedKey(key: String) {
        val currentQuery = collectionViewModel.searchQuery.value
        when (key) {
            "SPACE" -> collectionViewModel.updateSearchQuery(currentQuery + " ")
            "DELETE" -> if (currentQuery.isNotEmpty()) {
                collectionViewModel.updateSearchQuery(currentQuery.dropLast(1))
            }
            else -> collectionViewModel.updateSearchQuery(currentQuery + key)
        }
    }

    val selectedCard = cards.getOrNull(nav.selectedIndex)
    SideEffect {
        onLcdContentUpdate {
            OwnedBinderSelectionDetail(
                card = selectedCard,
                binder = binder,
                position = if (selectedCard == null) 0 else nav.selectedIndex + 1,
                total = cards.size,
                query = searchQuery,
                onOpenDetail = { onItemClick(it.owned.id) },
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
            } else if (
                !pageIsTurning &&
                nav.selectedIndex < minOf(cards.size, (spreadIndex + 1) * 18) - 1
            ) {
                nav.moveDown()
            }
        }
        onLeft {
            if (keyboardController.isVisible) keyboardController.handleLeft() else turnToPreviousSpread()
        }
        onLeftLong { beginAcceleratedPaging(OwnedBinderTurnDirection.PREVIOUS) }
        onLeftPressChanged { pressed ->
            if (!pressed) stopAcceleratedPaging(OwnedBinderTurnDirection.PREVIOUS)
        }
        onRight {
            if (keyboardController.isVisible) keyboardController.handleRight() else turnToNextSpread()
        }
        onRightLong { beginAcceleratedPaging(OwnedBinderTurnDirection.NEXT) }
        onRightPressChanged { pressed ->
            if (!pressed) stopAcceleratedPaging(OwnedBinderTurnDirection.NEXT)
        }
        onA {
            if (keyboardController.isVisible) {
                keyboardController.handleA(searchQuery) { handleActivatedKey(it) }
            } else {
                nav.activate()
            }
        }
        onB {
            if (!keyboardController.handleB()) onBack()
        }
        onSelect { keyboardController.open() }
        onStart { onAddClick() }
        onKeyActivated { key ->
            if (keyboardController.isVisible) handleActivatedKey(key)
        }
    }

    val leftPageSlots = displayedItemIndices.take(9).map { itemIndex ->
        Pair(cards.getOrNull(itemIndex), nav.selectedIndex == itemIndex)
    }
    val rightPageSlots = displayedItemIndices.drop(9).take(9).map { itemIndex ->
        Pair(cards.getOrNull(itemIndex), nav.selectedIndex == itemIndex)
    }

    BinderSpreadLayout(
        leftPageCards = leftPageSlots,
        rightPageCards = rightPageSlots,
        leftVisibleColumns = leftVisibleColumns,
        rightVisibleColumns = rightVisibleColumns,
        columnAnimationMillis = pageAnimationMillis,
        onCardClick = { card ->
            val visibleIndex = displayedItemIndices.firstOrNull { index ->
                cards.getOrNull(index)?.owned?.id == card.owned.id
            }
            if (!pageIsTurning && visibleIndex != null) nav.handleTouch(visibleIndex)
        },
        modifier = Modifier.fillMaxSize(),
    ) { card, selected, cardModifier ->
        OwnedPokemonBinderCard(
            card = card,
            selected = selected,
            modifier = cardModifier,
        )
    }
}

@Composable
private fun OwnedPokemonBinderCard(
    card: OwnedBinderCardData,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TerminalGreen else TerminalDimGreen.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TerminalGreen.copy(alpha = 0.15f) else TerminalBlack,
            contentColor = TerminalGreen,
        ),
        shape = RoundedCornerShape(3.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.owned.cp?.let { "CP $it" } ?: card.species?.formattedId.orEmpty(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) TerminalGreen else TerminalDimGreen,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    card.species?.types?.forEach { type ->
                        PokemonTypeIcon(
                            type = type,
                            style = TypeIconStyle.OVERDEX,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 1.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = card.spriteUrl,
                    contentDescription = card.owned.displayName ?: card.species?.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp),
                    contentScale = ContentScale.Fit,
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (card.owned.isFavorite) BinderCardMarker("★", TerminalGreen)
                    if (card.owned.isShiny) BinderCardMarker("✦", TerminalPurple)
                    if (card.owned.isShadow) BinderCardMarker("S", Color(0xFFBC13FE))
                    if (card.owned.isPurified) BinderCardMarker("P", Color(0xFF00E5FF))
                }
            }
        }
    }
}

@Composable
private fun BinderCardMarker(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 7.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .background(TerminalBlack.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun OwnedBinderSelectionDetail(
    card: OwnedBinderCardData?,
    binder: OwnedPokemonBinder,
    position: Int,
    total: Int,
    query: String,
    onOpenDetail: (OwnedBinderCardData) -> Unit,
) {
    if (card == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = binder.displayName,
                color = TerminalGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = if (query.isBlank()) "EMPTY BINDER" else "NO MATCHING CARDS",
                color = TerminalDimGreen,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "[START] ADD CARD",
                color = TerminalGreen.copy(alpha = 0.75f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        return
    }

    val owned = card.owned
    val species = card.species
    val displayName = owned.displayName?.takeIf { it.isNotBlank() } ?: species?.name ?: "UNKNOWN"
    val status = buildList {
        if (owned.isFavorite) add("FAVORITE")
        if (owned.isShiny) add("SHINY")
        if (owned.isShadow) add("SHADOW")
        if (owned.isPurified) add("PURIFIED")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${binder.displayName}  $position/$total",
            modifier = Modifier.fillMaxWidth(),
            color = TerminalDimGreen,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { onOpenDetail(card) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AsyncImage(
                model = card.spriteUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(1.dp),
                contentScale = ContentScale.Fit,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                com.example.overdex.ui.components.TypeIconText(
                    text = displayName.uppercase(),
                    modifier = Modifier.fillMaxWidth(),
                    color = TerminalGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                )
                if (owned.displayName?.takeIf { it.isNotBlank() } != null && species != null) {
                    Text(
                        text = species.name.uppercase(),
                        color = TerminalDimGreen,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    species?.types?.forEach { type ->
                        PokemonTypeIcon(
                            type = type,
                            style = TypeIconStyle.OVERDEX,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        OwnedLcdLine(
            "CP",
            buildString {
                append(owned.cp ?: "---")
                if (status.isNotEmpty()) append("  ${status.joinToString("/")}")
            },
        )
        OwnedLcdLine("FAST", owned.fastMove ?: "UNDISCLOSED")
        OwnedLcdLine("CHG 1", owned.chargedMove1 ?: "UNDISCLOSED")
        OwnedLcdLine("CHG 2", owned.chargedMove2 ?: "UNDISCLOSED")
    }
}

@Composable
private fun OwnedLcdLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = TerminalGreen.copy(alpha = 0.82f),
        fontSize = 8.sp,
        lineHeight = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Monospace,
    )
}
