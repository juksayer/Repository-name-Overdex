package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun CaptureTemplateOverlay(
    template: CaptureTemplate,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        template.regions.forEach { region ->
            val left = region.x * width
            val top = region.y * height
            val rectWidth = region.width * width
            val rectHeight = region.height * height

            // Draw bounding box
            drawRect(
                color = TerminalGreen,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw name label
            val textLayoutResult = textMeasurer.measure(
                text = region.id,
                style = TextStyle(color = TerminalPurple, fontSize = 10.sp)
            )
            
            // Background for text
            drawRect(
                color = Color.Black.copy(alpha = 0.7f),
                topLeft = Offset(left, top - textLayoutResult.size.height),
                size = Size(textLayoutResult.size.width.toFloat(), textLayoutResult.size.height.toFloat())
            )
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(left, top - textLayoutResult.size.height)
            )
        }
    }
}
