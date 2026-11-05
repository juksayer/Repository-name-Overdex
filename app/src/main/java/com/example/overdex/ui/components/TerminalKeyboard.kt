package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import com.example.overdex.ui.keyboard.SwipePoint
import com.example.overdex.ui.keyboard.TerminalSwipeRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen

/** The app supplies its already-loaded local species/move vocabulary once. */
val LocalTerminalSwipeWords = staticCompositionLocalOf<List<String>> { emptyList() }

/** Shared touch keyboard. A swipe proposes names; it never silently enters one. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TerminalKeyboard(
    layout: List<List<String>>,
    currentRow: Int,
    currentColumn: Int,
    modifier: Modifier = Modifier,
    isFocused: Boolean = true,
    onKeyActivated: ((String) -> Unit)? = null,
    swipeWords: List<String> = LocalTerminalSwipeWords.current,
    inputRevision: Int = 0,
) {
    val rowRequesters = remember(layout) { List(layout.size) { BringIntoViewRequester() } }
    val letterPage = layout == QwertyLayout || layout == LettersLayout
    val recognizer by produceState<TerminalSwipeRecognizer?>(null, swipeWords) {
        value = if (swipeWords.isEmpty()) null else withContext(Dispatchers.Default) {
            TerminalSwipeRecognizer(swipeWords)
        }
    }
    val scope = rememberCoroutineScope()
    val latestKey by rememberUpdatedState(onKeyActivated)
    var suggestions by remember(layout) { mutableStateOf<List<String>>(emptyList()) }
    var matching by remember(layout) { mutableStateOf(false) }
    var noMatch by remember(layout) { mutableStateOf(false) }
    var trail by remember(layout) { mutableStateOf<List<Offset>>(emptyList()) }
    // A newer tap/gesture invalidates an older background comparison.
    var generation by remember { mutableIntStateOf(0) }
    val coordinates = remember(layout) { mutableMapOf<Char, LayoutCoordinates>() }
    var gridCoordinates by remember(layout) { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(inputRevision) {
        generation++
        suggestions = emptyList()
        matching = false
        noMatch = false
    }
    LaunchedEffect(layout, currentRow, isFocused) {
        if (isFocused) rowRequesters.getOrNull(currentRow)?.bringIntoView()
    }
    fun enter(key: String) {
        generation++
        suggestions = emptyList()
        matching = false
        noMatch = false
        latestKey?.invoke(key)
    }
    BoxWithConstraints(modifier) {
        val rowGap = 4.dp
        Column(Modifier.fillMaxSize()) {
            if (letterPage) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("Swipe suggestions"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (suggestions.isEmpty()) {
                        Text(
                            text = when {
                                matching -> "Finding names…"
                                noMatch -> "No name matched · tap to type"
                                recognizer == null -> "Tap to type"
                                else -> "Slide across letters · tap a name"
                            },
                            color = TerminalGreen.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    } else suggestions.forEach { word ->
                        Box(
                            Modifier.height(44.dp)
                                .background(TerminalGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(1.dp, TerminalGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { enter(word) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(word, color = TerminalGreen, fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.semantics { contentDescription = "Insert $word" })
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("Terminal key grid")) {
                val keyHeight = if (layout.size in 1..4 && maxHeight != Dp.Infinity) {
                    ((maxHeight - rowGap * (layout.size - 1)) / layout.size).coerceIn(24.dp, 48.dp)
                } else 44.dp
                val swipeModifier = if (!letterPage || recognizer == null) Modifier else {
                    Modifier.pointerInput(layout, recognizer) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val grid = gridCoordinates
                            val bounds = if (grid == null || !grid.isAttached) emptyMap() else coordinates
                                .filterValues { it.isAttached }.mapValues { (_, key) ->
                                    val topLeft = grid.localPositionOf(key, Offset.Zero)
                                    Rect(topLeft, androidx.compose.ui.geometry.Size(key.size.width.toFloat(), key.size.height.toFloat()))
                                }
                            val centers = bounds.mapValues { (_, rect) -> SwipePoint(rect.center.x, rect.center.y) }
                            if (bounds.values.none { it.contains(down.position) }) return@awaitEachGesture
                            generation++
                            val request = generation
                            suggestions = emptyList()
                            matching = false
                            noMatch = false
                            val path = mutableListOf(down.position)
                            var dragging = false
                            var canceled = false
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    // Android cancellation may be delivered as an already
                                    // consumed synthetic release. Check before our consume.
                                    if (change?.isConsumed == true) canceled = true
                                    if (change == null || event.changes.count { it.pressed } > 1) canceled = true
                                    if (change != null) {
                                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                                        if (path.size < 1024) path += change.position else canceled = true
                                        if (dragging || canceled) {
                                            // Initial-pass ownership cancels child clicks/scrolling only
                                            // once this is a drag. A normal tap keeps key semantics.
                                            event.changes.forEach { it.consume() }
                                            trail = if (canceled) emptyList() else path.toList()
                                        }
                                    }
                                    val finished = change == null || !change.pressed
                                } while (!finished)
                            } finally {
                                // ACTION_CANCEL/layout disposal can abort the awaiting
                                // coroutine before an up event. Never leave its trail.
                                trail = emptyList()
                            }
                            if (dragging && !canceled) {
                                matching = true
                                val measured = path.map { SwipePoint(it.x, it.y) }
                                val matcher = recognizer
                                scope.launch {
                                    val found = withContext(Dispatchers.Default) { matcher?.suggest(measured, centers).orEmpty() }
                                    if (request == generation) {
                                        suggestions = found
                                        noMatch = found.isEmpty()
                                        matching = false
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { gridCoordinates = it }
                        .then(swipeModifier)
                        .drawWithContent {
                            drawContent()
                            trail.zipWithNext().forEach { (a, b) ->
                                drawLine(TerminalGreen.copy(alpha = 0.6f), a, b, strokeWidth = 3.dp.toPx())
                            }
                        }
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically),
                ) {
                    layout.forEachIndexed { rowIndex, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(rowRequesters[rowIndex]),
                        ) {
                            row.forEachIndexed { colIndex, key ->
                                val isSelected = isFocused && currentRow == rowIndex && currentColumn == colIndex
                                KeyboardKey(
                                    key = key,
                                    isSelected = isSelected,
                                    onClick = { enter(key) },
                                    keyHeight = keyHeight,
                                    modifier = Modifier.weight(1f).onGloballyPositioned {
                                        if (key.length == 1 && key[0] in 'A'..'Z') coordinates[key[0]] = it
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    key: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    keyHeight: Dp,
    modifier: Modifier = Modifier
) {
    val isBlank = key.isEmpty()
    
    Box(
        modifier = modifier
            .height(keyHeight)
            .background(
                color = if (isSelected) TerminalGreen else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) TerminalGreen else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(enabled = !isBlank, onClick = onClick)
            .semantics { if (key == "END") contentDescription = "End typing" },
        contentAlignment = Alignment.Center
    ) {
        if (!isBlank) {
            if (key.startsWith("TYPE:")) {
                val typeName = key.removePrefix("TYPE:")
                val type = try {
                    com.example.overdex.model.PokemonType.valueOf(typeName)
                } catch (e: Exception) {
                    null
                }
                
                type?.let {
                    PokemonTypeIcon(
                        type = it,
                        style = TypeIconStyle.OVERDEX,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (key == "END") {
                // The stepped letters echo the compact END key on the handheld games.
                Box(Modifier.size(25.dp, 22.dp)) {
                    "END".forEachIndexed { index, letter ->
                        Text(
                            text = letter.toString(),
                            color = if (isSelected) TerminalBlack else TerminalGreen,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.offset(x = (index * 8).dp, y = (index * 4).dp),
                        )
                    }
                }
            } else {
                val displayKey = when (key) {
                    "SPACE" -> "_"
                    "DELETE" -> "⌫"
                    else -> key
                }
                Text(
                    text = displayKey,
                    color = if (isSelected) TerminalBlack else TerminalGreen,
                    fontSize = if (displayKey.length > 1) 10.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Familiar QWERTY layout for direct touch and swipe input in the ODX-Fi LCD.
 * Instrument navigation can reach every letter and the ABC fallback.
 */
