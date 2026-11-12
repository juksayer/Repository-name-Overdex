package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.*

/**
 * Preserves a small, explicitly gated sequence of actual sprite-territory crops.
 * No frame queue, synthetic frame, absent-sprite result, or faint inference.
 * Encoding runs off the fast visual reader; overload can reduce the honest
 * sample count rather than delaying that reader or inventing missing samples.
 */
class TerminalVisualSequenceWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val artifactStore: CropArtifactStore,
    private val gate: TerminalVisualCaptureGate,
    private val calibrationProvider: () -> BattleCalibration = { calibration },
    private val captureDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Observer {
    override val observerId = ObserverId("TERMINAL_VISUAL_SEQUENCE_CAPTURE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Terminal Visual Sequence Capture"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private val availability = mutableMapOf<ActivePokemonSide, Boolean>()
    private var generation = 0L
    private var detachPreparation: (() -> Unit)? = null
    private var diagnostics: TerminalVisualCaptureDiagnostics? = null

    @Synchronized override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val epoch = ++generation
        val diagnostic = TerminalVisualCaptureDiagnostics().also { diagnostics = it }
        gate.beginSession()
        scope = CoroutineScope(captureDispatcher + SupervisorJob()).also { witnessScope ->
            // A separate consumer slot preserves the HP reader's early handoff.
            detachPreparation = match.acceptedChargeCapturePreparation.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL) { cue ->
                synchronized(this) {
                    if (generation != epoch || activeMatch !== match) return@synchronized
                    gate.prepare(cue)
                    trackWindows(diagnostic, System.currentTimeMillis())
                }
            }
            witnessScope.launch {
                match.articles.collect { article ->
                    synchronized(this@TerminalVisualSequenceWitness) {
                        if (generation != epoch || activeMatch !== match) return@collect
                        gate.observe(article)
                        if (article.payload is CropCaptured) {
                            val side = ActivePokemonSide.entries.firstOrNull { contract(it).witnessId == article.sourceId.id }
                            if (side != null) article.monotonicTimeNanos?.let { gate.published(side, it, article.id.value) }
                        }
                        trackWindows(diagnostic, System.currentTimeMillis())
                        publishCompleted(match, diagnostic)
                    }
                }
            }
            witnessScope.launch {
                input.supplyFrames { frame ->
                    val current = synchronized(this@TerminalVisualSequenceWitness) {
                        generation == epoch && activeMatch === match &&
                            gate.observeDimensions(frame.bitmap.width, frame.bitmap.height, frame.capturedAtMonotonicTimeNanos)
                    }
                    if (!current) return@supplyFrames
                    ActivePokemonSide.entries.forEach { side ->
                        val ticket = synchronized(this@TerminalVisualSequenceWitness) {
                            if (generation != epoch || activeMatch !== match) return@forEach
                            val window = gate.captureWindow(side)
                            val ticket = gate.takeFrame(side, frame.capturedAtMonotonicTimeNanos)
                            if (window != null) {
                                diagnostic.opened(window, window.evidenceReferences, frame.capturedAtWallTimeMillis)
                                diagnostic.received(window, frame.capturedAtMonotonicTimeNanos, frame.capturedAtWallTimeMillis,
                                    frame.publication, ticket != null,
                                    ticket == null && System.nanoTime() - frame.capturedAtMonotonicTimeNanos !in 0..TerminalVisualCaptureGate.MAX_SOURCE_AGE_NANOS)
                            }
                            ticket?.also { diagnostic.started(it) }
                        }
                        if (ticket == null && !gate.isCapturing(side)) {
                            reportAvailability(match, side, false, frame.capturedAtWallTimeMillis, epoch)
                        } else if (ticket != null) capture(match, frame, ticket, diagnostic, epoch)
                    }
                    synchronized(this@TerminalVisualSequenceWitness) {
                        if (generation == epoch && activeMatch === match) publishCompleted(match, diagnostic)
                    }
                }
            }
            // Expiry must not depend on another source frame arriving after a transition.
            witnessScope.launch {
                while (isActive) {
                    delay(100)
                    synchronized(this@TerminalVisualSequenceWitness) {
                        if (generation == epoch && activeMatch === match) publishCompleted(match, diagnostic)
                    }
                }
            }
        }
    }

    private fun capture(match: Match, frame: CapturedVisualFrame, ticket: TerminalVisualCaptureTicket,
        diagnostic: TerminalVisualCaptureDiagnostics, epoch: Long) {
        var preserved = false
        var retained = false
        var bytes = 0L
        var outcome = "ERROR"
        var copyNanos = 0L
        var preserveNanos = 0L
        val started = System.nanoTime()
        val cpuStarted = hpLocatorThreadCpuTimeNanos()
        val contract = contract(ticket.side)
        try {
            synchronized(this) {
                if (generation != epoch || activeMatch !== match) { outcome = "STOPPED"; return }
            }
            val crop = contract.crop
            val region = crop.region.regionIn(calibrationProvider())
            val rect = BattleCropResolver.resolveRect(region, frame.bitmap.width, frame.bitmap.height,
                crop.minimumSize, crop.areaInRegion)
            val readable = rect != null && frame.canObserve(BattleCropBounds(rect.left, rect.top, rect.right, rect.bottom)) &&
                SpeciesCaptureVisibility.canRead(region, frame.overlayRegion)
            reportAvailability(match, ticket.side, readable, frame.capturedAtWallTimeMillis, epoch)
            if (!readable) { outcome = "UNREADABLE"; return }
            val copyStarted = System.nanoTime()
            val resolved = BattleCropResolver.resolve(crop.cropName, region, frame.bitmap,
                crop.minimumSize, crop.areaInRegion)
            copyNanos = System.nanoTime() - copyStarted
            if (resolved == null) { outcome = "UNRESOLVED"; return }
            try {
                val preserveStarted = System.nanoTime()
                val artifact = try { artifactStore.preservePng(resolved.bitmap) }
                    finally { preserveNanos = System.nanoTime() - preserveStarted }
                if (artifact == null) { outcome = "PRESERVE_FAILED"; return }
                retained = true; bytes = artifact.byteCount
                synchronized(this) {
                    if (generation != epoch || activeMatch !== match) { outcome = "STOPPED"; return }
                    match.custody.submitTestimony(
                        sourceId = SourceId(contract.witnessId),
                        payload = CropCaptured(artifact, resolved.provenance, resolved.provenance.overlayBounds(frame.overlayRegion)),
                        timestamp = frame.capturedAtWallTimeMillis,
                        confidence = null,
                        evidenceReferences = emptyList(),
                        monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos,
                    )
                    preserved = true; outcome = "PRESERVED"
                }
            } finally { resolved.bitmap.recycle() }
        } catch (error: Exception) {
            Log.e("TerminalVisual", "Raw terminal crop preservation failed", error)
        } finally {
            gate.finishFrame(ticket, frame.capturedAtWallTimeMillis, preserved)
            val cpuFinished = hpLocatorThreadCpuTimeNanos()
            diagnostic.finished(ticket, outcome, System.nanoTime() - started, copyNanos, preserveNanos,
                if (cpuStarted != null && cpuFinished != null && cpuFinished >= cpuStarted) cpuFinished - cpuStarted else null,
                bytes, frame.capturedAtWallTimeMillis, retained = retained)
        }
    }

    private fun trackWindows(diagnostic: TerminalVisualCaptureDiagnostics, wallTime: Long): Set<Long> =
        ActivePokemonSide.entries.mapNotNull { gate.captureWindow(it) }.onEach {
            diagnostic.opened(it, it.evidenceReferences, wallTime)
        }.map { it.id }.toSet()

    private fun publishCompleted(match: Match, diagnostic: TerminalVisualCaptureDiagnostics, force: Boolean = false) {
        // Expiry/end also closes availability when no subsequent frame arrives.
        ActivePokemonSide.entries.filter { gate.captureWindow(it) == null }.forEach {
            reportAvailability(match, it, false, System.currentTimeMillis())
        }
        gate.drainCompleted(force).forEach { sequence ->
            match.custody.submitTestimony(
                SourceId(observerId.id), sequence.payload, sequence.perceivedAt,
                confidence = null, evidenceReferences = sequence.evidenceReferences,
                monotonicTimeNanos = sequence.payload.sourceEndedAtNanos,
            )
        }
        diagnostic.drain(trackWindows(diagnostic, System.currentTimeMillis()), System.nanoTime(), force).forEach { summary ->
            match.custody.submitTestimony(SourceId("TERMINAL_VISUAL_CAPTURE_DIAGNOSTICS"), RawTestimony(summary.text),
                summary.wallTime, null, summary.evidenceReferences, summary.sourceAt)
        }
    }

    @Synchronized private fun reportAvailability(match: Match, side: ActivePokemonSide, value: Boolean, at: Long,
        expectedGeneration: Long? = null) {
        if (expectedGeneration != null && (generation != expectedGeneration || activeMatch !== match)) return
        if (availability.put(side, value) != value) {
            match.custody.submitAvailability(SourceId(contract(side).witnessId), value, at)
        }
    }

    @Synchronized override fun stop() {
        generation++
        detachPreparation?.invoke(); detachPreparation = null
        gate.endSession()
        scope?.cancel("Terminal visual sequence capture stopped")
        scope = null
        activeMatch?.let { match ->
            diagnostics?.let { publishCompleted(match, it, force = true) }
            ActivePokemonSide.entries.forEach { reportAvailability(match, it, false, System.currentTimeMillis()) }
        }
        activeMatch = null
        diagnostics = null
        availability.clear()
    }

    private fun contract(side: ActivePokemonSide): BattleWitnessContract<CropCaptured> = when (side) {
        ActivePokemonSide.PLAYER -> BattleWitnessContracts.playerTerminalVisualCapture
        ActivePokemonSide.OPPONENT -> BattleWitnessContracts.opponentTerminalVisualCapture
    }
}
