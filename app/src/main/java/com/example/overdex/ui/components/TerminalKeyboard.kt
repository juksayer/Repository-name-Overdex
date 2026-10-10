package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen

/**
 * A foundational TerminalKeyboard component for Overdex.
 * Designed as a firmware module that receives navigation events from hardware controls.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TerminalKeyboard(
    layout: List<List<String>>,
    currentRow: Int,
    currentColumn: Int,
    modifier: Modifier = Modifier,
    onKeyActivated: ((String) -> Unit)? = null
) {
    val rowRequesters = remember(layout) { List(layout.size) { BringIntoViewRequester() } }
    LaunchedEffect(layout, currentRow) { rowRequesters.getOrNull(currentRow)?.bringIntoView() }
    BoxWithConstraints(modifier) {
        val rowGap = 6.dp
        val keyHeight = if (layout.size in 1..4 && maxHeight != Dp.Infinity) {
            ((maxHeight - rowGap * (layout.size - 1)) / layout.size).coerceIn(20.dp, 32.dp)
        } else 28.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically)
        ) {
            layout.forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(rowRequesters[rowIndex])
                ) {
                    row.forEachIndexed { colIndex, key ->
                        val isSelected = currentRow == rowIndex && currentColumn == colIndex

                        KeyboardKey(
                            key = key,
                            isSelected = isSelected,
                            onClick = { onKeyActivated?.invoke(key) },
                            keyHeight = keyHeight,
                            modifier = Modifier.weight(1f)
                        )
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
            .clickable(enabled = !isBlank, onClick = onClick),
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
            } else {
                val displayKey = when (key) {
                    "SPACE" -> "_"
                    "DELETE" -> "⌫"
                    else -> key
                }
                Text(
                    text = displayKey,
                    color = if (isSelected) TerminalBlack else TerminalGreen,
                    fontSize = if (displayKey.length > 1) 9.sp else 12.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Balanced Alphabet Layout for ODX-Fi LCD.
 * (7, 7, 7, 8) distribution with fixed bottom row.
 */
val LettersLayout = listOf(
    listOf("A", "B", "C", "D", "E", "F", "G", "H"),
    listOf("I", "J", "K", "L", "M", "N", "O", "P"),
    listOf("Q", "R", "S", "T", "U", "V", "W", "X"),
    listOf("Y", "Z", "SPACE", "DELETE", "#", ",")
)

/**
 * Balanced Token Layout for ODX-Fi LCD.
 * Standardized 8-column grid with Numbers and 18 Type Icons.
 */
val TokenLayout = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8"),
    listOf("9", "0", "TYPE:NORMAL", "TYPE:FIRE", "TYPE:WATER", "TYPE:ELECTRIC", "TYPE:GRASS", "TYPE:ICE"),
    listOf("TYPE:FIGHTING", "TYPE:POISON", "TYPE:GROUND", "TYPE:FLYING", "TYPE:PSYCHIC", "TYPE:BUG", "TYPE:ROCK", "TYPE:GHOST"),
    listOf("TYPE:DRAGON", "TYPE:STEEL", "TYPE:FAIRY", "TYPE:DARK", "SPACE", "DELETE", "#", "")
)

/**
 * Legacy layout reference (retained for compatibility if needed).
 */
val NumbersLayout = TokenLayout
val TestKeyboardLayout = LettersLayout
