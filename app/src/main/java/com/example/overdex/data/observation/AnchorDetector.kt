package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.AnchorObservation

/**
 * Interface for components capable of detecting visual anchors in a bitmap.
 */
interface AnchorDetector {
    /**
     * Scans a bitmap for visual anchors that can be used for spatial coordinate refinement.
     * 
     * @param bitmap The image to scan.
     * @param stage The semantic stage of observation (for debugging/logging).
     * @return A list of detected [AnchorObservation]s.
     */
    suspend fun detectAnchors(bitmap: Bitmap, stage: String = "UNKNOWN"): List<AnchorObservation>
}
