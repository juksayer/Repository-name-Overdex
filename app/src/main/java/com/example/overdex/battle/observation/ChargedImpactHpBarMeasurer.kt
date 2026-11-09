package com.example.overdex.battle.observation

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

/**
 * Acquires a single centered charge-camera bar of an already owned bar's size.
 * The phase policy supplies ownership; geometry alone never chooses a side.
 * Unlike the ordinary locator this can find a still-visible EMPTY outline.
 */
internal object ChargedImpactHpBarMeasurer {
    fun searchBounds(
        width: Int, height: Int, reference: ActiveHpBarMeasurement,
        territory: BattleCropBounds? = null,
    ): BattleCropBounds? {
        if (width <= 0 || height <= 0 || reference.width !in 32..width / 2 ||
            reference.bottom - reference.top !in 6..reference.width / 4) return null
        if (territory != null) return territory.takeIf {
            it.left >= 0 && it.top >= 0 && it.right <= width && it.bottom <= height &&
                it.right > it.left && it.bottom > it.top
        }
        val half = reference.width / 2 + max(8, width * 6 / 100)
        return BattleCropBounds((width / 2 - half).coerceAtLeast(0), height * 34 / 100,
            (width / 2 + half).coerceAtMost(width), height * 62 / 100)
    }

    fun acquire(
        bitmap: Bitmap, reference: ActiveHpBarMeasurement, territory: BattleCropBounds? = null,
    ): TrackedTerminalHpBarSample? {
        val bounds = searchBounds(bitmap.width, bitmap.height, reference, territory) ?: return null
        val width = bounds.right - bounds.left
        val pixels = IntArray(width * (bounds.bottom - bounds.top))
        bitmap.getPixels(pixels, 0, width, bounds.left, bounds.top, width, bounds.bottom - bounds.top)
        return acquire(bitmap.width, bitmap.height, reference, bounds, pixels)
    }

    internal fun acquire(
        width: Int, height: Int, reference: ActiveHpBarMeasurement,
        pixelAt: (Int, Int) -> Int,
    ): TrackedTerminalHpBarSample? = acquire(width, height, reference, null, pixelAt)

    internal fun acquire(
        width: Int, height: Int, reference: ActiveHpBarMeasurement,
        territory: BattleCropBounds?, pixelAt: (Int, Int) -> Int,
    ): TrackedTerminalHpBarSample? {
        val bounds = searchBounds(width, height, reference, territory) ?: return null
        val regionWidth = bounds.right - bounds.left
        val pixels = IntArray(regionWidth * (bounds.bottom - bounds.top))
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right)
            pixels[(y - bounds.top) * regionWidth + x - bounds.left] = pixelAt(x, y)
        return acquire(width, height, reference, bounds, pixels)
    }

    private data class Run(val y: Int, val left: Int, val right: Int, val color: Int)

    private fun acquire(
        width: Int, height: Int, reference: ActiveHpBarMeasurement,
        bounds: BattleCropBounds, pixels: IntArray,
    ): TrackedTerminalHpBarSample? {
        val regionWidth = bounds.right - bounds.left
        val flags = IntArray(pixels.size) { outline(pixels[it]) }
        val runs = mutableListOf<Run>()
        val minWidth = (reference.width * .94f).toInt()
        val maxWidth = (reference.width * 1.06f).toInt()
        for (y in bounds.top until bounds.bottom) for (color in intArrayOf(1, 2, 4)) {
            var start = -1
            var last = -1
            fun finish() {
                if (start >= 0 && last + 1 - start in minWidth..maxWidth)
                    runs += Run(y, start, last + 1, color)
                start = -1
                last = -1
            }
            for (x in bounds.left until bounds.right) {
                if (flags[(y - bounds.top) * regionWidth + x - bounds.left] and color != 0) {
                    if (start < 0) start = x
                    last = x
                } else if (last >= 0 && x - last > 2) finish()
            }
            finish()
            // Scenery with many lookalike lines is ambiguity, not extra work.
            if (runs.size > 96) return null
        }
        val candidates = mutableListOf<ActiveHpBarMeasurement>()
        val barHeight = reference.bottom - reference.top
        for (top in runs) for (bottom in runs) {
            if (bottom.y - top.y !in max(6, barHeight * 2 / 3)..barHeight * 3 / 2 ||
                top.color != bottom.color || abs(top.left - bottom.left) > 3 ||
                abs(top.right - bottom.right) > 3) continue
            val candidate = ActiveHpBarMeasurement(max(top.left, bottom.left), top.y,
                minOf(top.right, bottom.right), bottom.y, 0f, 0f)
            if (abs(candidate.centerX - width / 2f) > width * .06f) continue
            if (candidates.none { abs(it.left - candidate.left) <= 3 && abs(it.right - candidate.right) <= 3 &&
                    abs(it.top - candidate.top) <= 3 && abs(it.bottom - candidate.bottom) <= 3 }) candidates += candidate
            if (candidates.size > 6) return null
        }
        val measured = candidates.mapNotNull { candidate ->
            // The existing terminal reader requires paired outlines, both caps,
            // and a clean interior. Orange-only, flash and absent remain unknown.
            TrackedTerminalHpBarMeasurer.measure(width, height, candidate) { x, y ->
                if (x in bounds.left until bounds.right && y in bounds.top until bounds.bottom)
                    pixels[(y - bounds.top) * regionWidth + x - bounds.left] else 0
            }
        }
        val distinct = mutableListOf<TrackedTerminalHpBarSample>()
        measured.sortedByDescending { it.measurement.confidence }.forEach { sample ->
            if (distinct.none { abs(it.measurement.left - sample.measurement.left) <= 4 &&
                    abs(it.measurement.top - sample.measurement.top) <= 4 }) distinct += sample
        }
        return distinct.singleOrNull()
    }

    private fun outline(color: Int): Int {
        if (color ushr 24 < 230) return 0
        val r = color ushr 16 and 255
        val g = color ushr 8 and 255
        val b = color and 255
        return (if (minOf(r, g, b) >= 175 && maxOf(r, g, b) - minOf(r, g, b) <= 48) 1 else 0) or
            (if (r >= 155 && g in 65..210 && b <= 135 && r - g >= 22 && g - b >= 12) 2 else 0) or
            (if (isBlueHpBarBorder(r, g, b)) 4 else 0)
    }
}
