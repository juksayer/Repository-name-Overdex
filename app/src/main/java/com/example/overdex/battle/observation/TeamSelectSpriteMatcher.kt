package com.example.overdex.battle.observation

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class TeamSelectSpriteDescriptor(
    val speciesId: Int,
    val variant: String,
    val values: FloatArray,
)

data class TeamSelectSpriteMatch(
    val speciesId: Int,
    val variant: String,
    val score: Float,
    val distinctSpeciesMargin: Float,
    val confidence: Float,
)

/**
 * Translation-tolerant matcher for the exact Pokémon GO art shown inside a
 * Team Select card. Normal, shiny, and regional references all resolve to one
 * Pokédex ID. It is independent of OCR, so nicknames and symbol-only names do
 * not prevent the player roster from being identified.
 */
class TeamSelectSpriteMatcher(assetManager: AssetManager) {
    private val descriptors: List<TeamSelectSpriteDescriptor> = assetManager
        .open(CATALOG_ASSET)
        .bufferedReader()
        .useLines(::parseCatalogue)

    fun match(bitmap: Bitmap): TeamSelectSpriteMatch? =
        matchDescriptor(TeamSelectSpriteDescriptorMeasurer.measure(bitmap), descriptors)

    internal companion object {
        private const val CATALOG_ASSET = "team_select_sprite_catalog.tsv"
        private const val MAX_ACCEPTED_SCORE = 1.05f
        private const val MIN_DISTINCT_SPECIES_MARGIN = 0.05f

        fun parseCatalogue(lines: Sequence<String>): List<TeamSelectSpriteDescriptor> = lines
            .filterNot { it.isBlank() || it.startsWith('#') }
            .mapNotNull { line ->
                val columns = line.split('\t')
                val speciesId = columns.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val values = columns.getOrNull(2)?.split(',')?.mapNotNull(String::toFloatOrNull)?.toFloatArray()
                    ?: return@mapNotNull null
                if (values.size != TeamSelectSpriteDescriptorMeasurer.VALUE_COUNT) return@mapNotNull null
                TeamSelectSpriteDescriptor(speciesId, columns.getOrNull(1).orEmpty(), values)
            }
            .toList()

        fun matchDescriptor(
            query: FloatArray,
            catalogue: List<TeamSelectSpriteDescriptor>,
        ): TeamSelectSpriteMatch? {
            if (query.size != TeamSelectSpriteDescriptorMeasurer.VALUE_COUNT || catalogue.isEmpty()) return null
            val ranked = catalogue.asSequence()
                .map { candidate -> candidate to score(query, candidate.values) }
                .sortedBy { it.second }
                .toList()
            val best = ranked.first()
            val runnerUp = ranked.firstOrNull { it.first.speciesId != best.first.speciesId } ?: return null
            val margin = runnerUp.second - best.second
            if (best.second > MAX_ACCEPTED_SCORE || margin < MIN_DISTINCT_SPECIES_MARGIN) return null
            val confidence = (
                0.84f +
                    (margin.coerceAtMost(0.45f) / 0.45f) * 0.08f +
                    ((MAX_ACCEPTED_SCORE - best.second).coerceAtLeast(0f) / MAX_ACCEPTED_SCORE) * 0.06f
                ).coerceIn(0.84f, 0.98f)
            return TeamSelectSpriteMatch(
                speciesId = best.first.speciesId,
                variant = best.first.variant,
                score = best.second,
                distinctSpeciesMargin = margin,
                confidence = confidence,
            )
        }

        private fun score(left: FloatArray, right: FloatArray): Float {
            var histogram = 0f
            for (index in 0 until 40) {
                val difference = left[index] - right[index]
                histogram += difference * difference / (left[index] + right[index] + 0.00001f)
            }
            var shape = 0f
            for (index in 40 until TeamSelectSpriteDescriptorMeasurer.VALUE_COUNT) {
                shape += abs(left[index] - right[index])
            }
            return histogram + shape * 0.25f
        }
    }
}

internal object TeamSelectSpriteDescriptorMeasurer {
    const val VALUE_COUNT = 45
    private const val SIZE = 64

    fun measure(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        return try {
            measure(SIZE, SIZE, scaled::getPixel)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    internal fun measure(width: Int, height: Int, pixelAt: (Int, Int) -> Int): FloatArray {
        val hue = IntArray(24)
        val saturation = IntArray(8)
        val value = IntArray(8)
        var count = 0
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        var xTotal = 0L
        var yTotal = 0L
        val hsv = FloatArray(3)

        for (y in 6 until min(height, SIZE)) {
            for (x in 3 until min(width, 61)) {
                val color = pixelAt(x, y)
                if (Color.alpha(color) < 80) continue
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val highest = max(red, max(green, blue))
                val lowest = min(red, min(green, blue))
                if (highest > 238 && highest - lowest < 18) continue
                Color.RGBToHSV(red, green, blue, hsv)
                val normalizedHue = hsv[0] / 360f
                val normalizedSaturation = hsv[1]
                val normalizedValue = hsv[2]
                if ((normalizedSaturation < 0.07f && normalizedValue > 0.91f) ||
                    (normalizedValue > 0.88f && normalizedSaturation < 0.68f)
                ) continue
                hue[min(23, (normalizedHue * 24).toInt())]++
                saturation[min(7, (normalizedSaturation * 8).toInt())]++
                value[min(7, (normalizedValue * 8).toInt())]++
                count++
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                xTotal += x
                yTotal += y
            }
        }

        val result = FloatArray(VALUE_COUNT)
        fun appendNormalized(offset: Int, values: IntArray) {
            val total = values.sum().coerceAtLeast(1).toFloat()
            values.forEachIndexed { index, amount -> result[offset + index] = amount / total }
        }
        appendNormalized(0, hue)
        appendNormalized(24, saturation)
        appendNormalized(32, value)
        result[40] = count / (58f * 58f)
        if (count > 0) {
            result[41] = (maxX - minX + 1) / SIZE.toFloat()
            result[42] = (maxY - minY + 1) / SIZE.toFloat()
            result[43] = xTotal / count.toFloat() / SIZE
            result[44] = yTotal / count.toFloat() / SIZE
        }
        return result
    }
}
