package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.example.overdex.battle.observation.DroidballService
import kotlinx.coroutines.delay

/**
 * Temporary capture-debug HUD displaying observation diagnostics.
 */
@Composable
fun BattleOverlay() {
    val diagnostics by DroidballService.captureDiagnostics.collectAsState()

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            tick++
        }
    }

    val widthDp = with(LocalDensity.current) { 415.toDp() }
    val heightDp = with(LocalDensity.current) { 200.toDp() }

    // Pokémon GO badge inspired palette: pale translucent background, dark teal text
    val backgroundColor = Color(0xFFE0F2F1).copy(alpha = 0.88f)
    val borderColor = Color(0xFF004D40).copy(alpha = 0.4f)
    val textColor = Color(0xFF004D40)
    val accentColor = Color(0xFF00695C)
    val alertColor = Color(0xFFB71C1C)

    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 10.dp, bottomEnd = 10.dp)

    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .background(backgroundColor, shape)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val radius = 10.dp.toPx()
                val w = size.width
                val h = size.height

                // Draw border on bottom, left, and right, omitting the top edge (0,0 to w,0)
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, h - radius)
                    arcTo(
                        rect = Rect(0f, h - 2 * radius, 2 * radius, h),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = -90f,
                        forceMoveTo = false
                    )
                    lineTo(w - radius, h)
                    arcTo(
                        rect = Rect(w - 2 * radius, h - 2 * radius, w, h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = -90f,
                        forceMoveTo = false
                    )
                    lineTo(w, 0f)
                }
                drawPath(
                    path = path,
                    color = borderColor,
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            // Field 1: Capture state
            val stateText = "STATE: ${diagnostics.state}"
            Text(
                text = stateText,
                color = when (diagnostics.state) {
                    "OBSERVING" -> accentColor
                    "READY" -> Color(0xFFE65100)
                    "STOPPED" -> alertColor
                    else -> Color.DarkGray
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Field 2: Frame age (calculated with tick dependency and monotonic clock)
            val ageText = remember(tick, diagnostics.publicationNanoTime, diagnostics.state) {
                val pubTime = diagnostics.publicationNanoTime
                if (pubTime == null) {
                    "AGE: Not observed"
                } else {
                    val currentNano = System.nanoTime()
                    val rawDiff = (currentNano - pubTime) / 1_000_000
                    val diffMs = if (rawDiff < 0) 0L else rawDiff
                    if (diagnostics.state == "STOPPED") {
                        "AGE: ${diffMs}ms (STALE)"
                    } else {
                        "AGE: ${diffMs}ms"
                    }
                }
            }
            Text(
                text = ageText,
                color = if (diagnostics.state == "STOPPED") alertColor else textColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // Field 3: Frame dimensions
            val dimText = remember(diagnostics.width, diagnostics.height, diagnostics.state) {
                if (diagnostics.width != null && diagnostics.height != null) {
                    if (diagnostics.state == "STOPPED") {
                        "DIM: ${diagnostics.width}x${diagnostics.height} (STALE)"
                    } else {
                        "DIM: ${diagnostics.width}x${diagnostics.height}"
                    }
                } else {
                    "DIM: Not observed"
                }
            }
            Text(
                text = dimText,
                color = textColor.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
