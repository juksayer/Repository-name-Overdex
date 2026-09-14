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
     * retain bitmap compatibility while live capture can override this with the
     * timestamps assigned when the service publishes the frame.
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
