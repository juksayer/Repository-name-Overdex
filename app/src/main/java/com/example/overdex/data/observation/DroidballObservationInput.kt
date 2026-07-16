package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource

/**
 * A streaming implementation of [ObservationInput] representing a live capture
 * session via Droidball.
 *
 * This implementation is currently a skeleton and does not supply any visual data.
 */
class DroidballObservationInput : ObservationInput {
    override val source: SessionSource = SessionSource.LIVE_CAPTURE

    override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
        // Architecture: Droidball observes continuously.
        // Implementation pending: MediaProjection and frame distribution.
    }
}
