package com.example.overdex.data

/**
 * Abstraction for resolving Pokémon sprite locations.
 */
interface SpriteProvider {
    fun getSpriteUrl(id: Int): String
}

/**
 * Resolves sprites from the official PokeAPI GitHub repository.
 */
class GithubSpriteProvider : SpriteProvider {
    override fun getSpriteUrl(id: Int): String {
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
    }
}

/**
 * Resolves sprites from packaged assets.
 * Convention: assets/sprites/pokemon/{id}.png
 */
class LocalSpriteProvider : SpriteProvider {
    override fun getSpriteUrl(id: Int): String {
        return "file:///android_asset/sprites/pokemon/$id.png"
    }
}
