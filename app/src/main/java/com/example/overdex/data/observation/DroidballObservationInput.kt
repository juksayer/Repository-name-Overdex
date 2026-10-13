package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.SessionSource

import com.example.overdex.battle.observation.DroidballService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.yield

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
        DroidballService.frames.collectLiveSamples { frame ->
            onVisualData(frame.bitmap)
        }
    }

    override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
        DroidballService.frames.collectLiveSamples(onFrame)
    }
}

internal suspend fun <T> Flow<T>.collectLiveSamples(onSample: suspend (T) -> Unit) {
    collect { sample ->
        onSample(sample)
        // A hot SharedFlow can immediately supply another frame after synchronous
        // PNG work. On a bounded dispatcher that loop otherwise keeps its thread
        // indefinitely, starving countdown, announcements and resource captures.
        yield()
    }
}
