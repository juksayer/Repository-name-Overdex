package com.example.overdex.model

/** Physical display pixels in, normalized capture coordinates out; no system-bar offset. */
fun AnchorRegion.adjustPixels(dx: Float, dy: Float, sourceWidth: Int, sourceHeight: Int, resize: Boolean): AnchorRegion {
    require(sourceWidth > 0 && sourceHeight > 0)
    val minW = minOf(8f / sourceWidth, 1f)
    val minH = minOf(8f / sourceHeight, 1f)
    val w = width.coerceIn(minW, 1f)
    val h = height.coerceIn(minH, 1f)
    val left = x.coerceIn(0f, 1f - w)
    val top = y.coerceIn(0f, 1f - h)
    return if (resize) copy(x = left, y = top,
        width = (w + dx / sourceWidth).coerceIn(minW, 1f - left),
        height = (h + dy / sourceHeight).coerceIn(minH, 1f - top))
    else copy(x = (left + dx / sourceWidth).coerceIn(0f, 1f - w),
        y = (top + dy / sourceHeight).coerceIn(0f, 1f - h), width = w, height = h)
}
