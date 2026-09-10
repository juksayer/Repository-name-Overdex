package com.example.overdex.battle.witness

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Production Witness responsible for observing raw OCR text in the announcement region
 * (moveBannerRegion) and submitting raw testimony to the canonical Timeline.
 */
class AttackIncomingWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("ATTACK_INCOMING_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Attack Incoming Witness",
    private val perceive: suspend (Bitmap) -> String? = { bitmap ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            Log.e("AttackIncomingWitness", "Perception failed", e)
            null
        }
    },
    private val crop: (Bitmap, com.example.overdex.model.AnchorRegion) -> Bitmap? = { bitmap, region ->
        val width = bitmap.width
        val height = bitmap.height

        val left = (region.x * width).toInt().coerceIn(0, width - 1)
        val top = (region.y * height).toInt().coerceIn(0, height - 1)
        val w = (region.width * width).toInt().coerceAtMost(width - left)
        val h = (region.height * height).toInt().coerceAtMost(height - top)

        if (w < 32 || h < 32) null
        else {
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

        Log.d("AttackIncomingWitness", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            input.supply { bitmap ->
                val receiptTimestamp = System.currentTimeMillis()
                match.custody.submitInputAvailability(sourceId, true, receiptTimestamp)

                if (calibration.isCalibrated()) {
                    val cropped = crop(bitmap, calibration.moveBannerRegion)
                    if (cropped != null) {
                        val text = perceive(cropped)
                        
                        if (text != null) {
                            Log.d("AttackIncomingWitness", "OCR text observed: \"$text\" (conf=null)")

                            // Submit raw testimony preserving spelling, case, whitespace, punctuation, and empty strings
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = RawTestimony(text),
                                timestamp = receiptTimestamp,
                                confidence = null
                            )

                            Log.d("ATTACK_SLICE", "Witness submitted RawTestimony: sourceId=${sourceId.id}, text=\"$text\"")
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
