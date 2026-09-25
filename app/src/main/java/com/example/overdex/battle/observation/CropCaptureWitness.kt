package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Preserves one purpose-named crop as durable testimony. It never recognizes
 * or interprets the crop, and it submits nothing if persistence fails.
 */
class CropCaptureWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val contract: BattleWitnessContract<CropCaptured>,
    private val artifactStore: CropArtifactStore,
    private val isEnabled: () -> Boolean = { true },
    override val observerId: ObserverId,
    override val name: String,
    /**
     * Preserves a measured crop cadence without flooding the Timeline with an
     * indistinguishable copy for every display frame. Witnesses that need a
     * faster cadence may explicitly request one.
     */
    private val captureIntervalNanos: Long = 200_000_000L,
    /** The narrow identity strips use I/O so capture pressure cannot starve them. */
    private val captureDispatcher: CoroutineDispatcher = Dispatchers.Default
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastCapturedAtNanos = 0L
    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val sourceId = SourceId(contract.witnessId)
        scope = CoroutineScope(captureDispatcher + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                input.supplyFrames { frame ->
                    val enabled = isEnabled()
                    if (lastEnabled != enabled) {
                        match.custody.submitAvailability(sourceId, enabled, frame.capturedAtWallTimeMillis)
                        lastEnabled = enabled
                    }
                    if (!enabled) return@supplyFrames
                    if (captureIntervalNanos > 0L && lastCapturedAtNanos > 0L &&
                        frame.capturedAtMonotonicTimeNanos - lastCapturedAtNanos < captureIntervalNanos
                    ) return@supplyFrames
                    val resolved = contract.crop.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                    try {
                        val artifact = artifactStore.preservePng(resolved.bitmap) ?: return@supplyFrames
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = CropCaptured(artifact, resolved.provenance),
                            timestamp = frame.capturedAtWallTimeMillis,
                            confidence = null,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                        )
                        lastCapturedAtNanos = frame.capturedAtMonotonicTimeNanos
                    } catch (error: Exception) {
                        Log.e("CropCaptureWitness", "Crop preservation failed for ${contract.crop.cropName}", error)
                    } finally {
                        resolved.bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(contract.witnessId), false, System.currentTimeMillis())
        lastEnabled = false
        activeMatch = null
        scope?.cancel("Witness stopped")
        scope = null
    }
}
