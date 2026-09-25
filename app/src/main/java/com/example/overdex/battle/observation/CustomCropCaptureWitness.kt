package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.CustomBattleCrop
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Preserves one user-authored Draggy Box; interpretation is deliberately separate. */
class CustomCropCaptureWitness(
    private val input: ObservationInput,
    private val definition: CustomBattleCrop,
    private val artifactStore: CropArtifactStore,
    private val isEnabled: () -> Boolean,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                input.supplyFrames { frame ->
                    if (!definition.enabled || !isEnabled()) return@supplyFrames
                    val resolved = BattleCropResolver.resolve(
                        cropName = "Custom:${definition.id}:${definition.name}",
                        region = definition.region,
                        source = frame.bitmap,
                        minimumSize = 8
                    ) ?: return@supplyFrames
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
                    } catch (error: Exception) {
                        Log.e("CustomCropCapture", "Unable to preserve ${definition.name}", error)
                    } finally {
                        resolved.bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Custom crop stopped"); scope = null }
}
