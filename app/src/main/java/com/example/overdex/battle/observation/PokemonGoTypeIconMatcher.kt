package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.overdex.R
import com.example.overdex.model.PokemonType

/** Matches captured GO badges against the retained official reference sheet. */
object PokemonGoTypeIconMatcher {
    @Volatile internal var qteReader: QteGlyphReader? = null
        private set
    @Volatile private var matcher: TypeIconPixelMatcher? = null

    data class Result(
        val type: PokemonType?, val bestCandidate: PokemonType?, val similarity: Float, val colorSimilarity: Float,
        val shapeSimilarity: Float = 0f, val runnerUpMargin: Float = 0f, val colorAvailable: Boolean = false,
    )

    @Synchronized fun initialize(context: Context) {
        if (matcher != null) return
        val sheet = BitmapFactory.decodeResource(context.resources, R.drawable.pokemon_go_type_icon_reference)
            ?: return
        try {
            val references = readReferences { left, top ->
                IntArray(130 * 130).also { sheet.getPixels(it, 0, 130, left, top, 130, 130) }
            }
            qteReader = QteGlyphReader(references, PokemonType.WATER.ordinal, PokemonType.ROCK.ordinal)
            matcher = TypeIconPixelMatcher(references)
        } finally { sheet.recycle() }
    }

    internal fun readReferences(readPixels: (left: Int, top: Int) -> IntArray): List<TypeIconReference> {
        val ordered = listOf(
            PokemonType.FAIRY, PokemonType.PSYCHIC, PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.FIRE, PokemonType.STEEL,
            PokemonType.WATER, PokemonType.FLYING, PokemonType.GHOST, PokemonType.GRASS, PokemonType.BUG, PokemonType.GROUND,
            PokemonType.ICE, PokemonType.DARK, PokemonType.DRAGON, PokemonType.NORMAL, PokemonType.ELECTRIC, PokemonType.POISON,
        )
        return ordered.mapIndexed { index, type ->
            val pixels = readPixels(85 + index % 6 * 180, 85 + index / 6 * 180)
            TypeIconReference(type.ordinal, TypeIconPixels(130, 130, pixels), TypeIconCircle(64.5, 64.5, 65.0))
        }
    }

    fun matchIsolatedBadge(bitmap: Bitmap): Result = matcher?.matchIsolatedBadge(bitmap.pixels())?.publicResult()
        ?: Result(null, null, 0f, 0f)

    /** Circle geometry is measured from the pixels, independently of strip margins. */
    fun matchCrop(bitmap: Bitmap): List<Result> = matcher?.matchCrop(bitmap.pixels())?.map { it.publicResult() }
        ?: emptyList()

    private fun Bitmap.pixels(): TypeIconPixels {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return TypeIconPixels(width, height, pixels)
    }

    private fun TypeIconPixelResult.publicResult() = Result(
        key?.let { PokemonType.entries[it] }, bestCandidate?.let { PokemonType.entries[it] }, similarity,
        colorSimilarity, shapeSimilarity, runnerUpMargin, colorAvailable,
    )
}
