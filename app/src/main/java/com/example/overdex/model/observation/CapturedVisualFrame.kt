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
    val capturedAtMonotonicTimeNanos: Long,
    /** Full-display coordinate space used by Android's Pointer Location overlay. */
    val displayWidth: Int? = null,
    val displayHeight: Int? = null,
    /** Normalized captured-screen footprint of our visible presentation, never game identity evidence. */
    val overlayRegion: com.example.overdex.model.AnchorRegion? = null,
)
