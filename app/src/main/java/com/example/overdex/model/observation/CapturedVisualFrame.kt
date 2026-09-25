package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * One published visual frame and the instant it entered Overdex's evidence
 * pipeline. The bitmap remains transient; its captured times are evidence
 * metadata and travel with the frame to its Witness.
 */
data class CapturedVisualFrame(
    val bitmap: Bitmap,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long
)
