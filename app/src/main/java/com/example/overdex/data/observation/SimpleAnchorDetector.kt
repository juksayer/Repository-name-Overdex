package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.overdex.model.observation.AnchorObservation
import com.example.overdex.model.observation.AnchorType
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.debug.observatory.AnchorDetectedPayload
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.RectData

/**
 * Initial implementation of an anchor detector.
 * 
 * Locates move/type icons on the left side of move rows using a deterministic
 * bright-blob detection algorithm. This provides the spatial foundation for
 * refined coordinate calculations.
 */
object SimpleAnchorDetector : AnchorDetector {

    /**
     * Scans the bitmap for stable visual anchors.
     * 
     * @param bitmap The full capture to scan.
     * @param stage The current observation stage for recording.
     * @return A list of [AnchorObservation]s representing detected UI elements.
     */
    override suspend fun detectAnchors(bitmap: Bitmap, stage: String): List<AnchorObservation> {
        val anchors = mutableListOf<AnchorObservation>()
        val width = bitmap.width
        val height = bitmap.height

        // Pokémon GO summary screens typically have move rows in the bottom half.
        val searchMinY = (height * 0.4f).toInt()
        val searchMaxY = (height * 0.95f).toInt()
        val searchMinX = (width * 0.02f).toInt()
        val searchMaxX = (width * 0.30f).toInt()

        // scanX is placed where the colored badge or white glyph of the icon is likely to be.
        val scanX = (width * 0.10f).toInt().coerceIn(0, width - 1)

        var y = searchMinY
        while (y < searchMaxY) {
            val pixel = bitmap.getPixel(scanX, y)
            if (isBright(pixel)) {
                // Potential icon hit, find its full bounds
                val bounds = findBlobBounds(bitmap, scanX, y, searchMinX, searchMaxX, searchMinY, searchMaxY)

                if (isValidMoveIcon(bounds, width)) {
                    val anchor = AnchorObservation(
                        type = AnchorType.MoveIcon,
                        bounds = bounds,
                        confidence = calculateConfidence(bounds, width)
                    )
                    anchors.add(anchor)

                    // Observatory Evidence: Record the detected anchor
                    ObservationRecorder.record(
                        EvidenceSourceType.VISION,
                        AnchorDetectedPayload(
                            anchorType = anchor.type.name,
                            bounds = RectData(bounds.left, bounds.top, bounds.right, bounds.bottom),
                            confidence = anchor.confidence,
                            observationStage = stage
                        )
                    )

                    // Skip ahead past the detected icon to find the next one
                    y = bounds.bottom + (height * 0.01f).toInt().coerceAtLeast(1)
                    continue
                }
            }
            // Vertical scan step (0.5% increments for efficiency)
            y += (height * 0.005f).toInt().coerceAtLeast(1)
        }

        return anchors
    }

    private fun isBright(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Icons are significantly brighter than the dark move-row background (< 60).
        return (r + g + b) / 3 > 100
    }

    private fun findBlobBounds(bitmap: Bitmap, x: Int, y: Int, minX: Int, maxX: Int, minY: Int, maxY: Int): Rect {
        var left = x
        var right = x
        var top = y
        var bottom = y

        // Expand vertically
        while (top > minY && isBright(bitmap.getPixel(x, top - 1))) top--
        while (bottom < maxY - 1 && isBright(bitmap.getPixel(x, bottom + 1))) bottom++

        // Expand horizontally from the vertical center
        val centerY = (top + bottom) / 2
        while (left > minX && isBright(bitmap.getPixel(left - 1, centerY))) left--
        while (right < maxX - 1 && isBright(bitmap.getPixel(right + 1, centerY))) right++

        return Rect(left, top, right, bottom)
    }

    private fun isValidMoveIcon(bounds: Rect, screenWidth: Int): Boolean {
        val w = bounds.width()
        val h = bounds.height()

        // Icons in Pokemon GO are usually between 4% and 16% of screen width.
        val minSize = screenWidth * 0.04f
        val maxSize = screenWidth * 0.16f

        if (w < minSize || h < minSize) return false
        if (w > maxSize || h > maxSize) return false

        // Move icons are generally circular/square badges.
        val aspectRatio = w.toFloat() / h.toFloat()
        return aspectRatio in 0.5f..2.0f
    }

    private fun calculateConfidence(bounds: Rect, screenWidth: Int): Float {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val aspectRatio = w / h

        var confidence = 0.95f

        // Adjust confidence based on aspect ratio (ideal is 1.0)
        val ratioPenalty = Math.abs(1.0f - aspectRatio).coerceAtMost(0.5f)
        confidence -= ratioPenalty * 0.3f

        // Adjust based on typical icon size (~10% of screen width)
        val typicalSize = screenWidth * 0.10f
        val sizePenalty = (Math.abs(w - typicalSize) / typicalSize).coerceAtMost(0.5f)
        confidence -= sizePenalty * 0.2f

        return confidence.coerceIn(0.7f, 1.0f)
    }
}
