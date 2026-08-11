package com.example.overdex.battle.witness

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
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

class YouWinWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("YOU_WIN_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "You Win Witness"
) : Observer {

    private var scope: CoroutineScope? = null


    private fun isMatch(result: RecognitionResult<String>): Boolean {
        if (result.confidence < 1.0f) return false

        return result.value
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("[^A-Z! ]"), "")
            ?.contains("YOU WIN") == true
    }

    override fun start(match: Match) {
        if (scope != null) return

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        Log.d("YouWinWitness", "start()")

        newScope.launch {
            input.supply { bitmap ->
                Log.d("YouWinWitness", "bitmap received")

                val region = calibration.youWinRegion

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

                if (w < 32 || h < 32) return@supply

                val cropped = try {
                    Bitmap.createBitmap(bitmap, left, top, w, h)
                } catch (e: Exception) {
                    null
                }

                if (cropped != null) {
                    val result = SpeciesNameRecognizer.recognize(cropped)

                    if (isMatch(result)) {
                        Log.d(
                            "YouWinWitness",
                            "YOU WIN recognized: ${result.value} confidence=${result.confidence}"
                        )
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Observer stopped")
        scope = null
    }}