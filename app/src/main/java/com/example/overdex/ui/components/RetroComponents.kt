package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DirectoryTree(
    visibleNodes: List<FlattenedNode>,
    selectedPath: String,
    onNodeSelected: (FlattenedNode) -> Unit,
    modifier: Modifier = Modifier
) {
    // "Ownership of Time" - sequential reveal count
    var revealCount by remember(visibleNodes.size) { mutableIntStateOf(0) }
    
    LaunchedEffect(visibleNodes.size) {
        for (i in 1..visibleNodes.size) {
            revealCount = i
            delay(30L.milliseconds) // Slightly faster for tree expansion
        }
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        visibleNodes.forEachIndexed { index, flattened ->
            if (index < revealCount) {
                val isSelected = selectedPath == flattened.path
                val label = when (val node = flattened.node) {
                    is DirectoryNode -> {
                        val prefix = if (flattened.isExpanded) "- " else "+ "
                        "$prefix${node.name}/"
                    }
                    is ActionNode -> node.name
                }

                val requester = remember { BringIntoViewRequester() }
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        requester.bringIntoView()
                    }
                }

                TerminalMenuOption(
                    label = label,
                    selected = isSelected,
                    modifier = Modifier
                        .padding(start = (flattened.depth * 16).dp)
                        .bringIntoViewRequester(requester),
                    onClick = { onNodeSelected(flattened) }
                )
            }
        }
    }
}

@Composable
fun TerminalScreen(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun TerminalPathIndicator(path: String) {
    Text(
        text = path,
        color = TerminalDimGreen,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun TerminalHeader(text: String, color: Color = TerminalPurple) {
    Text(
        text = "[ $text ]",
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * A standardized terminal section with header and vertical spacing.
 */
@Composable
fun ColumnScope.TerminalSection(
    title: String,
    headerColor: Color = TerminalPurple,
    spacing: Int = 24,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.height(spacing.dp))
    TerminalHeader(text = title, color = headerColor)
    content()
}

@Composable
fun TerminalMenuOption(
    label: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selected) "> $label" else "  $label",
                color = if (selected) TerminalGreen else TerminalDimGreen,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (status != null) {
            Text(
                text = "[ $status ]",
                color = if (selected) TerminalBlack else TerminalGreen,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun TerminalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TerminalGreen,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun TerminalButton(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (selected) TerminalGreen else TerminalGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Text(
            text = if (selected) "▶ $text" else "> $text",
            color = if (selected) TerminalBlack else TerminalGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HardwareNumericEntry(
    value: String,
    onValueChange: (String) -> Unit,
    maxDigits: Int = 4,
    isFocused: Boolean = true,
    focusedDigitIndex: Int = 0,
    onDigitFocusChange: ((Int) -> Unit)? = null
) {
    val paddedValue = value.padStart(maxDigits, '0')
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until maxDigits) {
            val digitSelected = isFocused && focusedDigitIndex == i
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(40.dp)
                    .background(
                        color = if (digitSelected) TerminalGreen else TerminalBlack,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (digitSelected) TerminalBlack else TerminalDimGreen,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 8.dp)
                    .then(if (onDigitFocusChange != null) Modifier.clickable { onDigitFocusChange(i) } else Modifier)
            ) {
                Text(
                    text = paddedValue[i].toString(),
                    color = if (digitSelected) TerminalBlack else TerminalGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AttributeToggle(
    label: String,
    value: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) TerminalGreen else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (value) "[ X ]" else "[   ]",
            color = if (selected) TerminalBlack else TerminalGreen,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label.uppercase(),
            color = if (selected) TerminalBlack else TerminalGreen,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
