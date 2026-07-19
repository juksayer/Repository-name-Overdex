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
import com.example.overdex.battle.debug.observatory.*
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
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
                TerminalText(text = "PAYLOAD: ${event.payload::class.java.simpleName.removeSuffix("Payload")}")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Polymorphic Payload Rendering
                PayloadDetailRenderer(event.payload)

                Spacer(modifier = Modifier.height(24.dp))
                TerminalText(text = "RAW DATA (toString):", color = TerminalPurple.copy(alpha = 0.5f), fontSize = 10.sp)
                TerminalText(
                    text = event.payload.toString(),
                    color = TerminalDimGreen,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PayloadDetailRenderer(payload: RecordedPayload) {
    when (payload) {
        is AccessibilityPayload -> AccessibilityPayloadDetails(payload)
        is VisionCapturePayload -> VisionCaptureDetails(payload)
        is AnchorDetectedPayload -> AnchorDetectedDetails(payload)
        is RecognitionAttemptPayload -> RecognitionAttemptDetails(payload)
        is DecisionEvaluatedPayload -> DecisionEvaluatedDetails(payload)
        is IntegrityCheckedPayload -> IntegrityCheckedDetails(payload)
        is ProgressUpdatedPayload -> ProgressUpdatedDetails(payload)
        is SystemEventPayload -> SystemEventDetails(payload)
    }
}

@Composable
private fun DecisionEvaluatedDetails(payload: DecisionEvaluatedPayload) {
    TerminalText(text = "STAGE: ${payload.observationStage}", color = TerminalPurple)
    TerminalText(text = "FIELD: ${payload.field}", color = TerminalGreen)
    
    Spacer(modifier = Modifier.height(8.dp))
    TerminalText(text = "WINNING VALUE:", fontSize = 10.sp, color = TerminalDimGreen)
    TerminalText(text = "${payload.winningValue}", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    TerminalText(text = "CONFIDENCE: ${String.format(Locale.ROOT, "%.2f", payload.winningConfidence)}", color = TerminalGreen)

    Spacer(modifier = Modifier.height(16.dp))
    TerminalText(text = "COMPETITORS:", fontSize = 10.sp, color = TerminalPurple)
    payload.competitors.forEach { competitor ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            TerminalText(
                text = if (competitor.value == payload.winningValue) "▶ " else "  ",
                color = TerminalPurple,
                fontSize = 10.sp
            )
            Column {
                TerminalText(text = "${competitor.recognizer}: ${competitor.value}", fontSize = 11.sp)
                TerminalText(text = "CONF: ${String.format(Locale.ROOT, "%.2f", competitor.confidence)}", fontSize = 9.sp, color = TerminalDimGreen)
            }
        }
    }
}

@Composable
private fun IntegrityCheckedDetails(payload: IntegrityCheckedPayload) {
    TerminalText(text = "STATUS: ${payload.status}", color = if (payload.status == "COMPLETE") TerminalGreen else Color.Yellow, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    
    if (payload.resolvedFields.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        TerminalText(text = "RESOLVED:", fontSize = 10.sp, color = TerminalGreen)
        TerminalText(text = payload.resolvedFields.joinToString(", "), fontSize = 11.sp)
    }
    
    if (payload.missingFields.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        TerminalText(text = "MISSING:", fontSize = 10.sp, color = Color.Gray)
        TerminalText(text = payload.missingFields.joinToString(", "), fontSize = 11.sp)
    }
    
    if (payload.conflictingFields.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        TerminalText(text = "CONFLICTS:", fontSize = 10.sp, color = Color.Red)
        TerminalText(text = payload.conflictingFields.joinToString(", "), fontSize = 11.sp, color = Color.Red)
    }
}

@Composable
private fun ProgressUpdatedDetails(payload: ProgressUpdatedPayload) {
    TerminalText(text = "STAGE: ${payload.observationStage}", color = TerminalPurple)
    TerminalText(text = "PROGRESS: ${(payload.percentComplete * 100).toInt()}%", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    
    // Simple ASCII progress bar
    val dots = 20
    val filled = (payload.percentComplete * dots).toInt()
    TerminalText(
        text = "[" + "=".repeat(filled) + ">" + ".".repeat((dots - filled).coerceAtLeast(0)) + "]",
        color = TerminalGreen,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
    
    if (payload.isComplete) {
        TerminalText(text = "OBJECTIVE MET", color = TerminalGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
private fun AccessibilityPayloadDetails(payload: AccessibilityPayload) {
    TerminalText(text = "EVENT: ${payload.eventType}", color = TerminalGreen)
    TerminalText(text = "PACKAGE: ${payload.packageName}", color = TerminalDimGreen, fontSize = 12.sp)
    if (payload.text.isNotEmpty()) {
        TerminalText(text = "TEXT: ${payload.text.joinToString(", ")}")
    }
    payload.contentDescription?.let { TerminalText(text = "DESC: $it") }
}

@Composable
private fun VisionCaptureDetails(payload: VisionCapturePayload) {
    TerminalText(text = "STAGE: ${payload.observationStage}", color = TerminalPurple)
    TerminalText(text = "REGION: ${payload.regionId}", color = TerminalGreen)
    TerminalText(text = "RESOLUTION: ${payload.width}x${payload.height}", color = TerminalDimGreen, fontSize = 12.sp)
}

@Composable
private fun AnchorDetectedDetails(payload: AnchorDetectedPayload) {
    TerminalText(text = "STAGE: ${payload.observationStage}", color = TerminalPurple)
    TerminalText(text = "ANCHOR: ${payload.anchorType}", color = TerminalGreen)
    TerminalText(text = "CONFIDENCE: ${String.format(Locale.ROOT, "%.2f", payload.confidence)}", color = if (payload.confidence > 0.8) TerminalGreen else Color.Yellow)
    TerminalText(text = "BOUNDS: L:${payload.bounds.left} T:${payload.bounds.top} R:${payload.bounds.right} B:${payload.bounds.bottom}", color = TerminalDimGreen, fontSize = 10.sp)
}

@Composable
private fun RecognitionAttemptDetails(payload: RecognitionAttemptPayload) {
    TerminalText(text = "STAGE: ${payload.observationStage}", color = TerminalPurple)
    TerminalText(text = "REGION: ${payload.regionId}", color = TerminalGreen)
    TerminalText(text = "RECOGNIZER: ${payload.recognizerName}")
    
    val statusColor = if (payload.success) TerminalGreen else Color.Red
    TerminalText(text = "STATUS: ${if (payload.success) "SUCCESS" else "FAILED"}", color = statusColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    
    if (payload.success) {
        TerminalText(text = "VALUE: ${payload.resultValue}", color = Color.White)
        TerminalText(text = "CONFIDENCE: ${String.format(Locale.ROOT, "%.2f", payload.confidence)}", color = statusColor)
    }
}

@Composable
private fun SystemEventDetails(payload: SystemEventPayload) {
    TerminalText(text = "EVENT: ${payload.eventName}", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    payload.details?.let { TerminalText(text = "DETAILS: $it", color = TerminalDimGreen) }
}
