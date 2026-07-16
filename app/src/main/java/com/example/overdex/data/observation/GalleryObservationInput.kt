package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource

/**
 * A concrete implementation of [ObservationInput] that wraps a single [Bitmap]
 * typically obtained from the device gallery.
 */
class GalleryObservationInput(private val bitmap: Bitmap) : ObservationInput {
    override val source: SessionSource = SessionSource.SCREENSHOT

    override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
        onVisualData(bitmap)
    }
}
