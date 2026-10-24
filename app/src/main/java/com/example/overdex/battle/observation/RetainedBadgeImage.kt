package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.BattleHudBadgeShape
import com.example.overdex.ui.components.BattleHudBadgeStyle

/** Presentation copy only. Keep the native badge in the HUD's curved opening,
 * with transparent pixels wherever our own HUD body occupies the source.
 * Sharing the rendered contour prevents a rectangular cut through the balls.
 */
internal fun retainedBadgeImage(source: Bitmap, badge: AnchorRegion, hud: AnchorRegion?): Bitmap? {
    val bounds = BattleCropResolver.resolveRect(badge, source.width, source.height) ?: return null
    val image = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    val radius = BattleHudBadgeStyle.cornerPx(bounds.width().toFloat())
    val badgeShape = Path().apply {
        addRoundRect(RectF(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            radius, radius, Path.Direction.CW)
    }
    canvas.clipPath(badgeShape)
    if (hud != null && !SpeciesCaptureVisibility.canRead(badge, hud)) {
        val outline = BattleHudBadgeShape.createOutline(
            Size(hud.width * source.width, hud.height * source.height), LayoutDirection.Ltr, Density(1f)
        ) as Outline.Generic
        val occupied = Path(outline.path.asAndroidPath()).apply {
            offset(hud.x * source.width - bounds.left, hud.y * source.height - bounds.top)
        }
        // Display-to-capture rounding and filtered edges can spill HUD pixels
        // just outside its mathematical contour. Exclude one source pixel too.
        val guarded = Path()
        Paint().apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 2f }
            .getFillPath(occupied, guarded)
        canvas.clipOutPath(guarded)
    }
    canvas.drawBitmap(source, -bounds.left.toFloat(), -bounds.top.toFloat(), Paint())
    return image
}
