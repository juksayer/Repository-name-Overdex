package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.overdex.R
import com.example.overdex.model.PokemonType

/** Matches captured GO badges against the retained official reference sheet. */
object PokemonGoTypeIconMatcher {
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
            val ordered = listOf(
                PokemonType.FAIRY, PokemonType.PSYCHIC, PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.FIRE, PokemonType.STEEL,
                PokemonType.WATER, PokemonType.FLYING, PokemonType.GHOST, PokemonType.GRASS, PokemonType.BUG, PokemonType.GROUND,
                PokemonType.ICE, PokemonType.DARK, PokemonType.DRAGON, PokemonType.NORMAL, PokemonType.ELECTRIC, PokemonType.POISON,
            )
            matcher = TypeIconPixelMatcher(ordered.mapIndexed { index, type ->
                val centerX = 150 + index % 6 * 180
                val centerY = 150 + index / 6 * 180
                val pixels = IntArray(130 * 130)
                sheet.getPixels(pixels, 0, 130, centerX - 65, centerY - 65, 130, 130)
                TypeIconReference(type.ordinal, TypeIconPixels(130, 130, pixels), TypeIconCircle(64.5, 64.5, 65.0))
            })
        } finally { sheet.recycle() }
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
