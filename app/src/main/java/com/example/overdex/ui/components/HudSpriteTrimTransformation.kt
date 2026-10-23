package com.example.overdex.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/** Cached display-only trim: keep every visible pixel and the original asset. */
internal object HudSpriteTrimTransformation : Transformation {
    override val cacheKey = "overdex-hud-visible-sprite-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 == 0) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || (left == 0 && top == 0 && right == width - 1 && bottom == height - 1)) {
            return input
        }
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}
