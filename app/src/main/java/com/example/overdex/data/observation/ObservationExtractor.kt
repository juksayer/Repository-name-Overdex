package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.Observation
import com.example.overdex.model.ObservationRegionState

/**
 * Engine responsible for extracting raw bitmap data from calibrated observation regions.
 *
 * This is a pure producer that separates image extraction from downstream interpretation
 * (OCR, LCD, etc.), following the "One producer, many consumers" architecture.
 */
object ObservationExtractor {

    /**
     * Extracts bitmap observations for a set of region states.
     *
     * @param source The full source display bitmap.
     * @param states The current runtime states of the regions to extract.
     * @return A collection of cropped Observations.
     */
    fun extract(source: Bitmap, states: List<ObservationRegionState>): List<Observation> {
        val width = source.width
        val height = source.height

        return states.map { state ->
            // Calculate pixel coordinates from normalized values
            val left = (state.currentX * width).toInt().coerceIn(0, width - 1)
            val top = (state.currentY * height).toInt().coerceIn(0, height - 1)
            
            // Ensure width/height don't exceed source bounds from calculated start
            val cropWidth = (state.width * width).toInt().coerceAtMost(width - left)
            val cropHeight = (state.height * height).toInt().coerceAtMost(height - top)

            val bitmap = if (cropWidth > 0 && cropHeight > 0) {
                try {
                    Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
                } catch (e: Exception) {
                    android.util.Log.e("OBS_EXTRACTOR", "Extraction failed for ${state.name}", e)
                    createFallbackBitmap()
                }
            } else {
                // Fallback for zero-sized placeholders
                createFallbackBitmap()
            }

            Observation(state.region, bitmap)
        }
    }

    private fun createFallbackBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
