package com.example.overdex.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen

/**
 * A foundational TerminalKeyboard component for Overdex.
 * Designed as a firmware module that receives navigation events from hardware controls.
 */
@Composable
fun TerminalKeyboard(
    layout: List<List<String>>,
    currentRow: Int,
    currentColumn: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        layout.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEachIndexed { colIndex, key ->
                    val isSelected = currentRow == rowIndex && currentColumn == colIndex
                    
                    KeyboardKey(
                        key = key,
                        isSelected = isSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    key: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val isBlank = key.isBlank()
    
    Box(
        modifier = modifier
            .height(36.dp)
            .background(
                color = if (isSelected) TerminalGreen else if (isBlank) Color.Transparent else TerminalGreen.copy(alpha = 0.05f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) TerminalGreen else if (isBlank) Color.Transparent else TerminalDimGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!isBlank) {
            Text(
                text = key,
                color = if (isSelected) TerminalBlack else TerminalGreen,
                fontSize = if (key.length > 1) 10.sp else 14.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Full Dirty 1.0 Alpha-Numeric Layout
 */
val TestKeyboardLayout = listOf(
    listOf("A", "B", "C", "D", "E", "F", "G", "H"),
    listOf("I", "J", "K", "L", "M", "N", "O", "P"),
    listOf("Q", "R", "S", "T", "U", "V", "W", "X"),
    listOf("Y", "Z", "0", "1", "2", "3", "4", "5"),
    listOf("6", "7", "8", "9", "-", "'", "⌫"),
    listOf("SPACE", "CLEAR", "DONE")
)
