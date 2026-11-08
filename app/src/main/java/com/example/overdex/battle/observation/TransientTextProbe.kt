package com.example.overdex.battle.observation

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Cheap visual ranking for a preserved announcement or QTE text crop.
 *
 * This does not read text, identify an event, or establish that an event is absent.
 * It helps retain a text-bearing frame while the semantic reader is occupied.
 * A caller must still retain its latest-frame fallback when this probe is weak.
 * Source pixels and their capture time remain the recognition evidence.
 */
internal object TransientTextProbe {
    /** Bounds in the supplied crop's pixels, with exclusive right/bottom edges. */
    data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        internal fun contains(x: Int, y: Int): Boolean =
            x >= left && x < right && y >= top && y < bottom
    }

    data class Reading(
        val score: Float,
        val hasText: Boolean,
        /** A bounded, non-semantic fingerprint of the selected glyph geometry. */
        val signature: Long,
        val textBounds: PixelBounds?,
    )

    private val EMPTY = Reading(0f, false, 0L, null)
    private const val MAX_WIDTH = 384
    private const val MAX_HEIGHT = 128

    /**
     * [argb] is a contiguous, row-major copy of the unaltered crop. Exclusions are
     * crop-local overlap bounds supplied by the caller, never inferred from HUD text.
     * This function neither stores nor mutates the source or its excluded pixels.
     */
    fun analyze(
        argb: IntArray,
        width: Int,
        height: Int,
        excludedRegions: List<PixelBounds> = emptyList(),
    ): Reading {
        if (width < 8 || height < 5 || width.toLong() * height > argb.size) return EMPTY
        val step = maxOf(1, ceil(width.toDouble() / MAX_WIDTH).toInt(), ceil(height.toDouble() / MAX_HEIGHT).toInt())
        val sampledWidth = (width - 1) / step + 1
        val sampledHeight = (height - 1) / step + 1
        if (sampledWidth < 8 || sampledHeight < 5) return EMPTY
        val size = sampledWidth * sampledHeight
        val glyph = BooleanArray(size)
        val luminance = IntArray(size)
        val available = BooleanArray(size)
        for (y in 0 until sampledHeight) {
            val sourceY = (y * step + step / 2).coerceAtMost(height - 1)
            for (x in 0 until sampledWidth) {
                val sourceX = (x * step + step / 2).coerceAtMost(width - 1)
                val index = y * sampledWidth + x
                if (excludedRegions.any { it.contains(sourceX, sourceY) }) continue
                val color = argb[sourceY * width + sourceX]
                if (color ushr 24 < 128) continue
                available[index] = true
                val r = color ushr 16 and 255
                val g = color ushr 8 and 255
                val b = color and 255
                luminance[index] = (r * 77 + g * 150 + b * 29) ushr 8
                val neutral = minOf(r, g, b) >= 140 && maxOf(r, g, b) - minOf(r, g, b) <= 42
                val yellow = r >= 165 && g >= 145 && b + 32 < minOf(r, g) && abs(r - g) <= 65
                glyph[index] = neutral || yellow
            }
        }

        val visited = BooleanArray(size)
        val queue = IntArray(size)
        val components = mutableListOf<Component>()
        for (start in 0 until size) {
            if (!glyph[start] || visited[start]) continue
            var count = 1
            var head = 0
            queue[0] = start
            visited[start] = true
            var left = sampledWidth
            var top = sampledHeight
            var right = 0
            var bottom = 0
            var contrasting = 0
            while (head < count) {
                val index = queue[head++]
                val x = index % sampledWidth
                val y = index / sampledWidth
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                if (hasLocalContrast(index, x, y, sampledWidth, sampledHeight, luminance, available)) contrasting++
                for (ny in maxOf(0, y - 1)..minOf(sampledHeight - 1, y + 1)) {
                    for (nx in maxOf(0, x - 1)..minOf(sampledWidth - 1, x + 1)) {
                        val next = ny * sampledWidth + nx
                        if (glyph[next] && !visited[next]) {
                            visited[next] = true
                            queue[count++] = next
                        }
                    }
                }
            }
            val componentHeight = bottom - top
            val componentWidth = right - left
            val contrast = contrasting.toFloat() / count
            // Edges of clouds, a clipped type bubble, and the dark HUD backdrop
            // are not a row of complete glyphs. Small detached punctuation can
            // be ignored; the remaining letters supply the row geometry.
            if (count >= 3 && componentHeight >= maxOf(3f, sampledHeight * .13f) &&
                componentHeight <= sampledHeight * .85f && componentWidth <= componentHeight * 3 &&
                left > 0 && top > 0 && right < sampledWidth && bottom < sampledHeight && contrast >= .35f
            ) {
                components += Component(left, top, right, bottom, count, contrast)
            }
        }
        if (components.size < 3) return EMPTY

        var best: List<Component> = emptyList()
        var bestScore = 0f
        val minimumAnchorHeight = components.map { it.height }.sorted()[components.size / 2]
        for (anchor in components) {
            // A short exclamation mark must not choose a beautifully aligned
            // subset that omits the word's descenders. Anchor on typical letters.
            if (anchor.height < minimumAnchorHeight) continue
            val line = components.filter {
                it.height >= anchor.height * .45f && it.height <= anchor.height * 1.8f &&
                    abs(it.centerY - anchor.centerY) <= maxOf(it.height, anchor.height) * .45f
            }
            if (line.size < 3) continue
            val left = line.minOf { it.left }
            val right = line.maxOf { it.right }
            val medianHeight = line.map { it.height }.sorted()[line.size / 2]
            if (right - left < medianHeight * 2) continue
            val rowSpread = line.maxOf { it.centerY } - line.minOf { it.centerY }
            val alignment = (1f - rowSpread / maxOf(1f, medianHeight.toFloat())).coerceIn(0f, 1f)
            val contrast = line.map { it.contrast }.average().toFloat()
            val score = (.45f * (line.size / 6f).coerceAtMost(1f) + .30f * contrast + .25f * alignment)
                .coerceIn(0f, 1f)
            if (score > bestScore) {
                bestScore = score
                best = line
            }
        }
        if (best.isEmpty()) return EMPTY
        val bounds = PixelBounds(best.minOf { it.left }, best.minOf { it.top }, best.maxOf { it.right }, best.maxOf { it.bottom })
        val hasText = bestScore >= .65f
        return Reading(
            score = bestScore,
            hasText = hasText,
            signature = if (hasText) signature(glyph, sampledWidth, bounds, best) else 0L,
            textBounds = PixelBounds(
                bounds.left * step, bounds.top * step,
                (bounds.right * step).coerceAtMost(width), (bounds.bottom * step).coerceAtMost(height),
            ),
        )
    }

    private data class Component(
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val ink: Int, val contrast: Float,
    ) {
        val height get() = bottom - top
        val centerY get() = (top + bottom) / 2f
    }

    private fun hasLocalContrast(
        index: Int, x: Int, y: Int, width: Int, height: Int,
        luminance: IntArray, available: BooleanArray,
    ): Boolean {
        for (ny in maxOf(0, y - 2)..minOf(height - 1, y + 2) step 2) {
            for (nx in maxOf(0, x - 2)..minOf(width - 1, x + 2) step 2) {
                val near = ny * width + nx
                if (available[near] && luminance[index] - luminance[near] >= 36) return true
            }
        }
        return false
    }

    private fun signature(glyph: BooleanArray, width: Int, bounds: PixelBounds, components: List<Component>): Long {
        // Quantized occupancy is less sensitive to anti-aliasing/background
        // movement than a pixel hash. Only accepted glyph components contribute.
        val occupied = BooleanArray(glyph.size)
        for (component in components) {
            for (y in component.top until component.bottom) for (x in component.left until component.right) {
                val index = y * width + x
                if (glyph[index]) occupied[index] = true
            }
        }
        var hash = -3750763034362895579L // FNV-1a offset basis, signed representation.
        for (row in 0 until 8) for (column in 0 until 32) {
            val left = bounds.left + column * (bounds.right - bounds.left) / 32
            val right = bounds.left + (column + 1) * (bounds.right - bounds.left) / 32
            val top = bounds.top + row * (bounds.bottom - bounds.top) / 8
            val bottom = bounds.top + (row + 1) * (bounds.bottom - bounds.top) / 8
            var ink = 0
            var total = 0
            for (y in top until maxOf(top + 1, bottom)) for (x in left until maxOf(left + 1, right)) {
                total++
                if (occupied[y * width + x]) ink++
            }
            val density = (ink * 3 / total).coerceAtMost(3)
            hash = (hash xor density.toLong()) * 1099511628211L
        }
        return if (hash == 0L) 1L else hash
    }
}
