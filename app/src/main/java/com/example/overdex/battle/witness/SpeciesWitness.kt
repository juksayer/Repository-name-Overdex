package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
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
class SpeciesWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("SPECIES_WITNESS", ObserverSource.SCREEN_CAPTURE),

    override val name: String = "Species Witness"
) : Observer {

    private var scope: CoroutineScope? = null

    /**
     * Represents a discrete perception of a countdown element.
     */
    data class SpeciesTestimony(val value: String, val timestamp: Long)

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("COUNTDOWN", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        newScope.launch {
            Log.d("COUNTDOWN", "waiting for frames")
            input.supply { bitmap ->
                Log.d("COUNTDOWN", "bitmap received")
                match.incrementFrameCount()
                Log.d("COUNTDOWN", "Calibration: ${calibration.isCalibrated()}")
                if (calibration.isCalibrated()) {
                    val cropped = cropCountdown(bitmap)
                    if (cropped != null) {
                        val recognitionResult = CountdownRecognizer.recognize(cropped)

                        val value = recognitionResult.value
                        if (recognitionResult.confidence >= 1.0f && value != null) {
                            val testimony = SpeciesTestimony(value, System.currentTimeMillis())
                            Log.d("CountdownObserver", "CountdownWitness(value=$value)")

                            // Publish the witness to the rest of the instrument
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

    private fun cropCountdown(bitmap: Bitmap): Bitmap? {
        val region = calibration.countdownRegion
        val width = bitmap.width
        val height = bitmap.height

        val left = (region.x * width).toInt().coerceIn(0, width - 1)
        val top = (region.y * height).toInt().coerceIn(0, height - 1)
        val w = (region.width * width).toInt().coerceAtMost(width - left)
        val h = (region.height * height).toInt().coerceAtMost(height - top)

        if (w < 32 || h < 32) {
            Log.w("CountdownObserver", "Crop dimensions too small for ML Kit: ${w}x${h}")
            return null
        }

        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            null
        }
    }
}

