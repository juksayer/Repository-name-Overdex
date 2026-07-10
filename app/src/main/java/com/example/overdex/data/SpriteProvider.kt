package com.example.overdex.data

import android.content.res.AssetManager

/**
 * Abstraction for resolving Pokémon sprite locations.
 */
interface SpriteProvider {
    fun getSpriteUrl(id: Int): String
    fun exists(id: Int): Boolean
}

/**
 * Resolves sprites from the official PokeAPI GitHub repository.
 */
class GithubSpriteProvider : SpriteProvider {
    override fun getSpriteUrl(id: Int): String {
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
    }

    override fun exists(id: Int): Boolean = true // Assume remote sprites always exist
}

/**
 * Resolves sprites from packaged assets.
 * Convention: assets/sprites/pokemon/{id}.png
 */
class LocalSpriteProvider(private val assetManager: AssetManager) : SpriteProvider {
    override fun getSpriteUrl(id: Int): String {
        return "file:///android_asset/sprites/pokemon/$id.png"
    }

    override fun exists(id: Int): Boolean {
        return try {
            assetManager.open("sprites/pokemon/$id.png").use { }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Automatically prefers the primary provider if the sprite exists there,
 * otherwise falls back to the secondary provider.
 */
class FallbackSpriteProvider(
    private val primary: SpriteProvider,
    private val secondary: SpriteProvider
) : SpriteProvider {
    override fun getSpriteUrl(id: Int): String {
        return if (primary.exists(id)) {
            primary.getSpriteUrl(id)
        } else {
            secondary.getSpriteUrl(id)
        }
    }

    override fun exists(id: Int): Boolean {
        return primary.exists(id) || secondary.exists(id)
    }
}
