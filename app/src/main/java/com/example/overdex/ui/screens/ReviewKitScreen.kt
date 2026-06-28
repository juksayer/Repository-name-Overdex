package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen

@Composable
fun ReviewKitScreen(
    onBack: () -> Unit
) {
    TerminalScreen {
        TerminalHeader("review.kit")
        TerminalText(text = "build active team (max 3)", color = TerminalDimGreen)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalText(text = "1. venusaur [grass/poison]")
        TerminalText(text = "2. charizard [fire/flying]")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalText(text = "> add:")
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TerminalButton(text = "run full analysis", onClick = {})
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(text = "save team to log", onClick = {})
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(text = "clear team", onClick = {})
        
        Spacer(modifier = Modifier.weight(1f))
        
        TerminalButton(text = "exit kit", onClick = onBack)
    }
}
