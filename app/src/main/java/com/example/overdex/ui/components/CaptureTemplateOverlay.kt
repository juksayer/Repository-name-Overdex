package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.CaptureRegion
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlin.math.sqrt

private sealed class DragMode {
    object None : DragMode()
    object Move : DragMode()
    data class Resize(val handle: Handle) : DragMode()
}

private enum class Handle {
    TopLeft, TopRight, BottomLeft, BottomRight
}

private fun isNear(offset: Offset, x: Float, y: Float, threshold: Float): Boolean {
    val dx = offset.x - x
    val dy = offset.y - y
    return sqrt(dx * dx + dy * dy) < threshold
}

@Composable
fun CaptureTemplateOverlay(
    template: CaptureTemplate,
    isVisible: Boolean,
    imageSize: Size?,
    selectedRegionId: String? = null,
    onRegionSelect: (String?) -> Unit = {},
    onRegionUpdate: (CaptureRegion) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isVisible || imageSize == null || imageSize.width == 0f || imageSize.height == 0f) return

    val textMeasurer = rememberTextMeasurer()
    
    var dragMode by remember { mutableStateOf<DragMode>(DragMode.None) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(template, imageSize) {
                detectTapGestures { offset ->
                    val containerSize = size
                    val scale = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
                    val displayWidth = imageSize.width * scale
                    val displayHeight = imageSize.height * scale
                    val leftOffset = (containerSize.width - displayWidth) / 2
                    val topOffset = (containerSize.height - displayHeight) / 2

                    var found: String? = null
                    template.regions.forEach { region ->
                        val left = leftOffset + (region.x * displayWidth)
                        val top = topOffset + (region.y * displayHeight)
                        val right = left + (region.width * displayWidth)
                        val bottom = top + (region.height * displayHeight)

                        if (offset.x in left..right && offset.y in top..bottom) {
                            found = region.id
                        }
                    }
                    onRegionSelect(found)
                }
            }
            .pointerInput(template, imageSize, selectedRegionId) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val containerSize = size
                        val scale = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
                        val displayWidth = imageSize.width * scale
                        val displayHeight = imageSize.height * scale
                        val leftOffset = (containerSize.width - displayWidth) / 2
                        val topOffset = (containerSize.height - displayHeight) / 2

                        val selectedRegion = template.regions.find { it.id == selectedRegionId }
                        if (selectedRegion != null) {
                            val left = leftOffset + (selectedRegion.x * displayWidth)
                            val top = topOffset + (selectedRegion.y * displayHeight)
                            val right = left + (selectedRegion.width * displayWidth)
                            val bottom = top + (selectedRegion.height * displayHeight)
                            
                            val handleSize = 32.dp.toPx() // Larger hit target for handles
                            
                            dragMode = when {
                                isNear(offset, left, top, handleSize) -> DragMode.Resize(Handle.TopLeft)
                                isNear(offset, right, top, handleSize) -> DragMode.Resize(Handle.TopRight)
                                isNear(offset, left, bottom, handleSize) -> DragMode.Resize(Handle.BottomLeft)
                                isNear(offset, right, bottom, handleSize) -> DragMode.Resize(Handle.BottomRight)
                                offset.x in left..right && offset.y in top..bottom -> DragMode.Move
                                else -> DragMode.None
                            }
                        }
                    },
                    onDrag = { _, dragAmount ->
                        val selectedRegion = template.regions.find { it.id == selectedRegionId } ?: return@detectDragGestures
                        val containerSize = size
                        val scale = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
                        val displayWidth = imageSize.width * scale
                        val displayHeight = imageSize.height * scale
                        
                        val dx = dragAmount.x / displayWidth
                        val dy = dragAmount.y / displayHeight
                        
                        val updated = when (val mode = dragMode) {
                            DragMode.Move -> {
                                selectedRegion.copy(
                                    x = (selectedRegion.x + dx).coerceIn(0f, 1f - selectedRegion.width),
                                    y = (selectedRegion.y + dy).coerceIn(0f, 1f - selectedRegion.height)
                                )
                            }
                            is DragMode.Resize -> {
                                when (mode.handle) {
                                    Handle.TopLeft -> selectedRegion.copy(
                                        x = (selectedRegion.x + dx).coerceIn(0f, selectedRegion.x + selectedRegion.width - 0.01f),
                                        y = (selectedRegion.y + dy).coerceIn(0f, selectedRegion.y + selectedRegion.height - 0.01f),
                                        width = (selectedRegion.width - dx).coerceIn(0.01f, selectedRegion.x + selectedRegion.width),
                                        height = (selectedRegion.height - dy).coerceIn(0.01f, selectedRegion.y + selectedRegion.height)
                                    )
                                    Handle.TopRight -> selectedRegion.copy(
                                        y = (selectedRegion.y + dy).coerceIn(0f, selectedRegion.y + selectedRegion.height - 0.01f),
                                        width = (selectedRegion.width + dx).coerceIn(0.01f, 1f - selectedRegion.x),
                                        height = (selectedRegion.height - dy).coerceIn(0.01f, selectedRegion.y + selectedRegion.height)
                                    )
                                    Handle.BottomLeft -> selectedRegion.copy(
                                        x = (selectedRegion.x + dx).coerceIn(0f, selectedRegion.x + selectedRegion.width - 0.01f),
                                        width = (selectedRegion.width - dx).coerceIn(0.01f, selectedRegion.x + selectedRegion.width),
                                        height = (selectedRegion.height + dy).coerceIn(0.01f, 1f - selectedRegion.y)
                                    )
                                    Handle.BottomRight -> selectedRegion.copy(
                                        width = (selectedRegion.width + dx).coerceIn(0.01f, 1f - selectedRegion.x),
                                        height = (selectedRegion.height + dy).coerceIn(0.01f, 1f - selectedRegion.y)
                                    )
                                }
                            }
                            DragMode.None -> selectedRegion
                        }
                        
                        if (updated != selectedRegion) {
                            onRegionUpdate(updated)
                        }
                    },
                    onDragEnd = { dragMode = DragMode.None }
                )
            }
    ) {
        val containerSize = size
        val scale = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
        val displayWidth = imageSize.width * scale
        val displayHeight = imageSize.height * scale
        val leftOffset = (containerSize.width - displayWidth) / 2
        val topOffset = (containerSize.height - displayHeight) / 2

        template.regions.forEach { region ->
            val isSelected = region.id == selectedRegionId
            val left = leftOffset + (region.x * displayWidth)
            val top = topOffset + (region.y * displayHeight)
            val rectWidth = region.width * displayWidth
            val rectHeight = region.height * displayHeight

            drawRect(
                color = if (isSelected) TerminalPurple else TerminalGreen,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.dp.toPx())
            )

            if (isSelected) {
                val handleRadius = 6.dp.toPx()
                drawCircle(color = TerminalPurple, radius = handleRadius, center = Offset(left, top))
                drawCircle(color = TerminalPurple, radius = handleRadius, center = Offset(left + rectWidth, top))
                drawCircle(color = TerminalPurple, radius = handleRadius, center = Offset(left, top + rectHeight))
                drawCircle(color = TerminalPurple, radius = handleRadius, center = Offset(left + rectWidth, top + rectHeight))
            }

            val textLayoutResult = textMeasurer.measure(
                text = region.id,
                style = TextStyle(color = if (isSelected) TerminalPurple else TerminalGreen, fontSize = 10.sp)
            )
            
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
