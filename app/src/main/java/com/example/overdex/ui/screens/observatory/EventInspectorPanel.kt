package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.observatory.RecordedEvent
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple
import java.util.*

@Composable
fun EventInspectorPanel(
    event: RecordedEvent?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TerminalHeader(text = "EVENT INSPECTOR")
        
        if (event == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                TerminalText(text = "[ SELECT EVENT TO INSPECT ]", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TerminalText(text = "SEQUENCE: #${String.format(Locale.ROOT, "%06d", event.sequenceNumber)}", color = TerminalPurple)
                    TerminalText(text = "+${event.relativeTimestamp}ms", color = TerminalDimGreen)
                }
                
                TerminalText(text = "SOURCE: ${event.sourceType.name}")
                TerminalText(text = "PAYLOAD: ${event.payload::class.java.simpleName}")
                
                Spacer(modifier = Modifier.height(16.dp))
                TerminalText(text = "RAW DATA:", color = TerminalPurple, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                
                // For now, we'll just use toString() to display the payload
                // Future improvement: use the serializer to show formatted JSON
                TerminalText(
                    text = event.payload.toString(),
                    color = TerminalDimGreen,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
