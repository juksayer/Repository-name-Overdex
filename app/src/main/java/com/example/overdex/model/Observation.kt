package com.example.overdex.model

import android.graphics.Bitmap

/**
 * Represents a raw bitmap observation extracted from a specific region of interest.
 * 
 * This is the "v0" layer of evidence: isolated visual data before any interpretation 
 * or OCR has been performed.
 * 
 * @property region The definition of the screen area this bitmap was extracted from.
 * @property bitmap The actual image data for the region.
 */
data class Observation(
    val region: ObservationRegion,
    val bitmap: Bitmap
)
