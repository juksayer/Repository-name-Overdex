package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.overdex.presentation.DroidballPose
import kotlin.math.PI
import kotlin.math.sin

const val DROIDBALL_BODY_DIAMETER_FRACTION = .72f

/**
 * The shell and floating window share this single body renderer. The ball is a vector,
 * including its lighting; there are no bitmap copies or separate halves to strand.
 * The drawing may travel outside its modifier during a journey: owners must not clip it.
 */
@Composable
fun DroidballBody(
    modifier: Modifier = Modifier,
    pose: DroidballPose = DroidballPose(),
    contentDescription: String? = "Droidball",
) {
    val semantics = if (contentDescription == null) modifier else modifier.semantics {
        this.contentDescription = contentDescription
    }
    Canvas(semantics) {
        val unit = size.minDimension
        if (unit <= 0f) return@Canvas
        val x = (size.width - unit) / 2f + pose.xBodies * unit * DROIDBALL_BODY_DIAMETER_FRACTION
        val y = (size.height - unit) / 2f
        withTransform({
            translate(x, y)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            // A ground shadow does not rotate with the ball.
            val shadow = pose.shadow.coerceIn(0f, 1f)
            if (shadow > 0f) {
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = .34f * shadow), Color.Transparent),
                        center = Offset(.5f, .94f), radius = .31f,
                    ),
                    topLeft = Offset(.20f, .89f), size = Size(.60f, .085f),
                )
            }
            withTransform({
                translate(0f, pose.press.coerceIn(0f, 1f) * .045f)
                val pressedScale = 1f - pose.press.coerceIn(0f, 1f) * .07f
                scale(pressedScale, pressedScale, pivot = Offset(.5f, .59f))
            }) {
                rotate(pose.rollDegrees, pivot = Offset(.5f, .59f)) {
                    drawDroidballBody(pose)
                }
            }
            if (pose.scan > 0f) drawDroidballScan(pose.scan)
        }
    }
}

