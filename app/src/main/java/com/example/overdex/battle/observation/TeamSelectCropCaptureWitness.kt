package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Preserves a throttled, raw Team Select crop while the pre-battle session is armed. */
class TeamSelectCropCaptureWitness(
    private val input: ObservationInput,
    private val calibration: TeamSelectCalibration,
    private val contract: TeamSelectCropContract,
    private val artifactStore: CropArtifactStore,
    private val isEnabled: () -> Boolean,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var lastCaptureNanos = Long.MIN_VALUE
    private var enabled: Boolean? = null

    override fun start(match: Match) {
        if (scope != null) return
        val source = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope -> witnessScope.launch {
            input.supplyFrames { frame ->
                val nowEnabled = isEnabled()
                if (enabled != nowEnabled) {
                    match.custody.submitAvailability(source, nowEnabled, frame.capturedAtWallTimeMillis)
                    enabled = nowEnabled
                }
                if (!nowEnabled || frame.capturedAtMonotonicTimeNanos - lastCaptureNanos < CAPTURE_INTERVAL_NANOS) return@supplyFrames
                val crop = contract.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                try {
                    val artifact = artifactStore.preservePng(crop.bitmap) ?: return@supplyFrames
                    lastCaptureNanos = frame.capturedAtMonotonicTimeNanos
                    match.custody.submitTestimony(source, CropCaptured(artifact, crop.provenance), frame.capturedAtWallTimeMillis, null, emptyList(), frame.capturedAtMonotonicTimeNanos)
                } finally { crop.bitmap.recycle() }
            }
        } }
    }

    override fun stop() { scope?.cancel(); scope = null }
    private companion object { const val CAPTURE_INTERVAL_NANOS = 1_000_000_000L }
}
