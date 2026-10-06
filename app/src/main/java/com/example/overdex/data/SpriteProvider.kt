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
 * Resolves sprites from packaged assets.
 * Conventions:
 * - assets/sprites/pokemon/{id}.png
 * - assets/sprites/pokemon/shiny/{id}.png
 * - assets/sprites/pokemon/back/{id}.png
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
        val path = if (isShiny) "shiny/" else ""
        return "file:///android_asset/sprites/pokemon/$path$id.png"
    }

    override fun exists(id: Int, isShiny: Boolean): Boolean {
        if (id <= 0) return false
        return try {
            val path = if (isShiny) "shiny/" else ""
            assetManager.open("sprites/pokemon/$path$id.png").use { }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Player-side replay art is a distinct rear view, never a mirrored opponent sprite.
     * Some forms may not have a bundled rear asset yet; those deliberately fall back to
     * the ordinary local sprite instead of leaving the combatant invisible.
     */
    fun getBackSpriteUrl(id: Int): String {
        if (id > 0 && backSpriteExists(id)) {
            return "file:///android_asset/sprites/pokemon/back/$id.png"
        }
        return getSpriteUrl(id)
    }

    private fun backSpriteExists(id: Int): Boolean = try {
        assetManager.open("sprites/pokemon/back/$id.png").use { }
        true
    } catch (_: Exception) {
        false
    }

    private fun getPlaceholderUrl(): String = ""
}
