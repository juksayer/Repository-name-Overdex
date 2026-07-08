package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * A foundational container for captured evidence from a specific screen region.
 * This represents the "v0" observation layer: isolated visual evidence before interpretation.
 */
data class CaptureObservation(
    val regionId: String,
    val crop: Bitmap
)
