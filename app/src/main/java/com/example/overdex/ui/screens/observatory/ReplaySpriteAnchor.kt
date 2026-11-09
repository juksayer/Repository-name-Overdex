package com.example.overdex.ui.screens.observatory

import android.graphics.Bitmap

/** Visible pixel edges normalized to the square, centered ContentScale.Fit replay node. */
internal data class ReplaySpriteVisibleBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private val fullReplaySpriteBounds = ReplaySpriteVisibleBounds(0f, 0f, 1f, 1f)

/**
 * Measures alpha rather than a color palette, including faint edge pixels. Right and bottom are
 * exclusive pixel edges, so a fully opaque source covers its complete fitted rectangle. The source
 * stays untouched; unreadable or empty art uses the full node as a conservative fallback.
 */
internal fun replaySpriteVisibleBounds(bitmap: Bitmap): ReplaySpriteVisibleBounds {
    if (bitmap.isRecycled) return fullReplaySpriteBounds
    var readable: Bitmap? = null
    try {
        // Hardware bitmaps cannot be read with getPixels. The replay loader normally disables them,
        // but keep the geometry helper correct for any other decoded source as well.
        readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap
        val source = readable ?: return fullReplaySpriteBounds
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return fullReplaySpriteBounds
        val row = IntArray(width)
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if (row[x] ushr 24 == 0) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = y + 1
            }
        }
        if (right == 0) return fullReplaySpriteBounds
        val fitScale = 1f / maxOf(width, height)
        val fittedLeft = (1f - width * fitScale) / 2f
        val fittedTop = (1f - height * fitScale) / 2f
        return ReplaySpriteVisibleBounds(
            left = (fittedLeft + left * fitScale).coerceIn(0f, 1f),
            top = (fittedTop + top * fitScale).coerceIn(0f, 1f),
            right = (fittedLeft + right * fitScale).coerceIn(0f, 1f),
            bottom = (fittedTop + bottom * fitScale).coerceIn(0f, 1f),
        )
    } catch (_: IllegalStateException) {
        // A recycled source, including one recycled during the read, has no usable geometry.
        return fullReplaySpriteBounds
    } catch (_: IllegalArgumentException) {
        return fullReplaySpriteBounds
    } finally {
        readable?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
    }
}

/** Compatibility pivot for callers that only need the visible head's vertical position. */
internal fun replaySpriteVisibleTopFraction(bitmap: Bitmap): Float = replaySpriteVisibleBounds(bitmap).top
