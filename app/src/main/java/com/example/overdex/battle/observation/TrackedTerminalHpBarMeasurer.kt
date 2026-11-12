package com.example.overdex.battle.observation

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

/** The actual interior colour, separate from a geometrically measured fraction. */
internal enum class HpBarFillColor { GREEN, YELLOW, RED, EMPTY }

internal data class TrackedTerminalHpBarSample(
    val measurement: ActiveHpBarMeasurement,
    val fillColor: HpBarFillColor,
)

/**
 * A small follow-up measurement of a recently located bar, not an acquisition
 * search. The caller owns the combatant, freshness, low-HP trigger, and expiry.
 *
 * Reads one bounded rectangle around the last complete outline. A missing bar
 * is unavailable evidence. Zero is returned only for a still-visible paired
 * outline, both vertical caps, and a dark, visibly unfilled interior.
 */
internal object TrackedTerminalHpBarMeasurer {
    fun measure(
        bitmap: Bitmap, anchor: ActiveHpBarMeasurement, territory: BattleCropBounds? = null,
    ): TrackedTerminalHpBarSample? {
        val bounds = pixelBounds(bitmap.width, bitmap.height, anchor, territory) ?: return null
        val pixels = IntArray(bounds.width * bounds.height)
        bitmap.getPixels(pixels, 0, bounds.width, bounds.left, bounds.top, bounds.width, bounds.height)
        return measureRegion(bounds, anchor, pixels)
    }

    internal fun measure(
        width: Int,
        height: Int,
        anchor: ActiveHpBarMeasurement,
        pixelAt: (x: Int, y: Int) -> Int,
    ): TrackedTerminalHpBarSample? = measure(width, height, anchor, null, pixelAt)

