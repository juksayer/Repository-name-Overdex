package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource

import com.example.overdex.battle.observation.DroidballService
import kotlinx.coroutines.flow.collect

/**
 * A streaming implementation of [ObservationInput] representing a live capture
 * session via the Droidball service.
 */
class DroidballObservationInput : ObservationInput {
    override val source: SessionSource = SessionSource.LIVE_CAPTURE

    /**
     * Supplies frames from the live Droidball capture stream.
     */
    override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
        DroidballService.frames.collect { bitmap ->
            onVisualData(bitmap)
        }
    }
}
