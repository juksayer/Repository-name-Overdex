package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * One visual frame and its source receipt times. The bitmap remains transient;
 * captured times travel with the frame to its Witness. Live-feed publication
 * progress is separate because conversion and capture-side work take time.
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
    /** A retained game badge is our own image; these pixels cannot become new testimony. */
    val retainedBadgeRegion: com.example.overdex.model.AnchorRegion? = null,
    /** Accepted live-feed publication; absent for screenshot or older/custom inputs. */
    val publication: VisualFramePublication? = null,
)
