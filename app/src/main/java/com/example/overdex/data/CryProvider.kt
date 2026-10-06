package com.example.overdex.data

import android.content.res.AssetManager

/** Resolves Pokémon cries without requiring a network connection. */
interface CryProvider {
    fun getCryUrl(id: Int): String
    fun exists(id: Int): Boolean
}

/**
 * Every National Pokédex species from 1 through 1025 is packaged as an Ogg asset.
 * Battle recognition keeps its separate WAV reference catalogue because it needs
 * stable, hash-verified PCM input; these Ogg files are the lightweight playback set.
 */
class LocalCryProvider(private val assetManager: AssetManager) : CryProvider {
    override fun getCryUrl(id: Int): String =
        if (exists(id)) "file:///android_asset/cries/pokemon/latest/$id.ogg" else ""

    override fun exists(id: Int): Boolean {
        if (id <= 0) return false
        return try {
            assetManager.open("cries/pokemon/latest/$id.ogg").use { }
            true
        } catch (_: Exception) {
            false
        }
    }
}