private fun DrawScope.drawDroidballBody(pose: DroidballPose) {
    val detached = pose.detached.coerceIn(0f, 1f)
    val focus = pose.focus.coerceIn(0f, 1f)
    val sadness = pose.sadness.coerceIn(0f, 1f)
    val ink = Color(0xFF151C20)
    val red = Color(0xFFE3322A)
    val lower = lerp(Color(0xFF3D8735), Color(0xFFF0F1EB), detached)
    val center = Offset(.5f, .59f)
    val shell = Rect(.14f, .23f, .86f, .95f)

    // Rounded antennae remain part of the same rotating body.
    listOf(Offset(.24f, .08f) to Offset(.33f, .275f),
        Offset(.76f, .08f) to Offset(.67f, .275f)).forEach { (a, b) ->
        drawLine(ink, a, b, strokeWidth = .041f, cap = StrokeCap.Round)
        drawLine(Color(0xFFF55B44), a + Offset(-.004f, 0f), b + Offset(-.004f, 0f),
            strokeWidth = .019f, cap = StrokeCap.Round)
    }
    drawCircle(ink, .373f, center)
    val top = Path().apply {
        moveTo(.14f, .59f)
        arcTo(shell, 180f, 180f, false)
        lineTo(.86f, .59f)
        close()
    }
    val bottom = Path().apply {
        moveTo(.14f, .59f)
        lineTo(.86f, .59f)
        arcTo(shell, 0f, 180f, false)
        close()
    }
    drawPath(top, Brush.radialGradient(
        listOf(Color(0xFFFF6B4E), red, Color(0xFF961F24)),
        center = Offset(.34f, .31f), radius = .66f,
    ))
    drawPath(bottom, Brush.radialGradient(
        listOf(lerp(lower, Color.White, .17f), lower, lerp(lower, Color(0xFF536069), .50f)),
        center = Offset(.35f, .61f), radius = .54f,
    ))
    // A narrow shell highlight and a subdued lower rim suggest solid, machined material.
    drawArc(Color.White.copy(alpha = .38f), 198f, 82f, false,
        topLeft = Offset(.164f, .254f), size = Size(.672f, .672f), style = Stroke(.014f, cap = StrokeCap.Round))
    drawArc(Color(0xFF283137).copy(alpha = .30f), 15f, 145f, false,
        topLeft = Offset(.164f, .254f), size = Size(.672f, .672f), style = Stroke(.012f, cap = StrokeCap.Round))
    drawLine(ink, Offset(.14f, .59f), Offset(.86f, .59f), strokeWidth = .049f)
    if (detached > 0f) {
        // Three small etches, rather than a noisy texture that disappears at icon size.
        for (i in 0..2) {
            val x = .62f + i * .035f
            drawLine(Color(0xFF5C6870).copy(alpha = .26f * detached),
                Offset(x, .77f), Offset(x - .020f, .805f), strokeWidth = .008f,
                cap = StrokeCap.Round)
        }
    }

    val blink = pose.blink.coerceIn(0f, 1f)
    val eyeHeight = (.046f + .013f * focus) * (1f - blink)
    val eyeWidth = .037f + .009f * focus
    val gaze = sin(pose.scan * PI * 2.0).toFloat() * .013f
    for (left in listOf(true, false)) {
        val ex = (if (left) .375f else .625f) + gaze
        val ey = .465f + sadness * .012f
        if (eyeHeight > .004f) {
            drawOval(ink, Offset(ex - eyeWidth, ey - eyeHeight), Size(eyeWidth * 2, eyeHeight * 2))
            drawCircle(Color.White.copy(alpha = .65f * detached * (1f - blink)), .009f,
                Offset(ex - .010f, ey - .017f))
        } else {
            drawLine(ink, Offset(ex - eyeWidth, ey), Offset(ex + eyeWidth, ey + .002f),
                strokeWidth = .013f, cap = StrokeCap.Round)
        }
        // Brows are independent of the eyelids: open, wide eyes can still look intent.
        val outerX = ex + (if (left) -.052f else .052f)
        val innerX = ex + (if (left) .044f else -.044f)
        val browAlpha = maxOf(focus, sadness).coerceIn(0f, 1f)
        if (browAlpha > 0f) {
            drawLine(ink.copy(alpha = browAlpha),
                Offset(outerX, .397f + .025f * sadness),
                Offset(innerX, .397f + .032f * focus - .012f * sadness),
                strokeWidth = .021f, cap = StrokeCap.Round)
        }
    }
    // One equatorial button, attached to the lower jaw throughout the journey.
    drawCircle(ink, .105f, Offset(.5f, .615f))
    drawCircle(Brush.radialGradient(
        listOf(Color.White, lerp(Color(0xFFD6DDDC), lower, .12f), Color(0xFF8E9AA0)),
        center = Offset(.478f, .589f), radius = .12f,
    ), .069f, Offset(.5f, .615f))
    drawArc(Color.White.copy(alpha = .65f), 202f, 82f, false,
        topLeft = Offset(.442f, .557f), size = Size(.116f, .116f), style = Stroke(.007f, cap = StrokeCap.Round))
}

private fun DrawScope.drawDroidballScan(strength: Float) {
    val alpha = strength.coerceIn(0f, 1f)
    val y = .35f + .40f * strength
    val beam = Path().apply {
        moveTo(.76f, .49f)
        lineTo(1.28f, y - .12f)
        lineTo(1.28f, y + .12f)
        close()
    }
    drawPath(beam, Brush.horizontalGradient(
        listOf(Color(0xFF6EF8DE).copy(alpha = .24f * alpha), Color.Transparent),
        startX = .76f, endX = 1.28f,
    ))
    drawLine(Color(0xFFB6FFF0).copy(alpha = .5f * alpha), Offset(.79f, .49f), Offset(1.19f, y), .008f)
}
