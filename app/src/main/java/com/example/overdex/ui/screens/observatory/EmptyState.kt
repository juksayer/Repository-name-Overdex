package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun EmptyRecordingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TerminalText(
            text = "NO OBSERVATION RECORDING",
            color = TerminalPurple,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        TerminalText(
            text = "Deploy Droidball and begin an Observation Session to capture signals.",
            color = TerminalDimGreen,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
