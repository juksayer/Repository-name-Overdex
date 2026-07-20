package com.example.overdex.model.observation

import android.graphics.Rect

/**
 * Represents a detected stable UI element that can be used for spatial anchoring.
 * 
 * @property type The kind of anchor detected.
 * @property bounds The screen coordinates of the detected anchor.
 * @property confidence Detection certainty from 0.0 to 1.0.
 */
data class AnchorObservation(
    val type: AnchorType,
    val bounds: Rect,
    val confidence: Float
)
