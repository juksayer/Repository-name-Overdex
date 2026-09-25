package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.overdex.model.AnchorRegion
import kotlin.math.floor
import kotlin.math.roundToInt

data class BattleCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun asRect() = Rect(left, top, right, bottom)
}

data class BattleCropProvenance(
    val cropName: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val bounds: BattleCropBounds
)
data class ResolvedBattleCrop(val provenance: BattleCropProvenance, val bitmap: Bitmap)

/** A crop may inspect a measured subsection of its calibrated Battle Region. */
data class CropAreaInRegion(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f
) {
    init {
        require(x >= 0f && y >= 0f && width > 0f && height > 0f && x + width <= 1f && y + height <= 1f)
    }
}

/** The sole normalized battle-region to pixel-crop resolver. */
object BattleCropResolver {
    fun resolveRect(
        region: AnchorRegion,
        sourceWidth: Int,
        sourceHeight: Int,
        minimumSize: Int = 1,
        area: CropAreaInRegion = CropAreaInRegion()
    ): Rect? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || region.x < 0f || region.y < 0f ||
            region.width <= 0f || region.height <= 0f || region.x + region.width > 1.000001f || region.y + region.height > 1.000001f) return null
        val left = region.x + region.width * area.x
        val top = region.y + region.height * area.y
        val right = left + region.width * area.width
        val bottom = top + region.height * area.height
        val rect = Rect(
            floor(left * sourceWidth).toInt(), floor(top * sourceHeight).toInt(),
            (right * sourceWidth).roundToInt(), (bottom * sourceHeight).roundToInt()
        )
        return rect.takeIf { it.width() >= minimumSize && it.height() >= minimumSize }
    }

    fun resolve(
        cropName: String,
        region: AnchorRegion,
        source: Bitmap,
        minimumSize: Int = 1,
        area: CropAreaInRegion = CropAreaInRegion()
    ): ResolvedBattleCrop? {
        val rect = resolveRect(region, source.width, source.height, minimumSize, area) ?: return null
        return try {
            ResolvedBattleCrop(
                BattleCropProvenance(
                    cropName,
                    source.width,
                    source.height,
                    BattleCropBounds(rect.left, rect.top, rect.right, rect.bottom)
                ),
                Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
            )
        }
        catch (_: IllegalArgumentException) { null }
    }
}
