package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.AnchorRegion
import kotlin.math.abs

/**
 * Finds the teal name row near its calibrated position, without running OCR
 * over the rest of the badge. X and strip size remain the user's assignment.
 * This only proposes crop geometry; catalogue OCR still establishes identity.
 */
internal object SpeciesBadgeTextLocator {
    fun locate(
        source: Bitmap,
        calibrated: AnchorRegion,
        excludedRegions: List<AnchorRegion> = emptyList(),
    ): AnchorRegion {
        val rect = BattleCropResolver.resolveRect(calibrated, source.width, source.height) ?: return calibrated
        val height = rect.height()
        val width = rect.width()
        if (height < 8 || width < 16) return calibrated
        val top = (rect.top - height * 2).coerceAtLeast(0)
        val bottom = (rect.bottom + height * 2).coerceAtMost(source.height)
        val pixels = IntArray(width * (bottom - top))
        source.getPixels(pixels, 0, width, rect.left, top, width, bottom - top)
        val excluded = excludedRegions.mapNotNull {
            BattleCropResolver.resolveRect(it, source.width, source.height)
        }
        data class Line(val top: Int, val bottom: Int, val ink: Int, val left: Int, val right: Int)
        val lines = mutableListOf<Line>()
        var start = -1
        var last = -1
        var totalInk = 0
        var left = width
        var right = 0
        fun finish() {
            if (start >= 0) lines += Line(start, last + 1, totalInk, left, right)
            start = -1; totalInk = 0; left = width; right = 0
        }
        for (row in 0 until bottom - top) {
            var ink = 0
            var paper = 0
            var rowLeft = width
            var rowRight = 0
            for (x in 0 until width) {
                // Locate the game label before deciding whether it is covered.
                // Our HUD's name and question marks must not attract this search.
                if (excluded.any { it.contains(rect.left + x, top + row) }) continue
                val color = pixels[row * width + x]
                val r = (color ushr 16) and 255
                val g = (color ushr 8) and 255
                val b = color and 255
                if (r >= 150 && g >= 160 && b >= 150 && maxOf(r, g, b) - minOf(r, g, b) < 60) paper++
                // Excludes red balls, purple shields, white edges and blue sky.
                if (r in 20..145 && g in 45..170 && b in 40..195 &&
                    g >= r + 10 && b >= r + 8 && abs(g - b) < 40) {
                    ink++; rowLeft = minOf(rowLeft, x); rowRight = maxOf(rowRight, x + 1)
                }
            }
            if (ink >= 3 && paper >= width * .4f) {
                if (start < 0) start = row
                last = row; totalInk += ink
                left = minOf(left, rowLeft); right = maxOf(right, rowRight)
            } else if (start >= 0 && row - last > 1) finish()
        }
        finish()
        val line = lines.filter {
            val lineHeight = it.bottom - it.top
            // The edge of the search band can cut through the status icons.
            // A clipped fragment is not a complete name row to recenter on.
            it.top >= 2 && it.bottom <= bottom - top - 2 &&
                lineHeight in 5..(height - 2) && it.ink >= 12 && it.right - it.left >= lineHeight * 1.3f
        }.maxByOrNull { it.ink } ?: return calibrated
        val inkTop = top + line.top
        val inkBottom = top + line.bottom
        if (inkTop >= rect.top + 2 && inkBottom <= rect.bottom - 2) return calibrated
        val stripTop = ((inkTop + inkBottom - height) / 2f).coerceIn(0f, (source.height - height).toFloat())
        return calibrated.copy(y = stripTop / source.height)
    }
}
