package com.example.overdex.ui.ODXFi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AndroidPokeballLogo(
    modifier: Modifier = Modifier,
    isInteractive: Boolean = false
) {
    var trigger by remember { mutableIntStateOf(0) }
    val rotation = remember { Animatable(0f) }
    val wakeIntensity = remember { Animatable(0f) }
    val eyesAlpha = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            // Phase 1: Wake (Brighten and thicken)
            launch {
                wakeIntensity.animateTo(1f, tween(400))
            }

            // Phase 2: Acknowledge (Inertial rotation ~15 degrees)
            rotation.animateTo(
                targetValue = 15f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                )
            )

            // Phase 3: Blink (Mechanical acknowledgements)
            delay(100)
            eyesAlpha.snapTo(0f)
            delay(60)
            eyesAlpha.snapTo(1f)
            delay(100)
            eyesAlpha.snapTo(0f)
            delay(60)
            eyesAlpha.snapTo(1f)

            delay(500)

            // Phase 4: Rest
            launch {
                wakeIntensity.animateTo(0f, tween(600))
            }
            rotation.animateTo(0f, tween(600))
        }
    }

    Canvas(
        modifier = modifier
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { trigger++ })
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        val w = size.width
        val h = size.height

        // Android Head Shape
        val path = Path().apply {
            addArc(
                oval = Rect(0f, 0f, w, h),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f
            )
        }

        val animatedRed = androidx.compose.ui.graphics.lerp(
            Color.Red,
            Color(0xFFFF4444),
            wakeIntensity.value
        )

        drawContext.canvas.save()

        drawPath(path, color = Color.White)

        // Top half Red
        drawPath(path, color = animatedRed)

        // Middle black line
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, h / 2 - 2.dp.toPx()),
            size = size.copy(height = 4.dp.toPx())
        )

        // Center circle
        drawCircle(
            color = Color.Black,
            radius = 10.dp.toPx(),
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = center
        )

        // Eyes
        drawCircle(
            color = Color.White.copy(alpha = eyesAlpha.value),
            radius = 4.dp.toPx(),
            center = Offset(w * 0.3f, h * 0.3f)
        )
        drawCircle(
            color = Color.White.copy(alpha = eyesAlpha.value),
            radius = 4.dp.toPx(),
            center = Offset(w * 0.7f, h * 0.3f)
        )

        drawContext.canvas.restore()

        // Border
        val strokeWidth =
            androidx.compose.ui.unit.lerp(2.dp, 3.dp, wakeIntensity.value).toPx()

        drawPath(
            path,
            color = Color.Black,
            style = Stroke(width = strokeWidth)
        )

        // Antennas
        drawLine(
            color = animatedRed,
            start = Offset(w * 0.3f, h * 0.1f),
            end = Offset(w * 0.2f, -h * 0.1f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = animatedRed,
            start = Offset(w * 0.7f, h * 0.1f),
            end = Offset(w * 0.8f, -h * 0.1f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}