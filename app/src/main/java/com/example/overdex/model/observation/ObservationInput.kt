package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * An architectural boundary representing any source capable of supplying 
 * visual evidence to an [ObservationSession].
 */
interface ObservationInput {
    /**
     * Identifies the origin of this input source.
     */
    val source: SessionSource

    /**
     * Supplies visual evidence to a consumer.
     * 
     * This callback-driven approach supports single-frame (screenshots), 
     * multi-frame (stitched images), and real-time live capture feeds.
     * 
     * @param onVisualData A suspendable callback invoked with each [Bitmap] frame.
     */
    suspend fun supply(onVisualData: suspend (Bitmap) -> Unit)
}
