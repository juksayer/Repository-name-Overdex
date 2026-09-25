package com.example.overdex.battle.observation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Geometry of one low-saturation bright component in a countdown crop. */
internal data class CountdownComponentGeometry(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pixelCount: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Scores the paired, centered G and O forms without altering their source crop. */
internal object CountdownGoGeometry {
    fun score(
        first: CountdownComponentGeometry,
        second: CountdownComponentGeometry,
        sourceWidth: Int,
        sourceHeight: Int
    ): Float? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || first.width <= 0 || second.width <= 0 ||
            first.height <= 0 || second.height <= 0
        ) return null

        val left = if (first.left <= second.left) first else second
        val right = if (left === first) second else first
        val componentArea = sourceWidth.toFloat() * sourceHeight
        val smallerCoverage = min(left.pixelCount, right.pixelCount) / componentArea
        val areaBalance = min(left.pixelCount, right.pixelCount).toFloat() / max(left.pixelCount, right.pixelCount)
        val heightBalance = min(left.height, right.height).toFloat() / max(left.height, right.height)
        val verticalOverlap = max(0, min(left.bottom, right.bottom) - max(left.top, right.top)).toFloat() /
            min(left.height, right.height)
        val pairLeft = min(left.left, right.left)
        val pairRight = max(left.right, right.right)
        val pairSpan = (pairRight - pairLeft).toFloat() / sourceWidth
        val averageHeight = (left.height + right.height) / 2f / sourceHeight
        val pairCenter = (pairLeft + pairRight) / 2f / sourceWidth
        val centerOffset = abs(pairCenter - 0.5f)

        if (smallerCoverage < 0.08f || areaBalance < 0.55f || heightBalance < 0.78f ||
            verticalOverlap < 0.78f || pairSpan < 0.70f || averageHeight !in 0.40f..0.78f ||
            centerOffset > 0.12f
        ) return null

        return minOf(
            areaBalance,
            heightBalance,
            verticalOverlap,
            (smallerCoverage / 0.12f).coerceAtMost(1f),
            (pairSpan / 0.85f).coerceAtMost(1f),
            ((0.12f - centerOffset) / 0.12f).coerceIn(0f, 1f)
        )
    }
}
