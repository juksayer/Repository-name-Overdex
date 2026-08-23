package com.example.overdex.battle.witness

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.ObservationRecognizer
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

class GoodEffortWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("GOOD_EFFORT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Good Effort Witness",
    private val recognize: suspend (Bitmap?, String) -> List<RecognitionResult<*>> = { cropped, regionId ->
        if (cropped == null) emptyList()
        else ObservationRecognizer.recognize(CaptureObservation(regionId, cropped), stage = "MATCH_END")
    },
    private val crop: (Bitmap?, com.example.overdex.model.AnchorRegion) -> Bitmap? = { bitmap, region ->
        if (bitmap == null) null
        else {
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
    }
) : Observer {

    private var scope: CoroutineScope? = null

    private fun isMatch(result: RecognitionResult<*>): Boolean {
        if (result.confidence < 1.0f) return false

        return (result.value as? String)
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("[^A-Z! ]"), "")
            ?.contains("GOOD EFFORT") == true
    }

    override fun start(match: Match) {
        if (scope != null) return

        Log.d("GoodEffortWitness", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            input.supply { bitmap ->
                Log.d("GoodEffortWitness", "bitmap received")
                
                val timestamp = System.currentTimeMillis()
                match.custody.submitInputAvailability(sourceId, true, timestamp)

                val cropped = crop(bitmap, calibration.goodEffortRegion)

                val recognitionResults = if (cropped != null) {
                    recognize(cropped, "GoodEffort")
                } else if (bitmap == null) {
                    // Support for JVM unit tests where Bitmap class exists but methods are stubs
                    recognize(null, "GoodEffort")
                } else {
                    emptyList()
                }

                val result = recognitionResults
                    .firstOrNull { it.value is String }

                if (result != null && isMatch(result)) {
                    Log.d(
                        "GoodEffortWitness",
                        "GOOD EFFORT recognized: ${result.value} confidence=${result.confidence}"
                    )

                    // Reality Handoff (Neutral Testimony)
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = RawTestimony(result.value.toString()),
                        timestamp = timestamp,
                        confidence = result.confidence
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Observer stopped")
        scope = null
    }
}
