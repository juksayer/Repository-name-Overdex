package com.example.overdex.model

/** D-pad amounts are source pixels; LCD drags are converted before entering here. */
fun CaptureRegion.adjustPixels(dx: Float, dy: Float, sourceWidth: Int, sourceHeight: Int, resize: Boolean): CaptureRegion {
    require(sourceWidth > 0 && sourceHeight > 0)
    val minW = 1f / sourceWidth
    val minH = 1f / sourceHeight
    val w = width.coerceIn(minW, 1f)
    val h = height.coerceIn(minH, 1f)
    val left = x.coerceIn(0f, 1f - w)
    val top = y.coerceIn(0f, 1f - h)
    return if (resize) copy(x = left, y = top, width = (w + dx / sourceWidth).coerceIn(minW, 1f - left),
        height = (h + dy / sourceHeight).coerceIn(minH, 1f - top))
    else copy(x = (left + dx / sourceWidth).coerceIn(0f, 1f - w), y = (top + dy / sourceHeight).coerceIn(0f, 1f - h), width = w, height = h)
}
