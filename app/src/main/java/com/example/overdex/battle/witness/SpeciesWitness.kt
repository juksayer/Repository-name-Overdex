package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.observation.ObservationInput
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

    override val name: String = "Species Witness"
) : Observer {

    private var scope: CoroutineScope? = null

    /**
     * Represents a discrete perception of a Species element.
     */
    data class SpeciesTestimony(val value: String, val timestamp: Long)

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("SpeciesWitness", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        newScope.launch {
            Log.d("SpeciesWitness", "waiting for frames")
            input.supply { bitmap ->
                Log.d("SpeciesWitness", "bitmap received")
                match.incrementFrameCount()
                Log.d("SpeciesWitness", "Calibration: ${calibration.isCalibrated()}")
                if (calibration.isCalibrated()) {
                    val cropped = cropSpecies(bitmap)
                    if (cropped != null) {val result = SpeciesNameRecognizer.recognize(cropped)

                        val value = result.value

                        if (result.confidence >= 1.0f && value != null) {
                            val testimony = SpeciesTestimony(value, System.currentTimeMillis())

                            Log.d(
                                "SpeciesWitness",
                                "SpeciesTestimony(value=$value)"
                            )

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

    private fun cropSpecies(bitmap: Bitmap): Bitmap? {
        val region = calibration.enemyNameRegion       //until ontology is defined, or retired.
        val width = bitmap.width
        val height = bitmap.height

        val left = (region.x * width).toInt().coerceIn(0, width - 1)
        val top = (region.y * height).toInt().coerceIn(0, height - 1)
        val w = (region.width * width).toInt().coerceAtMost(width - left)
        val h = (region.height * height).toInt().coerceAtMost(height - top)

        if (w < 32 || h < 32) {
            Log.w("SpeciesWitness", "Crop dimensions too small for ML Kit: ${w}x${h}")
            return null
        }

        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            null
        }
    }
}

