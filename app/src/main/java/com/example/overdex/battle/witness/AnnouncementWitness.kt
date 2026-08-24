package com.example.overdex.battle.witness

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.AnnouncementRecognizer
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Match-scoped Witness responsible for identifying Pokémon entry/re-entry
 * announcements in the designated announcement region.
 */
class AnnouncementWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Announcement Witness",
    private val recognize: suspend (Bitmap?, Set<String>) -> RecognitionResult<String> = { b, v ->
        if (b == null) RecognitionResult(null, 0.0f, "AnnouncementRecognizer")
        else AnnouncementRecognizer.recognize(b, v)
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
    private var vocabulary: Set<String> = emptySet()

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("AnnouncementWitness", "start()")

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            // Retrieve canonical vocabulary once at Match start
            vocabulary = match.pokemonKnowledge.getAllSpeciesNames()
            Log.d("AnnouncementWitness", "Vocabulary loaded: ${vocabulary.size} species")

            input.supply { bitmap ->
                @Suppress("SENSELESS_COMPARISON")
                if (bitmap == null) {
                    Log.e("AnnouncementWitness", "Received null bitmap")
                    
                    // Support for JVM unit tests where Bitmap class exists but methods are stubs
                    val timestamp = System.currentTimeMillis()
                    val result = recognize(null, vocabulary)
                    if (result.confidence > 0f && result.value != null) {
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = RawTestimony(result.value),
                            timestamp = timestamp,
                            confidence = result.confidence
                        )
                    }
                    return@supply
                }

                val timestamp = System.currentTimeMillis()
                match.custody.submitInputAvailability(sourceId, true, timestamp)

                val region = calibration.moveBannerRegion
                val cropped = crop(bitmap, region)

                val result = if (cropped != null) {
                    recognize(cropped, vocabulary)
                } else {
                    null
                }

                if (result != null && result.confidence > 0f && result.value != null) {
                    Log.d("AnnouncementWitness", "Announcement recognized: ${result.value} (conf=${result.confidence})")

                    // Reality Handoff (Neutral Testimony)
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = RawTestimony(result.value),
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
        vocabulary = emptySet()
    }
}
