package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource

/**
 * A streaming implementation of [ObservationInput] representing a live capture
 * session via the Droidball service.
 *
 * This implementation is currently a skeleton and acts as a placeholder for
 * future MediaProjection-based frame supply.
 */
class DroidballObservationInput : ObservationInput {
    override val source: SessionSource = SessionSource.LIVE_CAPTURE

    /**
     * Placeholder for live stream frame supply. Currently does nothing.
     */
    override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
        // Architecture: Droidball observes continuously.
        // Implementation pending: MediaProjection and frame distribution.
    }
}
