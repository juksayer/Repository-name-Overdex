package com.example.overdex.model

import android.graphics.Bitmap

/**
 * Represents a raw bitmap observation extracted from a specific region of interest.
 * No interpretation or OCR has been performed at this stage.
 */
data class Observation(
    val region: ObservationRegion,
    val bitmap: Bitmap
)
