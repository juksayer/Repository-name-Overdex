package com.example.overdex.data

import android.content.res.AssetManager
import android.util.Log

/**
 * Abstraction for resolving the visual asset (sprite) for a specific Pokémon.
 */
interface SpriteProvider {
    /**
     * Returns the URI or URL for the requested sprite variant.
     */
    fun getSpriteUrl(
        id: Int,
        isShiny: Boolean = false,
        isShadow: Boolean = false,
        isPurified: Boolean = false
    ): String

    /** Returns true if the sprite for the given species is available in this provider. */
    fun exists(id: Int, isShiny: Boolean = false): Boolean
}

/**
 * Resolves sprites from the official PokeAPI GitHub repository.
 */
class GithubSpriteProvider : SpriteProvider {
    override fun getSpriteUrl(
        id: Int,
        isShiny: Boolean,
        isShadow: Boolean,
        isPurified: Boolean
    ): String {
        if (id <= 0) return getPlaceholderUrl()
        val path = if (isShiny) "shiny/" else ""
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$path$id.png"
    }

    override fun exists(id: Int, isShiny: Boolean): Boolean = id > 0

    private fun getPlaceholderUrl(): String = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png"
}

/**
 * Resolves sprites from packaged assets.
 * Convention: assets/sprites/pokemon/{id}.png
 */
class LocalSpriteProvider(private val assetManager: AssetManager) : SpriteProvider {
    override fun getSpriteUrl(
        id: Int,
        isShiny: Boolean,
        isShadow: Boolean,
        isPurified: Boolean
    ): String {
        if (id <= 0 || !exists(id, isShiny)) {
            if (id > 0) Log.w("SpriteProvider", "Missing local sprite for ID: $id (shiny=$isShiny)")
            return getPlaceholderUrl()
        }
        val path = if (isShiny) "shiny/" else "" // Future: support local shiny folder
        return "file:///android_asset/sprites/pokemon/$id.png"
    }

    override fun exists(id: Int, isShiny: Boolean): Boolean {
        if (id <= 0) return false
        return try {
            assetManager.open("sprites/pokemon/$id.png").use { }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getPlaceholderUrl(): String = "file:///android_asset/sprites/items/poke-ball.png"
}

/**
 * Automatically prefers the primary provider if the sprite exists there,
 * otherwise falls back to the secondary provider.
 */
class FallbackSpriteProvider(
    private val primary: SpriteProvider,
    private val secondary: SpriteProvider
) : SpriteProvider {
    override fun getSpriteUrl(
        id: Int,
        isShiny: Boolean,
        isShadow: Boolean,
        isPurified: Boolean
    ): String {
        return if (primary.exists(id, isShiny)) {
            primary.getSpriteUrl(id, isShiny, isShadow, isPurified)
        } else {
            secondary.getSpriteUrl(id, isShiny, isShadow, isPurified)
        }
    }

    override fun exists(id: Int, isShiny: Boolean): Boolean {
        return primary.exists(id, isShiny) || secondary.exists(id, isShiny)
    }
}
