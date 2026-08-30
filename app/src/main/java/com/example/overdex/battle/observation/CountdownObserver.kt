package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Production observer responsible for identifying the countdown numbers.
 *
 * This observer monitors the enemy name region of the screen and uses OCR
 * to recognize the Countdown numbers.
 */
class CountdownObserver(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("COUNTDOWN_OBSERVER", ObserverSource.SCREEN_CAPTURE),

    override val name: String = "Countdown Observer"
) : Observer {

    private var scope: CoroutineScope? = null

    /**
     * Represents a discrete perception of a countdown element.
     */
    data class CountdownWitness(val value: String, val timestamp: Long)

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("COUNTDOWN", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            Log.d("COUNTDOWN", "waiting for frames")
            input.supply { bitmap ->
                Log.d("COUNTDOWN", "bitmap received")
                match.incrementFrameCount()

                val timestamp = System.currentTimeMillis()
                match.custody.submitInputAvailability(sourceId, true, timestamp)

                Log.d("COUNTDOWN", "Calibration: ${calibration.isCalibrated()}")
                if (calibration.isCalibrated()) {
                    val cropped = cropSpecies(bitmap)
                    if (cropped != null) {

                        val recognitionResult = CountdownRecognizer.recognize(cropped)

                        val value = recognitionResult.value
                        if (recognitionResult.confidence >= 1.0f && value != null) {
                            val witness = CountdownWitness(value, timestamp)
                            Log.d("CountdownObserver", "CountdownWitness(value=$value)")

                            // 1. Reality Handoff (Neutral Testimony)
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = RawTestimony(value),
                                timestamp = witness.timestamp,
                                confidence = recognitionResult.confidence
                            )

                            // 2. Presentation Signal (Existing behavior)
                            DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(value))
                        } else if (value != null) {
                            Log.d("CountdownObserver", "Normalized OCR string: $value")
                        }
                    }
                }
            }
        }
    }
    override fun stop() {
        scope?.cancel("Observer stopped")
        scope = null
    }
    private fun cropSpecies(bitmap: Bitmap): Bitmap? {
        val region = calibration.countdownRegion
        val width = bitmap.width
        val height = bitmap.height

        val left = (region.x * width)
            .toInt()
            .coerceIn(0, width - 1)

        val top = (region.y * height)
            .toInt()
            .coerceIn(0, height - 1)

        val w = (region.width * width)
            .toInt()
            .coerceAtMost(width - left)

        val h = (region.height * height)
            .toInt()
            .coerceAtMost(height - top)

        if (w < 32 || h < 32) {
            Log.w(
                "CountdownObserver",
                "Crop dimensions too small for ML Kit: ${w}x${h}"
            )
            return null
        }

        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            null
        }
    }
}

