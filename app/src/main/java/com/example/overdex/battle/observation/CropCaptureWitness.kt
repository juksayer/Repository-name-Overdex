package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Preserves purpose-named crops as durable testimony. It never recognizes
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
    private val captureDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Stable, high-value crops may persist the complete published frame as a
     * sparse forensic sample. Frequent recognizers persist only their measured
     * region: repeatedly encoding a full screen must never delay the live
     * Timeline or make Android reclaim the activity.
     */
    private val preserveCompleteFrame: Boolean = false,
    /** Optional post-custody delivery. The artifact and CropCaptured record already exist. */
    private val onCaptured: ((CropCaptured, Long, Long) -> Unit)? = null,
    /** Live calibration may supply a current region without rebuilding the observation session. */
    private val calibrationProvider: () -> BattleCalibration = { calibration },
    private val acceptsFrame: (Long) -> Boolean = { true },
    /** Preserve this second region from the same source frame and cadence. */
    private val companionCrop: BattleWitnessContract<CropCaptured>? = null,
    /** Synchronous preview of the announcement strip before file I/O; never transfers bitmap ownership. */
    private val onAnnouncementPixels: ((ResolvedBattleCrop, Long, OcrBox?) -> Unit)? = null,
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSampledAtNanos = 0L
    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSampledAtNanos = 0L
        val sourceId = SourceId(contract.witnessId)
        scope = CoroutineScope(captureDispatcher + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                input.supplyFrames { frame ->
                    fun reportAvailability(enabled: Boolean) {
                        if (lastEnabled != enabled) {
                            match.custody.submitAvailability(sourceId, enabled, frame.capturedAtWallTimeMillis)
                            lastEnabled = enabled
                        }
                    }
                    if (!isEnabled()) {
                        reportAvailability(false)
                        return@supplyFrames
                    }
                    if (!acceptsFrame(frame.capturedAtMonotonicTimeNanos)) return@supplyFrames
                    if (captureIntervalNanos > 0L && lastSampledAtNanos > 0L &&
                        frame.capturedAtMonotonicTimeNanos - lastSampledAtNanos < captureIntervalNanos
                    ) return@supplyFrames
                    lastSampledAtNanos = frame.capturedAtMonotonicTimeNanos
                    val activeCalibration = calibrationProvider()
                    val primary = resolve(contract, activeCalibration, frame, ::reportAvailability)
                    val companion = companionCrop?.let { resolve(it, activeCalibration, frame) }
                    try {
                        if (onAnnouncementPixels != null) {
                            val strip = if (contract.crop == BattleCropContracts.announcement) primary
                                else companion.takeIf { companionCrop?.crop == BattleCropContracts.announcement }
                            if (strip != null) try {
                                onAnnouncementPixels.invoke(strip, frame.capturedAtMonotonicTimeNanos,
                                    strip.provenance.overlayBounds(frame.overlayRegion))
                            } catch (failure: RuntimeException) {
                                Log.w("CropCapture", "Announcement preview unavailable", failure)
                            }
                        }
                        if (primary != null) capture(match, frame, contract, primary, preserveCompleteFrame, onCaptured)
                        if (companion != null) capture(match, frame, requireNotNull(companionCrop), companion, false, null)
                    } finally {
                        primary?.bitmap?.recycle()
                        companion?.bitmap?.recycle()
                    }
                }
            }
        }
    }

    private fun resolve(
        contract: BattleWitnessContract<CropCaptured>,
        calibration: BattleCalibration,
        frame: CapturedVisualFrame,
        reportAvailability: (Boolean) -> Unit = {},
    ): ResolvedBattleCrop? {
        val crop = contract.crop
        val region = TeamBadgeCropLocator.locate(crop.region, calibration, frame)
        val rect = BattleCropResolver.resolveRect(region, frame.bitmap.width, frame.bitmap.height,
            crop.minimumSize, crop.areaInRegion)
        val readable = rect != null && frame.canObserve(BattleCropBounds(rect.left, rect.top, rect.right, rect.bottom))
        reportAvailability(readable)
        return if (readable) BattleCropResolver.resolve(crop.cropName, region, frame,
            crop.minimumSize, crop.areaInRegion) else null
    }

    private suspend fun capture(
        match: Match,
        frame: CapturedVisualFrame,
        contract: BattleWitnessContract<CropCaptured>,
        resolved: ResolvedBattleCrop,
        preserveCompleteFrame: Boolean,
        onCaptured: ((CropCaptured, Long, Long) -> Unit)?,
    ) {
        try {
            val artifact = if (preserveCompleteFrame) {
                artifactStore.preserveFrameCrop(frame.capturedAtMonotonicTimeNanos,
                    resolved.bitmap, resolved.provenance, frame.bitmap)
            } else artifactStore.preservePng(resolved.bitmap)
            if (artifact == null) return
            currentCoroutineContext().ensureActive()
            val testimony = CropCaptured(artifact, resolved.provenance,
                resolved.provenance.overlayBounds(frame.overlayRegion))
            val receipt = match.custody.submitTestimony(
                sourceId = SourceId(contract.witnessId),
                payload = testimony,
                timestamp = frame.capturedAtWallTimeMillis,
                confidence = null,
                evidenceReferences = emptyList(),
                monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos,
            )
            if (contract.crop == BattleCropContracts.announcement) match.offerAnnouncementCrop(receipt)
            onCaptured?.invoke(testimony, frame.capturedAtWallTimeMillis, frame.capturedAtMonotonicTimeNanos)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("CropCapture", "Crop capture failed for ${contract.crop.cropName}", error)
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
