package com.example.overdex.battle.observation

import kotlin.math.max

/** Geometry/color evidence is not an HP amount. In particular orange-only is never zero. */
internal data class CompleteHpBarSample(
    val bounds: BattleCropBounds,
    val confidence: Float,
    val interiorColumns: Int,
    val liveColumns: Int,
    val damageColumns: Int,
    val orangeOnly: Boolean,
    val railBrightness: Float,
    val backgroundBrightness: Float?,
    val hpSample: TrackedTerminalHpBarSample? = null,
    val backgroundMaskedColumns: Int = 0,
    val unmaskedUncertainColumns: Int = 0,
) {
    // A geometry prior only. Callers may publish HP exclusively from hpSample.
    fun anchor() = ActiveHpBarMeasurement(bounds.left, bounds.top, bounds.right, bounds.bottom,
        hpSample?.measurement?.filledFraction ?: 0f, confidence)
}

internal object CompleteHpBarProfile {
    fun measure(bar: ActiveHpBarMeasurement, territory: BattleCropBounds, pixelAt: (Int, Int) -> Int): CompleteHpBarSample {
        val inset = max(2, bar.width / 80)
        val vertical = max(2, (bar.bottom - bar.top) / 4)
        val left = bar.left + inset
        val right = bar.right - inset
        val top = bar.top + vertical
        val bottom = bar.bottom - vertical
        val orange = BooleanArray(right - left)
        var live = 0
        val uncertain = BooleanArray(right - left)
        val protectedColor = BooleanArray(right - left)
        for (x in left until right) {
            var livePixels = 0
            var orangePixels = 0
            for (y in top until bottom) {
                val c = pixelAt(x, y)
                val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
                val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val delta = hi - lo
                val hue = when {
                    delta == 0 -> 0f
                    hi == r -> ((g - b).toFloat() / delta * 60f + 360f) % 360f
                    hi == g -> (b - r).toFloat() / delta * 60f + 120f
                    else -> (r - g).toFloat() / delta * 60f + 240f
                }
                val saturated = hi > 0 && delta * 255 >= hi * 100
                val liveHue = hue < 16f || hue in 48f..190f || hue >= 348f
                if (saturated && hi >= 160) {
                    if (hue in 16f..48f) orangePixels++
                    else if (liveHue) livePixels++
                    else uncertain[x - left] = true
                    // Bright effects/unknown colors are never background-mask candidates.
                    protectedColor[x - left] = true
                } else if (delta >= 25 && hi >= 80 && hue !in 16f..48f) {
                    uncertain[x - left] = true
                    // Preserve even dim red/yellow/green slivers as unresolved evidence.
                    // Darkness or location after an orange run cannot erase possible HP.
                    if (liveHue || hi >= 160) protectedColor[x - left] = true
                }
            }
            // Even one surviving source column blocks orange-only. Never erode the mask.
            if (livePixels > 0) live++
            orange[x - left] = orangePixels >= max(2, (bottom - top) * 3 / 5)
        }
        val first = orange.indexOfFirst { it }
        val last = orange.indexOfLast { it }
        val damage = orange.count { it }
        val coherentDamage = first in 0..3 && damage >= 3 && damage >= (last - first + 1) * .9f
        var backgroundMasked = 0
        var unmaskedUncertain = 0
        for (column in uncertain.indices) {
            if (!uncertain[column]) continue
            // A derived color-test mask only, after independently verified whole-bar geometry.
            // Mask dim non-HP-colored uncertainty strictly AFTER a coherent damage prefix.
            // Keep uncertainty before/inside fill, bright effects, and all possible live HP.
            if (coherentDamage && live == 0 && column > last && !protectedColor[column]) backgroundMasked++
            else unmaskedUncertain++
        }
        val orangeOnly = live == 0 && unmaskedUncertain == 0 && coherentDamage
        fun brightness(c: Int) = maxOf(c ushr 16 and 255, c ushr 8 and 255, c and 255).toFloat()
        val rail = (left until right).sumOf { x ->
            (brightness(pixelAt(x, bar.top)) + brightness(pixelAt(x, bar.bottom))).toDouble()
        }.toFloat() / ((right - left) * 2)
        val backgroundRows = listOf(bar.top - 3, bar.bottom + 3).filter { it in territory.top until territory.bottom }
        val background = backgroundRows.takeIf { it.isNotEmpty() }?.let { rows ->
            rows.sumOf { y -> (left until right).sumOf { x -> brightness(pixelAt(x, y)).toDouble() } }.toFloat() /
                ((right - left) * rows.size)
        }
        return CompleteHpBarSample(BattleCropBounds(bar.left, bar.top, bar.right, bar.bottom),
            bar.confidence, right - left, live, damage, orangeOnly, rail, background,
            backgroundMaskedColumns = backgroundMasked, unmaskedUncertainColumns = unmaskedUncertain)
    }
}
