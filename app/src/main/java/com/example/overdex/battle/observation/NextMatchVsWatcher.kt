package com.example.overdex.battle.observation

import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.DroidballObservationInput
import kotlinx.coroutines.*

/** Watches only the result-to-next-battle gap; it hands the accepted VS crop to the new Match. */
class NextMatchVsWatcher(
    private val calibration: BattleCalibration,
    private val awaitingNextMatch: () -> Boolean,
    private val onVs: suspend (ResolvedBattleCrop, com.example.overdex.model.observation.CapturedVisualFrame) -> Unit
) {
    private var scope: CoroutineScope? = null
    fun start() {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { watcher -> watcher.launch {
            var lastAttempt = 0L
            DroidballObservationInput().supplyFrames { frame ->
                if (!awaitingNextMatch() || frame.capturedAtMonotonicTimeNanos - lastAttempt < 400_000_000L) return@supplyFrames
                lastAttempt = frame.capturedAtMonotonicTimeNanos
                val crop = BattleCropContracts.countdownGlyph.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                val isVs = try { AnnouncementRecognizer.recognize(crop.bitmap).value?.uppercase()?.replace(" ", "")?.contains("VS") == true } finally { crop.bitmap.recycle() }
                if (isVs) onVs(BattleCropContracts.countdownGlyph.resolve(calibration, frame.bitmap) ?: return@supplyFrames, frame)
            }
        }}
    }
    fun stop() { scope?.cancel(); scope = null }
}
