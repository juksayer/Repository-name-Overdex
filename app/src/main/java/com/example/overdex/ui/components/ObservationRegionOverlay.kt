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
import com.example.overdex.model.ObservationRegions
import com.example.overdex.ui.theme.TerminalPurple

/**
 * Developer flag to enable/disable the Observation Region Overlay.
 */
const val SHOW_OBSERVATION_REGIONS = false

/**
 * A developer-only diagnostic overlay that visualizes every defined Observation Region.
 *
 * @param imageSize The intrinsic size of the source image being observed.
 */
@Composable
fun ObservationRegionOverlay(
    imageSize: Size?,
    modifier: Modifier = Modifier
) {
    if (!SHOW_OBSERVATION_REGIONS || imageSize == null || imageSize.width == 0f || imageSize.height == 0f) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val containerSize = size
        val scale = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
        val displayWidth = imageSize.width * scale
        val displayHeight = imageSize.height * scale
        val leftOffset = (containerSize.width - displayWidth) / 2
        val topOffset = (containerSize.height - displayHeight) / 2

        ObservationRegions.all.forEach { region ->
            val left = leftOffset + (region.x * displayWidth)
            val top = topOffset + (region.y * displayHeight)
            val rectWidth = region.width * displayWidth
            val rectHeight = region.height * displayHeight

            // Draw Region Rectangle
            drawRect(
                color = TerminalPurple,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 1.dp.toPx())
            )

            // Draw Region Name
            val textLayoutResult = textMeasurer.measure(
                text = region.name,
                style = TextStyle(
                    color = TerminalPurple,
                    fontSize = 8.sp,
                    background = Color.Black.copy(alpha = 0.5f)
                )
            )

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(left, top - textLayoutResult.size.height)
            )
        }
    }
}
