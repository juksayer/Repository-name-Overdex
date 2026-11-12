package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.*

/**
 * Measures available frames during the existing gate's owned charge phase.
 * Independent of sparse PNG encoding; no pixels survive a synchronous callback.
 * The shared gate's lifecycle/cues belong to TerminalVisualSequenceWitness.
 */
internal class TerminalParticleWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val gate: TerminalVisualCaptureGate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calibrationProvider: () -> BattleCalibration = { calibration },
) : Observer {
    override val observerId = ObserverId("TERMINAL_PARTICLE_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Terminal Particle Witness"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var match: Match? = null
    private var generation = 0L
    private var buffer: TerminalParticleObservationBuffer? = null
    private var available: Boolean? = null
    private var lastFrameReceipt = Long.MIN_VALUE
    private var appearance: TerminalAppearanceFrameMeasurer? = null

    @Synchronized override fun start(match: Match) {
        if (this.match != null) return
        this.match = match
        val epoch = ++generation
        // Never create another competing collector on channel/one-shot inputs.
        reportAvailability(match, false, System.currentTimeMillis())
        if (!input.supportsIndependentFrameCollectors) return
        val pixels = TerminalParticleFrameMeasurer()
        appearance = TerminalAppearanceFrameMeasurer()
        buffer = TerminalParticleObservationBuffer({ pixels.reset(); appearance?.reset() }, { payload, refs, at, wall ->
            match.custody.submitTestimony(SourceId(observerId.id), payload, wall, null, refs, at)
        })
        scope = CoroutineScope(dispatcher + SupervisorJob()).also { currentScope ->
            currentScope.launch {
                input.supplyFrames { frame ->
                    synchronized(this@TerminalParticleWitness) {
                        if (generation == epoch && this@TerminalParticleWitness.match === match) read(match, frame, pixels)
                    }
                }
            }
            currentScope.launch {
                while (isActive) {
                    delay(100)
                    synchronized(this@TerminalParticleWitness) {
                        if (generation != epoch || this@TerminalParticleWitness.match !== match) return@launch
                        val assignment = gate.particleAssignment()
                        buffer?.synchronize(assignment, System.currentTimeMillis())
                        // Publish partial batches even when the feed stops producing frames.
                        buffer?.flushIfDue()
                        if (assignment == null || System.nanoTime() - lastFrameReceipt !in 0..500_000_000L)
                            reportAvailability(match, false, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    private fun read(match: Match, frame: CapturedVisualFrame, pixels: TerminalParticleFrameMeasurer) {
        val wall = frame.capturedAtWallTimeMillis
        val at = frame.capturedAtMonotonicTimeNanos
        if (!gate.observeDimensions(frame.bitmap.width, frame.bitmap.height, at)) return
        val assignment = gate.particleAssignment()
        buffer?.synchronize(assignment, wall)
        if (assignment == null) {
            reportAvailability(match, false, wall)
            return
        }
        val crop = if (assignment.side == ActivePokemonSide.PLAYER) BattleCropContracts.playerTerminalVisual
            else BattleCropContracts.opponentTerminalVisual
        val currentCalibration = calibrationProvider()
        val region = crop.region.regionIn(currentCalibration)
        val rect = BattleCropResolver.resolveRect(region, frame.bitmap.width, frame.bitmap.height,
            crop.minimumSize, crop.areaInRegion)
        val bounds = rect?.let { BattleCropBounds(it.left, it.top, it.right, it.bottom) }
        val readable = bounds != null && frame.canObserve(bounds) && SpeciesCaptureVisibility.canRead(region, frame.overlayRegion)
        val appearanceRegions = if(assignment.side==ActivePokemonSide.PLAYER) listOf(
            currentCalibration.playerAppearanceRegion, currentCalibration.appearanceColorReferenceRegion,
            currentCalibration.appearanceBackgroundRegion) else emptyList()
        val appearanceReadable = appearanceRegions.isNotEmpty() && appearanceRegions.all { r ->
            val rect=BattleCropResolver.resolveRect(r,frame.bitmap.width,frame.bitmap.height)
            rect!=null && frame.canObserve(BattleCropBounds(rect.left,rect.top,rect.right,rect.bottom)) &&
                SpeciesCaptureVisibility.canRead(r,frame.overlayRegion)
        }
        var appearanceAvailable = false
        lastFrameReceipt = System.nanoTime()
        val status = buffer?.accept(frame.bitmap.width, frame.bitmap.height, bounds, at, wall, frame.publication,
            readable || appearanceReadable, independentAppearanceReadable = appearanceReadable) {
            val particle = if(readable) pixels.measure(frame.bitmap, requireNotNull(bounds), at) else {
                pixels.reset(); TerminalParticleFrameMeasurement(TerminalParticleReadStatus.UNREADABLE,at)
            }
            val measured = if(appearanceReadable) appearance?.measure(frame.bitmap,appearanceRegions,at) else {
                appearance?.reset()
                null
            }
            appearanceAvailable = measured?.status in setOf("SAMPLED","BASELINE","SOURCE_GAP","GEOMETRY_CHANGED","THROTTLED")
            particle.copy(appearance=measured)
        }
        reportAvailability(match, appearanceAvailable || status in setOf("SAMPLED", "BASELINE", "SOURCE_GAP", "GEOMETRY_CHANGED", "STREAM_CHANGED"), wall)
    }

    private fun reportAvailability(match: Match, value: Boolean, wall: Long) {
        if (available == value) return
        available = value
        match.custody.submitAvailability(SourceId(observerId.id), value, wall)
    }

    @Synchronized override fun stop() {
        ++generation
        scope?.cancel("Terminal particle measurement stopped")
        scope = null
        buffer?.synchronize(null, System.currentTimeMillis())
        buffer = null
        appearance?.close(); appearance=null
        match?.let { reportAvailability(it, false, System.currentTimeMillis()) }
        match = null
        available = null
        lastFrameReceipt = Long.MIN_VALUE
    }
}
