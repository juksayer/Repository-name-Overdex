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

/**
 * Preserves one coherent Team Select frame and publishes every purpose-specific
 * aperture against that same immutable image. A single frame collector avoids
 * ten crop workers racing for different frames while this brief screen is open.
 */
class TeamSelectSnapshotCaptureWitness(
    private val input: ObservationInput,
    private val calibration: TeamSelectCalibration,
    private val contracts: List<TeamSelectCropContract>,
    private val artifactStore: CropArtifactStore,
    private val isEnabled: () -> Boolean,
    override val observerId: ObserverId,
    override val name: String,
) : Observer {
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastCaptureNanos = 0L
    private var enabled: Boolean? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                input.supplyFrames { frame ->
                    val nowEnabled = isEnabled()
                    if (enabled != nowEnabled) {
                        contracts.forEach { contract ->
                            match.custody.submitAvailability(
                                SourceId(sourceId(contract)),
                                nowEnabled,
                                frame.capturedAtWallTimeMillis,
                            )
                        }
                        enabled = nowEnabled
                    }
                    if (!nowEnabled ||
                        (lastCaptureNanos > 0L &&
                            frame.capturedAtMonotonicTimeNanos - lastCaptureNanos < CAPTURE_INTERVAL_NANOS)
                    ) return@supplyFrames

                    val resolved = contracts.mapNotNull { contract ->
                        contract.resolve(calibration, frame.bitmap)?.let { contract to it }
                    }
                    if (resolved.isEmpty()) return@supplyFrames
                    try {
                        val first = resolved.first().second
                        val artifact = artifactStore.preserveFrameCrop(
                            frameMonotonicTimeNanos = frame.capturedAtMonotonicTimeNanos,
                            bitmap = first.bitmap,
                            provenance = first.provenance,
                            sourceFrame = frame.bitmap,
                        ) ?: return@supplyFrames

                        lastCaptureNanos = frame.capturedAtMonotonicTimeNanos
                        resolved.forEach { (contract, crop) ->
                            match.custody.submitTestimony(
                                sourceId = SourceId(sourceId(contract)),
                                payload = CropCaptured(artifact, crop.provenance),
                                timestamp = frame.capturedAtWallTimeMillis,
                                confidence = null,
                                evidenceReferences = emptyList(),
                                monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos,
                            )
                        }
                    } finally {
                        resolved.forEach { (_, crop) -> crop.bitmap.recycle() }
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        if (enabled == true) {
            contracts.forEach { contract ->
                activeMatch?.custody?.submitAvailability(
                    SourceId(sourceId(contract)),
                    false,
                    System.currentTimeMillis(),
                )
            }
        }
        activeMatch = null
        enabled = null
        lastCaptureNanos = 0L
    }

    private fun sourceId(contract: TeamSelectCropContract): String = "${contract.cropName}_CAPTURE"

    private companion object {
        const val CAPTURE_INTERVAL_NANOS = 1_000_000_000L
    }
}
