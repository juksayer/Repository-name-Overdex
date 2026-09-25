package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SupportingMatchStart
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Preserves the established timer-overlay-clearance support role while reading
 * only verified crop artifacts. It is never a Match Start trigger.
 */
class PersistedTrainerInactiveTimerOverlayClearanceWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId(
        BattleWitnessContracts.trainerInactiveTimerOverlayClearance.witnessId,
        ObserverSource.SCREEN_CAPTURE
    ),
    override val name: String = "Trainer Inactive Timer Overlay Clearance Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var armed = false
                var accepted = false
                var frameIndex = 0
                match.articles.collect { article ->
                    if (accepted) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != BattleCropContracts.trainerInactiveTimerOverlayClearance.cropName) return@collect
                    frameIndex++
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact) ?: return@collect
                    try {
                        val stats = TrainerInactiveTimerOverlayClearanceMeasurer.measure(bitmap) ?: return@collect
                        if (!armed) {
                            armed = stats.upperColorfulPixelFraction >= 0.85f && stats.lowerColorfulPixelFraction >= 0.85f
                            return@collect
                        }
                        if (stats.upperColorfulPixelFraction > 0.55f || stats.lowerColorfulPixelFraction > 0.30f) return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = SupportingMatchStart(
                                frameIndex = frameIndex,
                                upperColorfulPixelFraction = stats.upperColorfulPixelFraction,
                                lowerColorfulPixelFraction = stats.lowerColorfulPixelFraction
                            ),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                        )
                        accepted = true
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}

internal data class TimerOverlayClearanceMeasurement(
    val upperColorfulPixelFraction: Float,
    val lowerColorfulPixelFraction: Float
)

internal object TrainerInactiveTimerOverlayClearanceMeasurer {
    private const val COLORFUL_SPREAD_THRESHOLD = 40

    fun measure(bitmap: Bitmap): TimerOverlayClearanceMeasurement? {
        if (bitmap.width < 2 || bitmap.height < 2) return null
        val split = bitmap.height / 2
        val upper = IntArray(bitmap.width * split)
        val lower = IntArray(bitmap.width * (bitmap.height - split))
        bitmap.getPixels(upper, 0, bitmap.width, 0, 0, bitmap.width, split)
        bitmap.getPixels(lower, 0, bitmap.width, 0, split, bitmap.width, bitmap.height - split)
        return TimerOverlayClearanceMeasurement(colorfulFraction(upper), colorfulFraction(lower))
    }

    internal fun colorfulFraction(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        val colorful = pixels.count { pixel ->
            val red = pixel ushr 16 and 0xff
            val green = pixel ushr 8 and 0xff
            val blue = pixel and 0xff
            maxOf(red, green, blue) - minOf(red, green, blue) >= COLORFUL_SPREAD_THRESHOLD
        }
        return colorful.toFloat() / pixels.size
    }
}
