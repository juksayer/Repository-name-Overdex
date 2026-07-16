package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * An architectural boundary representing any source capable of supplying 
 * visual observations to an [ObservationSession].
 */
interface ObservationInput {
    /**
     * Identifies the origin of this input source.
     */
    val source: SessionSource

    /**
     * Drives the supply of visual evidence to a consumer.
     * This callback-driven approach supports single-frame, multi-frame, 
     * and future live observation sources.
     */
    suspend fun supply(onVisualData: suspend (Bitmap) -> Unit)
}
