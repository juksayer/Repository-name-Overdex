package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.overdex.R
import com.example.overdex.model.PokemonType

/**
 * Matches one isolated Pokémon GO type badge against the user-supplied official
 * reference sheet. It compares only the bright glyph silhouette, never an
 * Overdex presentation vector or the badge colour.
 */
object PokemonGoTypeIconMatcher {
    private const val size = 32
    // Established against the first replayed live-battle badges. Broaden only
    // after labeled replay samples demonstrate that it remains discriminating.
    private const val threshold = 0.40f
    private data class Template(val glyph: BooleanArray, val color: Int)
    private var templates: Map<PokemonType, Template>? = null

    data class Result(val type: PokemonType?, val bestCandidate: PokemonType?, val similarity: Float, val colorSimilarity: Float)

    fun initialize(context: Context) {
        if (templates != null) return
        val sheet = BitmapFactory.decodeResource(context.resources, R.drawable.pokemon_go_type_icon_reference)
            ?: return
        try {
            val ordered = listOf(
                PokemonType.FAIRY, PokemonType.PSYCHIC, PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.FIRE, PokemonType.STEEL,
                PokemonType.WATER, PokemonType.FLYING, PokemonType.GHOST, PokemonType.GRASS, PokemonType.BUG, PokemonType.GROUND,
                PokemonType.ICE, PokemonType.DARK, PokemonType.DRAGON, PokemonType.NORMAL, PokemonType.ELECTRIC, PokemonType.POISON
            )
            templates = ordered.mapIndexed { index, type ->
                val column = index % 6
                val row = index / 6
                val centerX = 150 + column * 180
                val centerY = 150 + row * 180
                val tile = Bitmap.createBitmap(sheet, centerX - 65, centerY - 65, 130, 130)
                try { type to Template(normalize(tile), representativeColor(tile)) } finally { tile.recycle() }
            }.toMap()
        } finally { sheet.recycle() }
    }

    fun matchIsolatedBadge(bitmap: Bitmap): Result {
        val candidate = normalize(bitmap)
        val options = templates ?: return Result(null, null, 0f, 0f)
        val observedColor = representativeColor(bitmap)
        val best = options.maxByOrNull { (_, template) -> similarity(candidate, template.glyph) } ?: return Result(null, null, 0f, 0f)
        val glyphScore = similarity(candidate, best.value.glyph)
        val colorScore = colorSimilarity(observedColor, best.value.color)
        val combined = glyphScore * 0.7f + colorScore * 0.3f
        return Result(best.key.takeIf { combined >= threshold }, best.key, combined, colorScore)
    }

    /** Finds the one or two bright-glyph badges in a calibrated active-type crop. */
    fun matchCrop(bitmap: Bitmap): List<Result> {
        val occupied = BooleanArray(bitmap.width)
        for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) >= 225 && Color.green(pixel) >= 225 && Color.blue(pixel) >= 225) occupied[x] = true
        }
        val spans = mutableListOf<IntRange>()
        var start = -1
        for (x in 0 until bitmap.width) {
            if (occupied[x] && start < 0) start = x
            if ((!occupied[x] || x == bitmap.width - 1) && start >= 0) {
                spans += start..if (occupied[x] && x == bitmap.width - 1) x else x - 1
                start = -1
            }
        }
        val merged = spans.fold(mutableListOf<IntRange>()) { groups, span ->
            val previous = groups.lastOrNull()
            if (previous != null && span.first - previous.last <= 12) groups[groups.lastIndex] = previous.first..span.last
            else groups += span
            groups
        }.filter { it.last - it.first + 1 >= 6 }.take(2)

        return merged.mapNotNull { span ->
            val brightYs = buildList {
                for (x in span) for (y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.red(pixel) >= 225 && Color.green(pixel) >= 225 && Color.blue(pixel) >= 225) add(y)
                }
            }
            if (brightYs.isEmpty()) return@mapNotNull null
            val centerX = (span.first + span.last) / 2
            val centerY = (brightYs.min() + brightYs.max()) / 2
            // Type strips are calibrated around one badge row. Use that row's
            // height for badge scale; glyph width varies too much by type.
            val side = (bitmap.height * 0.8f).toInt().coerceIn(32, minOf(bitmap.width, bitmap.height))
            val left = (centerX - side / 2).coerceIn(0, bitmap.width - side)
            val top = (centerY - side / 2).coerceIn(0, bitmap.height - side)
            val badge = Bitmap.createBitmap(bitmap, left, top, side, side)
            try { matchIsolatedBadge(badge) } finally { badge.recycle() }
        }
    }

    private fun normalize(bitmap: Bitmap): BooleanArray {
        // Both inputs are isolated square badges. Preserve their geometry: a
        // glyph's position and proportions distinguish Dragon from Normal.
        return BooleanArray(size * size) { index ->
            val x = index % size; val y = index / size
            val sourceX = x * bitmap.width / size
            val sourceY = y * bitmap.height / size
            val pixel = bitmap.getPixel(sourceX.coerceAtMost(bitmap.width - 1), sourceY.coerceAtMost(bitmap.height - 1))
            Color.red(pixel) >= 225 && Color.green(pixel) >= 225 && Color.blue(pixel) >= 225
        }
    }

    private fun similarity(a: BooleanArray, b: BooleanArray): Float {
        val union = a.indices.count { a[it] || b[it] }
        return if (union == 0) 0f else a.indices.count { a[it] && b[it] }.toFloat() / union
    }

    private fun representativeColor(bitmap: Bitmap): Int {
        val pixels = mutableListOf<Int>()
        val centerX = (bitmap.width - 1) / 2f
        val centerY = (bitmap.height - 1) / 2f
        val radius = minOf(bitmap.width, bitmap.height) / 2f
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val normalizedDistance = kotlin.math.hypot(x - centerX, y - centerY) / radius
            if (normalizedDistance !in 0.28f..0.78f) continue
            val p = bitmap.getPixel(x, y)
            val max = maxOf(Color.red(p), Color.green(p), Color.blue(p)); val min = minOf(Color.red(p), Color.green(p), Color.blue(p))
            if (max - min >= 45 && max < 235) pixels += p
        }
        if (pixels.isEmpty()) return Color.BLACK
        return Color.rgb(pixels.map { Color.red(it) }.average().toInt(), pixels.map { Color.green(it) }.average().toInt(), pixels.map { Color.blue(it) }.average().toInt())
    }

    private fun colorSimilarity(a: Int, b: Int): Float {
        val distance = kotlin.math.sqrt(((Color.red(a)-Color.red(b)).toDouble().let { it*it } + (Color.green(a)-Color.green(b)).toDouble().let { it*it } + (Color.blue(a)-Color.blue(b)).toDouble().let { it*it }))
        return (1.0 - distance / 441.67).coerceIn(0.0, 1.0).toFloat()
    }
}
