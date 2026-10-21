package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Color
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import kotlin.math.abs

/** Recognition treatments are temporary; the original dial crop remains the evidence. */
internal object DecisionTimerTextReader {
    suspend fun read(bitmap: Bitmap): String {
        val raw = SharedLatinTextRecognizer.readText(bitmap)
        if (DecisionTimerReadingFilter.parse(raw) != null) return raw
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bounds = DecisionTimerNumerals.bounds(bitmap.width, bitmap.height) { x, y -> pixels[y * bitmap.width + x] }
            ?: return raw
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val scale = 3
        val padding = 16
        val renderedWidth = width * scale + padding * 2
        val renderedHeight = height * scale + padding * 2
        val rendered = IntArray(renderedWidth * renderedHeight) { Color.WHITE }
        for (y in 0 until height) for (x in 0 until width) {
            if (!DecisionTimerNumerals.isWhite(pixels[(bounds.top + y) * bitmap.width + bounds.left + x])) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                rendered[(padding + y * scale + dy) * renderedWidth + padding + x * scale + dx] = Color.BLACK
            }
        }
        val isolated = Bitmap.createBitmap(rendered, renderedWidth, renderedHeight, Bitmap.Config.ARGB_8888)
        return try { SharedLatinTextRecognizer.readText(isolated).ifBlank { raw } }
        finally { isolated.recycle() }
    }
}

/** Select compact white numeral components, excluding the much taller circular arc and sky. */
internal object DecisionTimerNumerals {
    fun isWhite(color: Int): Boolean {
        val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
        return minOf(r, g, b) > 200 && maxOf(r, g, b) - minOf(r, g, b) < 50
    }

    fun bounds(width: Int, height: Int, pixel: (Int, Int) -> Int): BattleCropBounds? {
        val white = BooleanArray(width * height) { isWhite(pixel(it % width, it / width)) }
        val candidates = mutableListOf<BattleCropBounds>()
        val queue = IntArray(white.size)
        for (seed in white.indices) {
            if (!white[seed]) continue
            var head = 0; var tail = 1
            queue[0] = seed; white[seed] = false
            var left = width; var top = height; var right = 0; var bottom = 0
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                fun visit(next: Int) { if (white[next]) { white[next] = false; queue[tail++] = next } }
                if (x > 0) visit(index - 1)
                if (x + 1 < width) visit(index + 1)
                if (y > 0) visit(index - width)
                if (y + 1 < height) visit(index + width)
            }
            if (bottom - top in (width * .15f).toInt()..(width * .55f).toInt() &&
                right - left <= width * .4f && abs((left + right) / 2f - width / 2f) <= width * .3f && tail >= 20) {
                candidates += BattleCropBounds(left, top, right, bottom)
            }
        }
        val centre = candidates.minByOrNull { abs((it.left + it.right) / 2f - width / 2f) } ?: return null
        val digitHeight = centre.bottom - centre.top
        // Keep both glyphs of 10..15. An arc fragment has a different baseline/height.
        val digits = candidates.filter {
            abs((it.top + it.bottom) - (centre.top + centre.bottom)) / 2f <= digitHeight * .25f &&
                (it.bottom - it.top).toFloat() / digitHeight in .7f..1.3f
        }
        return BattleCropBounds(digits.minOf { it.left }, digits.minOf { it.top },
            digits.maxOf { it.right }, digits.maxOf { it.bottom })
    }
}
