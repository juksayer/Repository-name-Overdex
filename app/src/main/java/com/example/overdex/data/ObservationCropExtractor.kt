package com.example.overdex.data

import android.graphics.Bitmap
import com.example.overdex.model.CaptureTemplate

/**
 * Extracts individual bitmaps for each region defined in a CaptureTemplate.
 */
object ObservationCropExtractor {
    
    /**
     * Extracts crops from a source bitmap based on the provided template.
     * @return A map of region IDs to their corresponding cropped Bitmaps.
     */
    fun extract(source: Bitmap, template: CaptureTemplate): Map<String, Bitmap> {
        val crops = mutableMapOf<String, Bitmap>()
        val width = source.width
        val height = source.height

        template.regions.forEach { region ->
            // Convert normalized coordinates to absolute pixels
            val left = (region.x * width).toInt().coerceIn(0, width - 1)
            val top = (region.y * height).toInt().coerceIn(0, height - 1)
            val cropWidth = (region.width * width).toInt().coerceAtMost(width - left)
            val cropHeight = (region.height * height).toInt().coerceAtMost(height - top)

            if (cropWidth > 0 && cropHeight > 0) {
                try {
                    val cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
                    crops[region.id] = cropped
                } catch (e: Exception) {
                    android.util.Log.e("CROP_EXTRACTOR", "Failed to crop ${region.id}", e)
                }
            }
        }
        return crops
    }
}
