package com.example.overdex.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MediaManager(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build()

    fun playSound(url: String) {
        if (url.isEmpty()) return
        
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.release()
    }
}
