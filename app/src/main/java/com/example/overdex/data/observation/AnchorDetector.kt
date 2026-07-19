package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.AnchorObservation

/**
 * Interface for components capable of detecting visual anchors in a bitmap.
 */
interface AnchorDetector {
    suspend fun detectAnchors(bitmap: Bitmap, stage: String = "UNKNOWN"): List<AnchorObservation>
}
