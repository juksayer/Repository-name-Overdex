package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun ResearcherModeOverlay(
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack.copy(alpha = 0.95f),
        contentColor = TerminalGreen
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RESEARCHER MODE", fontWeight = FontWeight.Bold, color = TerminalPurple, fontSize = 20.sp)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TerminalGreen)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("ADVANCED SUBSYSTEMS", color = TerminalPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            PlaceholderSetting("CRT Scanline Intensity")
            PlaceholderSetting("CRT Curvature")
            PlaceholderSetting("Screen Bloom")
            PlaceholderSetting("Phosphor Color")
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("DEBUG TOOLS", color = TerminalPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            PlaceholderToggle("Debug Overlay")
            PlaceholderToggle("OCR Bounding Boxes")
            PlaceholderToggle("Experimental Features")
        }
    }
}

@Composable
private fun PlaceholderSetting(label: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontSize = 14.sp)
        Slider(
            value = 0.5f,
            onValueChange = {},
            enabled = true,
            colors = SliderDefaults.colors(
                thumbColor = TerminalGreen.copy(alpha = 0.5f),
                activeTrackColor = TerminalGreen.copy(alpha = 0.3f),
                inactiveTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
private fun PlaceholderToggle(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Switch(
            checked = false,
            onCheckedChange = {},
            enabled = true,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TerminalGreen.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray
            )
        )
    }
}
