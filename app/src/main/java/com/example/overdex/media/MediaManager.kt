package com.example.overdex.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MediaManager(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build()

    fun warmUp(url: String) {
        if (url.isEmpty()) return
        
        // Idempotency: Do nothing if the URL is already loaded or preparing.
        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() == url) {
            return
        }

        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        
        // Passive Warming: Ensure the player doesn't start automatically.
        exoPlayer.playWhenReady = false
        exoPlayer.prepare()
    }

    fun playSound(url: String) {
        if (url.isEmpty()) return
        
        // If the URL is different from what's loaded, load it now.
        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != url) {
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
        
        // Reliable Playback: Always restart from the beginning and play.
        exoPlayer.seekTo(0)
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.release()
    }
}
