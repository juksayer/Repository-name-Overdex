package com.example.overdex.model.observation

import android.graphics.Rect

/**
 * Represents a detected stable UI element that can be used for spatial anchoring.
 */
data class AnchorObservation(
    val type: AnchorType,
    val bounds: Rect,
    val confidence: Float
)
