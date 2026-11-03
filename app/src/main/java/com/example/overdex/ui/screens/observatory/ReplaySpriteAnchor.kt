package com.example.overdex.ui.screens.observatory

import android.graphics.Bitmap

/** Visible-art pivot in the square, ContentScale.Fit replay node. Leaves source pixels intact. */
internal fun replaySpriteVisibleTopFraction(bitmap: Bitmap): Float {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return 0f
    val row = IntArray(bitmap.width)
    var top = 0
    while (top < bitmap.height) {
        bitmap.getPixels(row, 0, bitmap.width, 0, top, bitmap.width, 1)
        if (row.any { it ushr 24 != 0 }) break
        top++
    }
    if (top == bitmap.height) return 0f
    val fittedHeight = minOf(1f, bitmap.height.toFloat() / bitmap.width)
    return ((1f - fittedHeight) / 2f + top.toFloat() / bitmap.height * fittedHeight).coerceIn(0f, 1f)
}
