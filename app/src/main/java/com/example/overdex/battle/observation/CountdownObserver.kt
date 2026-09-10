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
 * Production observer responsible for observing raw OCR text in the countdown region
 * and submitting raw testimony to the canonical Timeline.
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

                        if (value != null) {
                            Log.d("CountdownObserver", "OCR text observed: \"$value\" (conf=null)")

                            // Submit every successful OCR reading as RawTestimony
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = RawTestimony(value),
                                timestamp = timestamp,
                                confidence = recognitionResult.confidence
                            )
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
