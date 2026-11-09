package com.example.overdex.model.observation

import android.graphics.Bitmap

/**
 * An architectural boundary representing any source capable of supplying 
 * visual evidence to an [ObservationSession].
 */
interface ObservationInput {
    /**
     * Identifies the origin of this input source.
     */
    val source: SessionSource

    /**
     * True only for multicast feeds whose collectors each receive their own
     * frames. A narrow terminal reader may then run independently of geometry
     * acquisition. Channel-backed or one-shot inputs keep one collector.
     */
    val supportsIndependentFrameCollectors: Boolean get() = false

    /** Latest accepted live-feed progress, even when a slow reader missed its frames. */
    fun latestVisualPublication(): VisualFramePublication? = null

    /**
     * Supplies visual evidence to a consumer.
     * 
     * This callback-driven approach supports single-frame (screenshots), 
     * multi-frame (stitched images), and real-time live capture feeds.
     * 
     * @param onVisualData A suspendable callback invoked with each [Bitmap] frame.
     */
    suspend fun supply(onVisualData: suspend (Bitmap) -> Unit)

    /**
     * Supplies visual frames with their capture receipt times. Existing inputs
     * retain bitmap compatibility while live capture can override this with its
     * source receipt times and independent publication metadata.
     */
    suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
        supply { bitmap ->
            onFrame(
                CapturedVisualFrame(
                    bitmap = bitmap,
                    capturedAtWallTimeMillis = System.currentTimeMillis(),
                    capturedAtMonotonicTimeNanos = System.nanoTime()
                )
            )
        }
    }
}
