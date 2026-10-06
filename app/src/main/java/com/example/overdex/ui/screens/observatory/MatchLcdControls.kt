package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalGreen

/** Stable renderer for the separate LCD surface; its content follows screen state. */
@Composable
internal fun PublishMatchLcd(
    publish: ((@Composable () -> Unit)?) -> Unit,
    content: @Composable () -> Unit
) {
    val latestContent = rememberUpdatedState(content)
    val latestPublish = rememberUpdatedState(publish)
    val renderer: @Composable () -> Unit = remember { { latestContent.value() } }
    SideEffect { publish(renderer) }
    DisposableEffect(Unit) { onDispose { latestPublish.value(null) } }
}

@Composable
internal fun MatchLcdColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
internal fun MatchLcdText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = TerminalGreen, fontFamily = FontFamily.Monospace,
        fontSize = 10.sp, lineHeight = 12.sp)
}

@Composable
internal fun MatchLcdButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, TerminalGreen.copy(alpha = if (selected) 1f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalGreen,
            containerColor = TerminalGreen.copy(alpha = if (selected) 0.15f else 0f))) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
    }
}