val QwertyLayout = listOf(
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("Z", "X", "C", "V", "B", "N", "M", "DELETE"),
    listOf("ABC", "SPACE", "#", "END")
)

/**
 * Alphabetical fallback with the same letters and input controls.
 */
val LettersLayout = listOf(
    listOf("A", "B", "C", "D", "E", "F", "G", "H"),
    listOf("I", "J", "K", "L", "M", "N", "O", "P"),
    listOf("Q", "R", "S", "T", "U", "V", "W", "X"),
    listOf("Y", "Z", "SPACE", "DELETE", "#", "QWERTY", "END")
)

/**
 * Balanced Token Layout for ODX-Fi LCD.
 * Numbers and all 18 type icons, plus explicit return choices for letter input.
 */
val TokenLayout = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8"),
    listOf("9", "0", "TYPE:NORMAL", "TYPE:FIRE", "TYPE:WATER", "TYPE:ELECTRIC", "TYPE:GRASS", "TYPE:ICE"),
    listOf("TYPE:FIGHTING", "TYPE:POISON", "TYPE:GROUND", "TYPE:FLYING", "TYPE:PSYCHIC", "TYPE:BUG", "TYPE:ROCK", "TYPE:GHOST"),
    listOf("TYPE:DRAGON", "TYPE:STEEL", "TYPE:FAIRY", "TYPE:DARK", "SPACE", "DELETE", "#", ",", "ABC", "QWERTY")
)

/**
 * Legacy layout reference (retained for compatibility if needed).
 */
val NumbersLayout = TokenLayout
val TestKeyboardLayout = LettersLayout
