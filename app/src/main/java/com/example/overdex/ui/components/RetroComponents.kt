package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

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
    status: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ">",
                color = TerminalDimGreen,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = label,
                color = TerminalGreen,
                fontSize = 16.sp
            )
        }
        if (status != null) {
            Text(
                text = "[ $status ]",
                color = TerminalGreen,
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
    fontSize: TextUnit = 14.sp
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        modifier = modifier
    )
}

@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Text(
            text = "> $text",
            color = TerminalGreen,
            fontSize = 16.sp
        )
    }
}
