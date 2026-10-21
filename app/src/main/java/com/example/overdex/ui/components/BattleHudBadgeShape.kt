package com.example.overdex.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.pow

/** GO badge material and the complementary lower-edge joint, in display pixels. */
internal object BattleHudBadgeStyle {
    val background = Color.White.copy(alpha = .80f)
    // Deliberately distinct from GO's teal badge text and white announcements.
    // Retain full move names; confirmation and danger also have non-color marks.
    val moveInk = Color(0xFF763CB6)
    // Approximately 17 capture pixels at 672/1080 scale in the retained GO badge.
    // Scale with badge width, not Android font or density settings.
    fun cornerPx(width: Float): Float = width * 27f / 415f
    fun centerDipPx(width: Float): Float = width * 1.5f / 415f
}

/** Avoid double-white compositing where GO's antialiased bottom edge meets us.
 * Only the first four display pixels of our upper contour soften; the body,
 * corner geometry and saved seam coordinate stay the same.
 */
internal fun Modifier.battleHudBadgeBackground(): Modifier = drawWithCache {
    val path = (BattleHudBadgeShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
    val step = size.width / 415f
    val layerColor = BattleHudBadgeStyle.background.copy(alpha =
        1f - (1f - BattleHudBadgeStyle.background.alpha).pow(1f / 4f))
    onDrawBehind {
        clipPath(path) {
            repeat(4) { index ->
                translate(top = step * (index + 1)) { drawPath(path, layerColor) }
            }
        }
    }
}

/** The top fills the space outside GO's rounded bottom corners; the bottom repeats them. */
internal object BattleHudBadgeShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = BattleHudBadgeStyle.cornerPx(size.width).coerceAtMost(size.height / 2f)
        val k = .55228475f
        val w = size.width
        val h = size.height
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f)
            cubicTo(0f, k * r, (1f - k) * r, r, r, r)
            // GO's lower edge sags very slightly at its center. A flat joint
            // doubled that white edge even when the saved corners lined up.
            quadraticTo(w / 2f, r + 2f * BattleHudBadgeStyle.centerDipPx(w), w - r, r)
            cubicTo(w - (1f - k) * r, r, w, k * r, w, 0f)
            lineTo(w, h - r)
            cubicTo(w, h - (1f - k) * r, w - (1f - k) * r, h, w - r, h)
            lineTo(r, h)
            cubicTo((1f - k) * r, h, 0f, h - (1f - k) * r, 0f, h - r)
            close()
        })
    }
}
