package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Match-scoped Witness responsible for producing testimony regarding
 * the opponent species phenomenon.
 */
class SpeciesWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("SPECIES_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Species Witness",
    private val recognize: suspend (Bitmap) -> RecognitionResult<String> = SpeciesNameRecognizer::recognize,
    private val crop: (Bitmap, com.example.overdex.model.AnchorRegion) -> Bitmap? = { bitmap, region ->
        val width = bitmap.width
        val height = bitmap.height

        val left = (region.x * width).toInt().coerceIn(0, width - 1)
        val top = (region.y * height).toInt().coerceIn(0, height - 1)
        val w = (region.width * width).toInt().coerceAtMost(width - left)
        val h = (region.height * height).toInt().coerceAtMost(height - top)

        if (w < 32 || h < 32) {
            null
        } else {
            try {
                Bitmap.createBitmap(bitmap, left, top, w, h)
            } catch (e: Exception) {
                null
            }
        }
    }
) : Observer {

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("SpeciesWitness", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            Log.d("SpeciesWitness", "waiting for frames")
            input.supply { bitmap ->
                Log.d("SpeciesWitness", "bitmap received")
                match.incrementFrameCount()
                
                val timestamp = System.currentTimeMillis()
                match.custody.submitInputAvailability(sourceId, true, timestamp)

                if (calibration.isCalibrated()) {
                    val cropped = crop(bitmap, calibration.enemyNameRegion)
                    if (cropped != null) {
                        val result = recognize(cropped)
                        val value = result.value

                        if (result.confidence >= 1.0f && value != null) {
                            Log.d("SpeciesWitness", "Species recognized: $value")

                            // 1. Reality Handoff (Neutral Testimony)
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = RawTestimony(value),
                                timestamp = timestamp,
                                confidence = result.confidence
                            )

                            // 2. Presentation Signal (Existing behavior)
                            DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(value))
                        } else if (value != null) {
                            Log.d("SpeciesWitness", "Normalized OCR string: $value")
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
}

