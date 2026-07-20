package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * A foundational container for captured evidence from a specific screen region.
 * 
 * This represents the "v0" observation layer: isolated visual evidence before any
 * text extraction or recognition has been performed.
 * 
 * @property regionId The unique identifier of the screen region being captured.
 * @property crop The raw [Bitmap] extracted from the screen capture.
 */
data class CaptureObservation(
    val regionId: String,
    val crop: Bitmap
)
