package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.AnchorObservation

/**
 * Initial implementation of an anchor detector.
 * Currently serves as a placeholder/skeleton for future computer vision logic.
 */
object SimpleAnchorDetector : AnchorDetector {
    
    override suspend fun detectAnchors(bitmap: Bitmap): List<AnchorObservation> {
        // Brick #121: Establish the capability.
        // Return empty list for now to ensure no behavior changes until future bricks.
        return emptyList()
    }
}