    internal fun measure(
        width: Int,
        height: Int,
        anchor: ActiveHpBarMeasurement,
        territory: BattleCropBounds?,
        pixelAt: (x: Int, y: Int) -> Int,
    ): TrackedTerminalHpBarSample? {
        val bounds = pixelBounds(width, height, anchor, territory) ?: return null
        val pixels = IntArray(bounds.width * bounds.height)
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            pixels[(y - bounds.top) * bounds.width + x - bounds.left] = pixelAt(x, y)
        }
        return measureRegion(bounds, anchor, pixels)
    }

    /** Used to confirm that a normal locator's low fraction really has red fill. */
    fun measureFillColor(bitmap: Bitmap, measurement: ActiveHpBarMeasurement): HpBarFillColor? =
        measure(bitmap, measurement)?.fillColor

    internal fun measureFillColor(
        width: Int,
        height: Int,
        measurement: ActiveHpBarMeasurement,
        pixelAt: (x: Int, y: Int) -> Int,
    ): HpBarFillColor? = measure(width, height, measurement, pixelAt)?.fillColor

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** The visibility mask and the pixel reader must cover precisely the same region. */
    internal fun searchBounds(
        width: Int, height: Int, anchor: ActiveHpBarMeasurement, territory: BattleCropBounds? = null,
    ): BattleCropBounds? = pixelBounds(width, height, anchor, territory)?.let {
        BattleCropBounds(it.left, it.top, it.right, it.bottom)
    }

    private fun pixelBounds(
        width: Int, height: Int, anchor: ActiveHpBarMeasurement, territory: BattleCropBounds?,
    ): Bounds? {
        val barHeight = anchor.bottom - anchor.top
        if (width <= 0 || height <= 0 || anchor.width < 32 || barHeight !in 6..(anchor.width / 4) ||
            anchor.left < 1 || anchor.right >= width || anchor.top < 1 || anchor.bottom >= height - 1
        ) return null
        if (territory != null && (territory.left < 0 || territory.top < 0 ||
                territory.right > width || territory.bottom > height ||
                anchor.left < territory.left || anchor.right > territory.right ||
                anchor.top < territory.top || anchor.bottom >= territory.bottom)) return null
        val yMargin = verticalMargin(barHeight) + HEIGHT_TOLERANCE
        return Bounds(
            (anchor.left - X_MARGIN).coerceAtLeast(territory?.left ?: 0),
            (anchor.top - yMargin).coerceAtLeast(territory?.top ?: 0),
            (anchor.right + X_MARGIN).coerceAtMost(territory?.right ?: width),
            (anchor.bottom + yMargin + 1).coerceAtMost(territory?.bottom ?: height),
        )
    }

    // The empty outline drops by about one bar height as the defeated sprite
    // shrinks. Retain a small local search through that movement; a large charge
    // camera relocation still needs independent acquisition and ownership.
    private fun verticalMargin(height: Int) = height.coerceIn(3, 16)

    private fun measureRegion(
        bounds: Bounds,
        anchor: ActiveHpBarMeasurement,
        pixels: IntArray,
    ): TrackedTerminalHpBarSample? {
        // Pixel classification is linear in this tiny region, never the frame.
        val flags = IntArray(pixels.size) { classify(pixels[it]) }
        fun flagsAt(x: Int, y: Int) = flags[(y - bounds.top) * bounds.width + x - bounds.left]
        val expectedHeight = anchor.bottom - anchor.top
        val margin = verticalMargin(expectedHeight)
        var best: TrackedTerminalHpBarSample? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (top in max(bounds.top + 1, anchor.top - margin)..minOf(bounds.bottom - 2, anchor.top + margin)) {
            val centerBottom = top + expectedHeight
            for (bottom in max(top + 6, centerBottom - HEIGHT_TOLERANCE)..minOf(bounds.bottom - 2, centerBottom + HEIGHT_TOLERANCE)) {
                for (outline in intArrayOf(WHITE, ORANGE, BLUE)) {
                    // Full-length paired lines are mandatory; a single scenery
                    // line or the glow of a faint cannot stand in for a bar.
                    val rowLeft = anchor.left + X_MARGIN
                    val rowRight = anchor.right - X_MARGIN
                    val topSupport = rowSupport(rowLeft, rowRight) { x -> flagsAt(x, top) and outline != 0 }
                    if (topSupport < MIN_LINE_SUPPORT) continue
                    val bottomSupport = rowSupport(rowLeft, rowRight) { x -> flagsAt(x, bottom) and outline != 0 }
                    if (bottomSupport < MIN_LINE_SUPPORT) continue
                    fun capAt(expectedX: Int, direction: Int): Pair<Int, Float>? =
                        (max(bounds.left + 1, expectedX - X_MARGIN)..minOf(bounds.right - 2, expectedX + X_MARGIN))
                            .mapNotNull { x ->
                                val innerX = x + direction * max(4, anchor.width / 80 + 1)
                                if (innerX !in bounds.left until bounds.right) return@mapNotNull null
                                val support = rowSupport(top + 1, bottom) { y ->
                                    flagsAt(x, y) and outline != 0 && flagsAt(innerX, y) and outline == 0
                                }
                                if (support < MIN_CAP_SUPPORT) null
                                else x to (support - abs(x - expectedX) * .025f)
                            }.maxByOrNull { it.second }
                    val left = capAt(anchor.left, 1) ?: continue
                    val right = capAt(anchor.right - 1, -1) ?: continue
                    if (abs((right.first + 1 - left.first) - anchor.width) > X_MARGIN * 2) continue
                    val shape = ActiveHpBarMeasurement(left.first, top, right.first + 1, bottom, 0f, 0f)
                    val sample = measureInterior(shape, ::flagsAt) ?: continue
                    val score = topSupport + bottomSupport + left.second + right.second -
                        abs(top - anchor.top) * .015f - abs(bottom - top - expectedHeight) * .02f
                    if (score > bestScore) {
                        bestScore = score
                        best = sample.copy(measurement = sample.measurement.copy(
                            confidence = ((topSupport + bottomSupport + left.second + right.second) / 4f)
                                .coerceIn(0f, .99f),
                        ))
                    }
                }
            }
        }
        return best
    }

    private fun measureInterior(
        bar: ActiveHpBarMeasurement,
        flagsAt: (Int, Int) -> Int,
    ): TrackedTerminalHpBarSample? {
        val inset = max(2, bar.width / 80)
        val left = bar.left + inset
        val right = bar.right - inset
        val verticalInset = max(2, (bar.bottom - bar.top) / 4)
        val top = bar.top + verticalInset
        val bottom = bar.bottom - verticalInset
        if (right - left < 24 || bottom - top < 3) return null
        val requiredRows = max(2, ((bottom - top) * .60f).toInt())
        val columns = IntArray(right - left)
        val orangeColumns = BooleanArray(columns.size)
        var darkPixels = 0
        var saturatedPixels = 0
        var nonDarkPixels = 0
        for (x in left until right) {
            var red = 0
            var yellow = 0
            var green = 0
            var orange = 0
            for (y in top until bottom) {
                val pixel = flagsAt(x, y)
                if (pixel and RED != 0) red++
                if (pixel and YELLOW != 0) yellow++
                if (pixel and GREEN != 0) green++
                if (pixel and ORANGE != 0) orange++
                if (pixel and (RED or YELLOW or GREEN or ORANGE or UNRESOLVED_RED) != 0) saturatedPixels++
                if (pixel and DARK != 0) darkPixels++ else nonDarkPixels++
            }
            columns[x - left] = when {
                red >= requiredRows -> RED
                yellow >= requiredRows -> YELLOW
                green >= requiredRows -> GREEN
                else -> 0
            }
            orangeColumns[x - left] = orange >= requiredRows
        }
        val first = columns.indexOfFirst { it != 0 }
        if (first < 0) {
            // A dim unknown/particle does not prove zero. Even sub-column red
            // evidence suppresses EMPTY until an actually clear frame arrives.
            val total = darkPixels + nonDarkPixels
            if (saturatedPixels != 0 || darkPixels < total * .97f) return null
            return TrackedTerminalHpBarSample(bar.copy(filledFraction = 0f), HpBarFillColor.EMPTY)
        }
        // At native Pixel resolution the neutral/blue outline and its inner
        // antialiasing leave four to five dark columns before the red fill.
        // Permit only a short, width-scaled RED prefix; a detached particle
        // elsewhere in the frame still cannot start an HP band.
        val prefixLimit = if (columns[first] == RED) max(2, columns.size / 40) else 2
        if (first > prefixLimit) return null
        var last = first
        var gap = 0
        for (index in first + 1 until columns.size) {
            if (columns[index] != 0) {
                last = index
                gap = 0
            } else if (++gap > 1) break
        }
        // An interrupted fill band is unavailable, never a shortened estimate.
        if ((last + 1 until columns.size).any { columns[it] != 0 }) return null
        val fraction = (last + 1).toFloat() / columns.size
        val filled = columns.sliceArray(first..last)
        val dominant = listOf(RED, YELLOW, GREEN).maxBy { color -> filled.count { it == color } }
        if (filled.count { it == dominant } < filled.size * .90f) return null
        if (dominant == GREEN && fraction <= .35f) return null
        if (dominant == YELLOW && fraction <= .12f) return null
        // The part after the measured endpoint must look like the dark bar
        // interior, not an unrecognised flash or a bright sprite background.
        var tailStart = last + 1
        if (dominant == RED && tailStart < columns.size) {
            // The actual red bar can be followed immediately by GO's orange
            // delayed-damage remainder. It is not remaining HP. Exempt only a
            // continuous, vertically supported orange band touching the red
            // endpoint (at most one antialiased gap column), never arbitrary
            // orange pixels elsewhere in the dark interior.
            val damageStart = (tailStart..minOf(tailStart + 1, columns.lastIndex))
                .firstOrNull { orangeColumns[it] }
            if (damageStart != null) {
                var damageEnd = damageStart
                while (damageEnd + 1 < columns.size && orangeColumns[damageEnd + 1]) damageEnd++
                if (damageEnd - damageStart + 1 > columns.size * .35f) return null
                tailStart = damageEnd + 1
            }
        }
        val tailPixels = (columns.size - tailStart) * (bottom - top)
        if (tailPixels > 0) {
            var darkTail = 0
            var saturatedTail = 0
            for (x in left + tailStart until right) for (y in top until bottom) {
                val flags = flagsAt(x, y)
                if (flags and DARK != 0) darkTail++
                if (flags and (RED or YELLOW or GREEN or ORANGE) != 0) saturatedTail++
            }
            if (saturatedTail > 0 || darkTail < tailPixels * .90f) return null
        }
        val fillColor = when (dominant) {
            RED -> HpBarFillColor.RED
            YELLOW -> HpBarFillColor.YELLOW
            else -> HpBarFillColor.GREEN
        }
        return TrackedTerminalHpBarSample(bar.copy(filledFraction = fraction), fillColor)
    }

    private inline fun rowSupport(start: Int, endExclusive: Int, predicate: (Int) -> Boolean): Float {
        if (endExclusive <= start) return 0f
        var found = 0
        for (value in start until endExclusive) if (predicate(value)) found++
        return found.toFloat() / (endExclusive - start)
    }

    private fun classify(color: Int): Int {
        if (color ushr 24 < 230) return 0
        val r = color ushr 16 and 255
        val g = color ushr 8 and 255
        val b = color and 255
        val neutral = minOf(r, g, b) >= 175 && maxOf(r, g, b) - minOf(r, g, b) <= 48
        // Real Pixel red HP is pale salmon (roughly 236,133,117). Its green
        // and blue are close; orange damage/border has a much larger G-B gap.
        val red = r >= 160 && g <= 155 && b <= 165 && r - g >= 60 && r - b >= 50 && abs(g - b) <= 50
        // A dim red sliver is not confidently accepted fill, but its hue still
        // contradicts an empty dark interior. Keep that uncertainty explicit:
        // the 3% antialiasing allowance must not erase a one-column red sliver.
        val unresolvedRed = !red && r - g >= 40 && r - b >= 40
        val orange = !red && r >= 155 && g in 65..210 && b <= 135 && r - g >= 22 && g - b >= 12
        val green = g >= 145 && g - r >= 55 && g - b >= 15
        // Neutral outline antialiasing over olive scenery can be (151,163,141).
        // Genuine yellow HP has substantially less blue, not just high R/G.
        val yellow = r >= 145 && g >= 145 && b <= 145 && abs(r - g) <= 55 && minOf(r, g) - b >= 40
        val dark = maxOf(r, g, b) <= 155 && maxOf(r, g, b) - minOf(r, g, b) <= 70 &&
            !red && !unresolvedRed && !orange && !green && !yellow
        return (if (neutral) WHITE else 0) or (if (orange) ORANGE else 0) or
            (if (isBlueHpBarBorder(r, g, b)) BLUE else 0) or (if (red) RED else 0) or
            (if (yellow) YELLOW else 0) or (if (green) GREEN else 0) or (if (dark) DARK else 0) or
            (if (unresolvedRed) UNRESOLVED_RED else 0)
    }

    private const val WHITE = 1
    private const val ORANGE = 2
    private const val BLUE = 4
    private const val RED = 8
    private const val YELLOW = 16
    private const val GREEN = 32
    private const val DARK = 64
    private const val UNRESOLVED_RED = 128
    private const val X_MARGIN = 3
    private const val HEIGHT_TOLERANCE = 2
    private const val MIN_LINE_SUPPORT = .94f
    private const val MIN_CAP_SUPPORT = .70f
}
