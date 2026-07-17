package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun ObservationWorkspaceViewer(
    recognitionResults: Map<String, List<RecognitionResult<*>>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        TerminalHeader(text = "observation session workspace", color = TerminalPurple)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            recognitionResults.forEach { (regionId, results) ->
                item {
                    TerminalText(text = regionId.uppercase(), color = TerminalPurple, fontSize = 10.sp)
                }
                items(results) { result ->
                    WorkspaceResultRow(result)
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceResultRow(result: RecognitionResult<*>) {
    Column(modifier = Modifier.padding(start = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TerminalText(text = result.recognizer, color = TerminalDimGreen, fontSize = 9.sp)
            TerminalText(text = "CONF: ${(result.confidence * 100).toInt()}%", color = TerminalDimGreen, fontSize = 9.sp)
        }
        TerminalText(
            text = result.value?.toString() ?: "NULL",
            color = if (result.value != null) Color.White else Color.Gray,
            fontSize = 12.sp
        )
    }
}
