package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.BattleCalibration
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.CountdownRecognizer
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.data.observation.RecognitionObservationMapper
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    override fun start(match: Match) {
        if (scope != null) return

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        newScope.launch {
            input.supply { bitmap ->
                match.incrementFrameCount()

                if (calibration.isCalibrated()) {
                    val cropped = cropCountdown(bitmap)
                    if (cropped != null) {
                        val recognitionResult = CountdownRecognizer.recognize(cropped)
                        Log.d(
                            "CountdownObserver",
                            "Witnessed countdown: ${recognitionResult.value}"
                        )
                        
                        // We only submit if we have a confident recognition
                        if (recognitionResult.value != null) {
                            val observation = RecognitionObservationMapper.map(
                                regionId = "CountdownName",
                                result = recognitionResult,
                                source = ObservationSource.OCR
                            )
                            
                        if (observation != null) {
                            // TODO: Fix domain mismatch between model.observation and battle.observation
                            Log.d("CountdownObserver", "Observed: $observation")
                        }
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

        return if (w > 0 && h > 0) {
            try {
                Bitmap.createBitmap(bitmap, left, top, w, h)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

