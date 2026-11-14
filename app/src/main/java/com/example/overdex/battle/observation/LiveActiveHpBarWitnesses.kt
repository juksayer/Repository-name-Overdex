package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
import com.example.overdex.battle.custody.ObservationSessionStoppedByUser
import com.example.overdex.model.AnchorRegion
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.PlayerActiveIdentityResolved
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.CapturedVisualFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

/** A transient, bitmap-free handoff from the live HP witness to derived witnesses. */
internal data class LiveActiveHpBarSample(
    val measurement: ActiveHpBarMeasurement,
    val appearance: ActiveHpBarBorderAppearance?,
    val recipientVisual: FastMoveRecipientVisualSample?,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    // Charge-camera pixels remain truthful HP evidence, but are unavailable
    // for Fast Move detectors. The first normal return also resets baselines.
    val fastMoveEvidenceAllowed: Boolean = true,
    // Only the outline/interior verifier may certify EMPTY. A rounded scalar
    // fraction or a missing bar cannot waive ordinary damage settling.
    val fillColor: HpBarFillColor? = null,
    val baselineOnly: Boolean = false,
)

/** A small color grid sampled below the tracked HP bar; it owns no Bitmap. */
internal data class FastMoveRecipientVisualSample(val pixels: IntArray, val bounds: BattleCropBounds? = null)

/** Actual visual pixels have their own stream; a missing HP reading is not a missing image. */
internal data class LiveRecipientVisualSample(
    val visual: FastMoveRecipientVisualSample?,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long,
)

internal class LiveActiveHpBarFrameHub {
    private val latest = AtomicReference<LiveActiveHpBarSample?>(null)
    private val _samples = MutableSharedFlow<LiveActiveHpBarSample>(
        replay = 0,
        // Border pulses can be shorter than one rendered frame. Keep enough
        // bitmap-free measurements for the detector coroutines to survive a
        // brief scheduling stall without silently losing several move uses.
        // At 30 fps this preserves about one second of bitmap-free history for
        // every independent detector. It costs only small measurements and
        // sampled color grids, never source-frame Bitmaps.
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val samples = _samples.asSharedFlow()
    internal val sampleSubscriberCount: Int get() = _samples.subscriptionCount.value
    private val _visualSamples = MutableSharedFlow<LiveRecipientVisualSample>(
        extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val visualSamples = _visualSamples.asSharedFlow()
    private var lastVisualAt = Long.MIN_VALUE

    fun publish(sample: LiveActiveHpBarSample) {
        latest.set(sample)
        _samples.tryEmit(sample)
        publishVisual(LiveRecipientVisualSample(
            sample.recipientVisual.takeIf { sample.fastMoveEvidenceAllowed },
            sample.capturedAtWallTimeMillis, sample.capturedAtMonotonicTimeNanos))
    }

    @Synchronized fun publishVisual(sample: LiveRecipientVisualSample) {
        if (sample.capturedAtMonotonicTimeNanos <= lastVisualAt) return
        lastVisualAt = sample.capturedAtMonotonicTimeNanos
        _visualSamples.tryEmit(sample)
    }

    fun latestSample(): LiveActiveHpBarSample? = latest.get()
}

/**
 * Measures the active bar directly from the captured frame stream.
 *
 * The live measurement path is deliberately separate from [CropCaptureWitness].
 * It can inspect every usable frame without turning each frame into a PNG. The
 * crop witness still preserves a sparse forensic sample, so the Timeline keeps
 * a durable source when a later investigation needs one.
 */
internal class LiveActiveHpBarWitness(
    private val input: ObservationInput,
    private val tracker: LiveBattlefieldHpTracker,
    private val isEnabled: () -> Boolean,
    private val playerFrameHub: LiveActiveHpBarFrameHub,
    private val opponentFrameHub: LiveActiveHpBarFrameHub,
    private val onDepartureCheck: (ActivePokemonSide, Long) -> Unit = { _, _ -> },
    private val chargedImpactHpRegion: AnchorRegion = com.example.overdex.data.BattleCalibration().chargedImpactHpRegion,
    private val onBattlefieldReturn: (Long) -> Unit = {},
) : Observer {
    override val observerId = ObserverId("ACTIVE_HP_BARS_FRAME_WITNESS", ObserverSource.SCREEN_CAPTURE)
    override val name = "Active HP Bars Frame"
    override val managesAvailability = true

    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private val lastSpeciesNames = mutableMapOf<ActivePokemonSide, String>()
    private val lastSpeciesAt = mutableMapOf<ActivePokemonSide, Long>()
    private val lastAppearanceAt = mutableMapOf<ActivePokemonSide, Long>()
    private val stateLock = Any()
    private val terminalWatch = TerminalHpWatchPolicy()
    private val completeTrack = CompleteHpBarTrack()
    private val chargedImpactWatch = ChargedImpactHpWatchPolicy()
    private val departureChecks = HpBarDepartureCheckPolicy()
    private val recipientWatch = PostChargeRecipientWatchPolicy()
    private var terminalDiagnostics = TerminalHpWatchDiagnostics()
    private val lastPublishedAt = mutableMapOf<ActivePokemonSide, Long>()
    private var terminalEnabled: Boolean? = null
    private var ended = false
    private var earlyPreparationEpoch = 0L
    private var detachEarlyPreparation: (() -> Unit)? = null
    private var firstChargeScanToken: Long? = null
    private var firstChargeMeasurementToken: Long? = null
    private val terminalDiagnosticSource = SourceId("ACTIVE_HP_TERMINAL_WATCH_DIAGNOSTICS")
    private val chargePreparationDiagnosticSource = SourceId("CHARGED_HP_CAPTURE_SCHEDULING")
    private val sources = mapOf(
        ActivePokemonSide.PLAYER to SourceId(BattleWitnessContracts.playerActiveHpBar.witnessId),
        ActivePokemonSide.OPPONENT to SourceId(BattleWitnessContracts.opponentActiveHpBar.witnessId),
    )

    fun canCaptureChargedImpact(at: Long): Boolean = synchronized(stateLock) {
        !ended && isEnabled() && chargedImpactWatch.canCapture(at)
    }

    private fun availability(match: Match, enabled: Boolean, at: Long) {
        sources.values.forEach { match.custody.submitAvailability(it, enabled, at) }
        lastEnabled = enabled
    }

    private fun terminalSummary(match: Match, reason: String) {
        match.custody.submitTestimony(
            terminalDiagnosticSource, RawTestimony(terminalDiagnostics.summary(reason)),
            System.currentTimeMillis(), 1f, emptyList(), System.nanoTime(),
        )
    }

    /** This lock never surrounds a general geometry search. */
    private fun publishMeasurement(
        match: Match, frame: CapturedVisualFrame, side: ActivePokemonSide,
        measurement: ActiveHpBarMeasurement, generation: Long, terminal: Boolean,
        chargedImpactCueIds: List<String>? = null,
        fillColor: HpBarFillColor? = null,
    ) = synchronized(stateLock) {
        val at = frame.capturedAtMonotonicTimeNanos
        if (ended || !isEnabled() || !terminalWatch.isCurrentGeneration(side, generation) ||
            lastPublishedAt[side]?.let { at <= it } == true) {
            if (terminal) terminalDiagnostics.suppressed()
            return@synchronized
        }
        val phaseCueIds = chargedImpactCueIds ?: chargedImpactWatch.cameraCueIds(side, measurement, at)
        val disposition = chargedImpactWatch.fastMoveDisposition(side, measurement, at)
        val fastMoveEvidenceAllowed = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.EVIDENCE
        val samplingCpuStarted = hpLocatorThreadCpuTimeNanos()
        val samplingStarted = System.nanoTime()
        val appearance = if (fastMoveEvidenceAllowed) ActiveHpBarBorderAppearanceMeasurer.measure(frame.bitmap, measurement) else null
        val recipientVisual = if (fastMoveEvidenceAllowed) FastMoveRecipientVisualSampler.sample(frame.bitmap, measurement) else null
        val samplingCpuFinished = hpLocatorThreadCpuTimeNanos()
        match.hpObservationDiagnostics.onPostLocatorSampling(
            side, System.nanoTime() - samplingStarted,
            if (samplingCpuStarted != null && samplingCpuFinished != null && samplingCpuFinished >= samplingCpuStarted)
                samplingCpuFinished - samplingCpuStarted else null,
        )
        lastPublishedAt[side] = at
        match.hpObservationDiagnostics.onSubmission(side, at, System.nanoTime())
        match.custody.submitTestimony(
            sourceId = if (phaseCueIds == null) sources.getValue(side) else SourceId(ChargedImpactHpSource.id(side)),
            payload = ActiveHpBarMeasured(
                side, measurement.left, measurement.top, measurement.right, measurement.bottom,
                measurement.filledFraction, frame.bitmap.width, frame.bitmap.height,
                fillColor = fillColor?.name,
            ),
            timestamp = frame.capturedAtWallTimeMillis,
            confidence = measurement.confidence,
            evidenceReferences = phaseCueIds.orEmpty(),
            monotonicTimeNanos = at,
        )
        val hub = if (side == ActivePokemonSide.PLAYER) playerFrameHub else opponentFrameHub
        hub.publish(LiveActiveHpBarSample(measurement, appearance, recipientVisual,
            frame.capturedAtWallTimeMillis, at, frame.bitmap.width, frame.bitmap.height,
            fastMoveEvidenceAllowed, fillColor,
            baselineOnly = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.BASELINE_ONLY))
        if (terminal) terminalDiagnostics.published(side)
    }

    private fun checkTerminalFrame(match: Match, frame: CapturedVisualFrame) {
        val enabled = isEnabled()
        synchronized(stateLock) {
            if (terminalEnabled != enabled) {
                // Ignore's last source frame is a boundary; the first newly
                // enabled frame is eligible for fresh ordinary acquisition.
                terminalWatch.resetAll(if (enabled) null else frame.capturedAtMonotonicTimeNanos)
                completeTrack.resetAll(if (enabled) null else frame.capturedAtMonotonicTimeNanos)
                chargedImpactWatch.resetAll(if (enabled) null else frame.capturedAtMonotonicTimeNanos)
                recipientWatch.reset(frame.capturedAtMonotonicTimeNanos)
                terminalDiagnostics.resetContinuity()
                terminalEnabled = enabled
            }
            if (ended || !enabled) return
        }
        terminalDiagnostics.receive(frame.capturedAtMonotonicTimeNanos, frame.publication)
        val results = terminalWatch.checkFrame(frame.bitmap.width, frame.bitmap.height,
            frame.capturedAtMonotonicTimeNanos) { _, anchor ->
            val cpuStarted = hpLocatorThreadCpuTimeNanos()
            val started = System.nanoTime()
            // Mask the complete local search, not only the old outline: a
            // moved presentation or retained badge must never become game HP.
            val bounds = TrackedTerminalHpBarMeasurer.searchBounds(frame.bitmap.width, frame.bitmap.height, anchor)
            val observable = bounds != null && frame.canObserve(bounds)
            val sample = if (observable) TrackedTerminalHpBarMeasurer.measure(frame.bitmap, anchor) else null
            val cpuFinished = hpLocatorThreadCpuTimeNanos()
            terminalDiagnostics.checked(
                if (!observable) "OBSCURED" else sample?.fillColor?.name ?: "UNAVAILABLE",
                System.nanoTime() - started,
                if (cpuStarted != null && cpuFinished != null && cpuFinished >= cpuStarted) cpuFinished - cpuStarted else null,
            )
            sample
        }
        results.forEach { publishMeasurement(match, frame, it.side, it.sample.measurement, it.generation, true,
            fillColor = it.sample.fillColor) }
        checkCompleteFrame(match, frame)
        checkChargedImpactFrame(match, frame)
        checkRecipientFrame(frame)
    }

    private fun recordComplete(match: Match, frame: CapturedVisualFrame, side: ActivePokemonSide,
        sample: CompleteHpBarSample, generation: Long, cueIds: List<String> = emptyList()) = synchronized(stateLock) {
        if (ended || !isEnabled()) return@synchronized
        val change = completeTrack.accept(side, sample, frame.capturedAtMonotonicTimeNanos, generation)
            ?: return@synchronized
        val b = sample.bounds
        match.custody.submitTestimony(SourceId("COMPLETE_${side.name}_HP_BAR_FRAME_WITNESS"),
            com.example.overdex.battle.custody.CompleteHpBarObserved(side, b.left, b.top, b.right, b.bottom,
                frame.bitmap.width, frame.bitmap.height, sample.interiorColumns, sample.liveColumns,
                sample.damageColumns, change.status, change.candidateSince, lastAppearanceAt[side],
                sample.railBrightness, sample.backgroundBrightness,
                sample.backgroundMaskedColumns, sample.unmaskedUncertainColumns),
            frame.capturedAtWallTimeMillis, sample.confidence, cueIds, frame.capturedAtMonotonicTimeNanos)
    }

    private fun measureCompleteFrame(frame: CapturedVisualFrame, anchor: ActiveHpBarMeasurement): CompleteHpBarSample? {
        val started = System.nanoTime()
        val cpuStarted = hpLocatorThreadCpuTimeNanos()
        val sample = TrackedTerminalHpBarMeasurer.measureComplete(frame.bitmap, anchor)
        val cpuFinished = hpLocatorThreadCpuTimeNanos()
        terminalDiagnostics.checked(if (sample == null) "COMPLETE_UNAVAILABLE" else "COMPLETE_MEASURED",
            System.nanoTime() - started,
            if (cpuStarted != null && cpuFinished != null && cpuFinished >= cpuStarted) cpuFinished - cpuStarted else null)
        return sample
    }

    private fun checkCompleteFrame(match: Match, frame: CapturedVisualFrame) {
        completeTrack.frameSize(frame.bitmap.width, frame.bitmap.height)
        completeTrack.requests(frame.capturedAtMonotonicTimeNanos).forEach { request ->
            // Charge camera has its own recipient-owned acquisition and fixed window.
            if (chargedImpactWatch.cameraCueIds(request.side, request.anchor,
                    frame.capturedAtMonotonicTimeNanos) != null) return@forEach
            val bounds = TrackedTerminalHpBarMeasurer.searchBounds(frame.bitmap.width, frame.bitmap.height, request.anchor)
            if (bounds == null || !frame.canObserve(bounds)) return@forEach
            val sample = measureCompleteFrame(frame, request.anchor) ?: return@forEach
            recordComplete(match, frame, request.side, sample, request.generation)
        }
    }

    private fun checkRecipientFrame(frame: CapturedVisualFrame) {
        val at = frame.capturedAtMonotonicTimeNanos
        recipientWatch.requests(at).forEach { request ->
            val bounds = FastMoveRecipientVisualSampler.bounds(frame.bitmap.width, frame.bitmap.height, request.anchor)
            val visible = bounds != null && frame.canObserve(bounds) && SpeciesCaptureVisibility.canRead(
                AnchorRegion(bounds.left.toFloat() / frame.bitmap.width, bounds.top.toFloat() / frame.bitmap.height,
                    (bounds.right - bounds.left).toFloat() / frame.bitmap.width,
                    (bounds.bottom - bounds.top).toFloat() / frame.bitmap.height), frame.overlayRegion)
            val visual = if (visible) FastMoveRecipientVisualSampler.sample(frame.bitmap, request.anchor) else null
            synchronized(stateLock) {
                if (ended || !isEnabled() || !recipientWatch.current(request, at)) return@forEach
                val hub = if (request.side == ActivePokemonSide.PLAYER) playerFrameHub else opponentFrameHub
                hub.publishVisual(LiveRecipientVisualSample(visual, frame.capturedAtWallTimeMillis, at))
            }
        }
    }

    private fun checkChargedImpactFrame(match: Match, frame: CapturedVisualFrame) {
        val request = chargedImpactWatch.request(frame.bitmap.width, frame.bitmap.height,
            frame.capturedAtMonotonicTimeNanos) ?: return
        synchronized(stateLock) {
            if (ended || !isEnabled() || !chargedImpactWatch.current(request)) return
            if (firstChargeScanToken != request.token) {
                firstChargeScanToken = request.token
                match.custody.submitTestimony(chargePreparationDiagnosticSource,
                    RawTestimony("stage=FIRST_SCAN windowToken=${request.token} recipient=${request.side} " +
                        chargedImpactWatch.preparationDescription()), frame.capturedAtWallTimeMillis,
                    null, request.cueArticleIds, frame.capturedAtMonotonicTimeNanos)
            }
        }
        val cpuStarted = hpLocatorThreadCpuTimeNanos()
        val started = System.nanoTime()
        val territory = BattleCropResolver.resolveRect(chargedImpactHpRegion, frame.bitmap.width, frame.bitmap.height)
            ?.let { BattleCropBounds(it.left, it.top, it.right, it.bottom) }
        val sharedTerritory = ChargedImpactHpBarMeasurer.sharedTerritory(
            frame.bitmap.width, frame.bitmap.height, territory,
            tracker.hpTerritory(request.side, frame.bitmap.width, frame.bitmap.height))
        val search = sharedTerritory?.let {
            ChargedImpactHpBarMeasurer.searchBounds(frame.bitmap.width, frame.bitmap.height, request.reference, it)
        }
        val bounds = if (request.anchor != null) {
            search?.let {
                TrackedTerminalHpBarMeasurer.searchBounds(frame.bitmap.width, frame.bitmap.height, request.anchor, it)
            }
        } else search
        val observable = bounds != null && frame.canObserve(bounds)
        val completeGeneration = completeTrack.generation(request.side)
        val complete = when {
            !observable -> null
            request.anchor != null -> TrackedTerminalHpBarMeasurer.measureComplete(frame.bitmap, request.anchor, search)
            else -> ChargedImpactHpBarMeasurer.acquireComplete(frame.bitmap, request.reference, search)
        }
        val sample = complete?.hpSample
        val cpuFinished = hpLocatorThreadCpuTimeNanos()
        val kind = if (request.anchor == null) "CHARGE_ACQUIRE" else "CHARGE_TRACK"
        terminalDiagnostics.checked("${kind}_${if (!observable) "OBSCURED" else sample?.fillColor?.name ?: "UNAVAILABLE"}",
            System.nanoTime() - started,
            if (cpuStarted != null && cpuFinished != null && cpuFinished >= cpuStarted) cpuFinished - cpuStarted else null)
        if (complete == null) return
        synchronized(stateLock) {
            if (ended || !isEnabled() || !chargedImpactWatch.current(request) ||
                !chargedImpactWatch.acceptGeometry(request, complete.anchor(), frame.capturedAtMonotonicTimeNanos)) return
            recordComplete(match, frame, request.side, complete, completeGeneration, request.cueArticleIds)
            if (sample == null) return
            // Camera acquisition has its own appearance/phase token. An
            // ordinary locator finishing a real relocation must not invalidate
            // this one-frame zero merely by changing the red watch's geometry.
            if (ended || !isEnabled() || !chargedImpactWatch.current(request) ||
                !chargedImpactWatch.accept(request, sample, frame.capturedAtMonotonicTimeNanos)) return
            if (firstChargeMeasurementToken != request.token) {
                firstChargeMeasurementToken = request.token
                match.custody.submitTestimony(chargePreparationDiagnosticSource,
                    RawTestimony("stage=FIRST_ACCEPTED_SAMPLE windowToken=${request.token} " +
                        "recipient=${request.side} fillColor=${sample.fillColor}"),
                    frame.capturedAtWallTimeMillis, null, request.cueArticleIds, frame.capturedAtMonotonicTimeNanos)
            }
            recipientWatch.cameraMeasured(frame.capturedAtMonotonicTimeNanos)
            publishMeasurement(match, frame, request.side, sample.measurement,
                terminalWatch.snapshotGeneration(request.side), true, request.cueArticleIds, sample.fillColor)
        }
    }

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesNames.clear()
        lastSpeciesAt.clear()
        lastAppearanceAt.clear()
        val preparationEpoch = synchronized(stateLock) {
            terminalWatch.resetAll()
            completeTrack.resetAll()
            chargedImpactWatch.resetAll()
            departureChecks.begin()
            recipientWatch.begin()
            terminalDiagnostics = TerminalHpWatchDiagnostics()
            lastPublishedAt.clear()
            terminalEnabled = isEnabled()
            ended = false
            firstChargeScanToken = null
            firstChargeMeasurementToken = null
            ++earlyPreparationEpoch
        }
        detachEarlyPreparation = match.acceptedChargeCapturePreparation.attach { cue ->
            synchronized(stateLock) {
                if (ended || earlyPreparationEpoch != preparationEpoch || activeMatch !== match || !isEnabled())
                    return@synchronized
                val receivedAt = System.nanoTime()
                val result = when (cue.kind) {
                    AcceptedChargeCaptureCueKind.GET_READY -> chargedImpactWatch.prepare(
                        ActivePokemonSide.OPPONENT, cue.capturedAtNanos, cue.cropArticleId)
                    AcceptedChargeCaptureCueKind.ATTACK_INCOMING -> chargedImpactWatch.prepare(
                        ActivePokemonSide.PLAYER, cue.capturedAtNanos, cue.cropArticleId)
                    AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS -> chargedImpactWatch.prepareFromPlayerQte(
                        cue.capturedAtNanos, cue.cropArticleId)
                }
                // No inferred AttackIncoming/damage/faint is published here. The
                // ledger keeps this scheduling receipt distinct from testimony.
                match.custody.submitTestimony(chargePreparationDiagnosticSource,
                    RawTestimony("stage=EARLY_PREPARATION kind=${cue.kind} result=$result " +
                        "recognizedAt=${cue.recognizedAtNanos} receivedAt=$receivedAt " +
                        "recognitionDelayMs=${(cue.recognizedAtNanos - cue.capturedAtNanos) / 1_000_000} " +
                        chargedImpactWatch.preparationDescription()), cue.capturedAtWallTimeMillis,
                    null, listOf(cue.cropArticleId), cue.capturedAtNanos)
            }
        }
        match.beginHpObservationDiagnostics(input::latestVisualPublication)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                while (true) {
                    delay(5_000)
                    match.publishHpObservationDiagnostics(HpDiagnosticSummaryReason.PERIODIC)
                    terminalSummary(match, "PERIODIC")
                }
            }
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> {
                            val at = article.monotonicTimeNanos ?: return@collect
                            val changed = synchronized(stateLock) {
                                val appearanceAt = payload.appearanceStartedAtNanos?.takeIf { it in 0L..at } ?: at.takeIf {
                                    article.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
                                }
                                val newAppearance = appearanceAt != null &&
                                    appearanceAt > (lastAppearanceAt[payload.side] ?: Long.MIN_VALUE)
                                if (at < (lastSpeciesAt[payload.side] ?: Long.MIN_VALUE)) false else {
                                    lastSpeciesAt[payload.side] = at
                                    if (newAppearance) lastAppearanceAt[payload.side] = appearanceAt!!
                                    if (!newAppearance && lastSpeciesNames[payload.side] == payload.speciesName) false else {
                                        terminalWatch.reset(payload.side, appearanceAt ?: at)
                                        completeTrack.reset(payload.side, appearanceAt ?: at)
                                        chargedImpactWatch.appearanceBoundary(payload.side, appearanceAt ?: at)
                                        departureChecks.reset(payload.side, at)
                                        recipientWatch.reset(at)
                                        lastSpeciesNames[payload.side] = payload.speciesName
                                        true
                                    }
                                }
                            }
                            if (changed) {
                                tracker.appearanceBoundary(payload.side, payload.appearanceStartedAtNanos?.takeIf { it in 0L..at } ?: at)
                                tracker.observeSpecies(payload.side, payload.speciesName, at)
                                match.hpObservationDiagnostics.resetContinuity(payload.side)
                            }
                        }
                        is PlayerActiveIdentityResolved -> {
                            val side = ActivePokemonSide.PLAYER
                            val changed = synchronized(stateLock) {
                                if (payload.appearanceStartedAtNanos <= (lastAppearanceAt[side] ?: Long.MIN_VALUE)) false else {
                                    lastAppearanceAt[side] = payload.appearanceStartedAtNanos
                                    terminalWatch.reset(side, payload.appearanceStartedAtNanos)
                                    completeTrack.reset(side, payload.appearanceStartedAtNanos)
                                    chargedImpactWatch.appearanceBoundary(side, payload.appearanceStartedAtNanos)
                                    departureChecks.reset(side, payload.appearanceStartedAtNanos)
                                    recipientWatch.reset(payload.appearanceStartedAtNanos)
                                    true
                                }
                            }
                            if (changed) tracker.appearanceBoundary(side, payload.appearanceStartedAtNanos)
                        }
                        is SpeciesCheckMeasured -> if (payload.status == "OPENED" &&
                            payload.reason in setOf("EMPTY_HP", "SWITCH", "FAINT")) {
                            synchronized(stateLock) {
                                terminalWatch.reset(payload.side, article.monotonicTimeNanos)
                                completeTrack.reset(payload.side, article.monotonicTimeNanos)
                                chargedImpactWatch.reset(payload.side, article.monotonicTimeNanos)
                                departureChecks.reset(payload.side, payload.triggerMonotonicNanos)
                                recipientWatch.reset(payload.triggerMonotonicNanos)
                                lastSpeciesNames.remove(payload.side)
                            }
                            tracker.appearanceBoundary(payload.side, payload.triggerMonotonicNanos)
                            match.hpObservationDiagnostics.resetContinuity(payload.side)
                        }
                        is ActivePokemonFaintedWitnessed -> {
                            val changed = synchronized(stateLock) {
                                val current = lastSpeciesNames[payload.side]
                                val oldSpecies = payload.speciesName != null && current != null &&
                                    !payload.speciesName.equals(current, ignoreCase = true)
                                val oldAppearance = article.monotonicTimeNanos?.let {
                                    it < (lastAppearanceAt[payload.side] ?: Long.MIN_VALUE)
                                } == true
                                if (oldSpecies || oldAppearance) false else {
                                    terminalWatch.reset(payload.side, article.monotonicTimeNanos)
                                    completeTrack.reset(payload.side, article.monotonicTimeNanos)
                                    chargedImpactWatch.reset(payload.side, article.monotonicTimeNanos)
                                    article.monotonicTimeNanos?.let {
                                        departureChecks.reset(payload.side, it)
                                        recipientWatch.reset(it)
                                    }
                                    true
                                }
                            }
                            if (changed) tracker.appearanceBoundary(payload.side, article.monotonicTimeNanos ?: System.nanoTime())
                        }
                        is MatchEnded -> {
                            synchronized(stateLock) {
                                ended = true
                                terminalWatch.resetAll(article.monotonicTimeNanos)
                                completeTrack.resetAll(article.monotonicTimeNanos)
                                chargedImpactWatch.resetAll(article.monotonicTimeNanos)
                                departureChecks.stop(article.monotonicTimeNanos ?: System.nanoTime())
                                recipientWatch.reset(article.monotonicTimeNanos ?: System.nanoTime())
                            }
                            terminalSummary(match, "MATCH_END")
                        }
                        GetReadyWitnessed, AttackIncoming -> {
                            val at = article.monotonicTimeNanos ?: return@collect
                            synchronized(stateLock) {
                                departureChecks.prepare(at)
                                if (!ended && isEnabled()) recipientWatch.prepare(at)
                                if (!ended && isEnabled()) chargedImpactWatch.prepare(
                                    if (payload == GetReadyWitnessed) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER,
                                    at, article.id.value)
                            }
                        }
                        ChargeMoveUsedAnnounced -> article.monotonicTimeNanos?.let { at ->
                            synchronized(stateLock) {
                                departureChecks.impact(at)
                                if (!ended && isEnabled()) recipientWatch.impact(at)
                                if (!ended && isEnabled()) chargedImpactWatch.impact(at, article.id.value)
                            }
                        }
                        is RawTestimony -> if (isAcceptedPlayerQteProgressCue(article)) {
                            article.monotonicTimeNanos?.let { at ->
                                synchronized(stateLock) {
                                    if (!ended && isEnabled()) chargedImpactWatch.prepareFromPlayerQte(at, article.id.value)
                                }
                            }
                        }
                        ObservationRestartedByUser -> article.monotonicTimeNanos?.let { at ->
                            synchronized(stateLock) {
                                departureChecks.begin(at); recipientWatch.reset(at); chargedImpactWatch.resetAll(at); completeTrack.resetAll(at)
                            }
                        }
                        ScreenIgnoredByUser, ObservationSessionStoppedByUser -> article.monotonicTimeNanos?.let { at ->
                            synchronized(stateLock) {
                                departureChecks.stop(at); recipientWatch.reset(at); chargedImpactWatch.resetAll(at); completeTrack.resetAll(at)
                            }
                        }
                    }
                }
            }
            if (input.supportsIndependentFrameCollectors) witnessScope.launch {
                // Independent SharedFlow subscriber, with its own bounded state.
                // It never enters the full locator's monitor or retains frames.
                input.supplyFrames { checkTerminalFrame(match, it) }
            }
            witnessScope.launch {
                try {
                    // One acquisition collector owns the shared two-side locator.
                    // The separate terminal reader never mutates that locator.
                    input.supplyFrames { frame ->
                        val receivedAtNanos = System.nanoTime()
                        val enabled = isEnabled()
                        match.hpObservationDiagnostics.onFrameReceived(
                            frame.capturedAtMonotonicTimeNanos, receivedAtNanos, frame.publication, enabled,
                        )
                        if (lastEnabled != enabled) availability(match, enabled, frame.capturedAtWallTimeMillis)
                        if (!enabled) {
                            synchronized(stateLock) { departureChecks.resetAll(frame.capturedAtMonotonicTimeNanos); recipientWatch.reset(frame.capturedAtMonotonicTimeNanos) }
                            if (!input.supportsIndependentFrameCollectors) checkTerminalFrame(match, frame)
                            match.observeForecastBattlefield(frame.capturedAtMonotonicTimeNanos, false)
                            return@supplyFrames
                        }
                        if (!input.supportsIndependentFrameCollectors) checkTerminalFrame(match, frame)
                        val (generations, referenceEpochs) = synchronized(stateLock) {
                            if (ended) return@supplyFrames
                            ActivePokemonSide.entries.associateWith(terminalWatch::snapshotGeneration) to
                                ActivePokemonSide.entries.associateWith(chargedImpactWatch::snapshotReferenceEpoch)
                        }
                        val completeGenerations = ActivePokemonSide.entries.associateWith(completeTrack::generation)
                        val locateStartedAtNanos = System.nanoTime()
                        val located = tracker.measureFrameWithDiagnostics(frame)
                        match.hpObservationDiagnostics.onLocationCompleted(
                            frame.capturedAtMonotonicTimeNanos, locateStartedAtNanos, System.nanoTime(),
                            located.outcomes.mapValues { it.value.name },
                            located.timings,
                        )
                        // Reuse this normal locator result; the hint performs no
                        // additional geometry, pixel extraction, OCR, or testimony.
                        synchronized(stateLock) {
                            if (!ended && isEnabled()) departureChecks.observe(frame.bitmap.width, frame.bitmap.height,
                                frame.capturedAtMonotonicTimeNanos, located) { bounds ->
                                frame.canObserve(bounds) && SpeciesCaptureVisibility.canRead(AnchorRegion(
                                    bounds.left.toFloat() / frame.bitmap.width, bounds.top.toFloat() / frame.bitmap.height,
                                    (bounds.right - bounds.left).toFloat() / frame.bitmap.width,
                                    (bounds.bottom - bounds.top).toFloat() / frame.bitmap.height), frame.overlayRegion)
                            }.forEach { onDepartureCheck(it, frame.capturedAtMonotonicTimeNanos) }
                        }
                        val measurements = located.measurements
                        if (recipientWatch.observe(frame.bitmap.width, frame.bitmap.height,
                            frame.capturedAtMonotonicTimeNanos, measurements)) {
                            onBattlefieldReturn(frame.capturedAtMonotonicTimeNanos)
                        }
                        match.observeForecastBattlefield(frame.capturedAtMonotonicTimeNanos,
                            measurements.keys.containsAll(ActivePokemonSide.entries))
                        measurements.forEach { (side, measurement) ->
                            val completeBounds = TrackedTerminalHpBarMeasurer.searchBounds(frame.bitmap.width, frame.bitmap.height, measurement)
                            val complete = if (completeBounds != null && frame.canObserve(completeBounds))
                                measureCompleteFrame(frame, measurement) else null
                            if (complete != null) recordComplete(match, frame, side, complete, completeGenerations.getValue(side),
                                chargedImpactWatch.cameraCueIds(side, measurement, frame.capturedAtMonotonicTimeNanos).orEmpty())
                            synchronized(stateLock) {
                                if (ended || !isEnabled()) return@forEach
                                // A source-older identity may arrive during the locator's
                                // scan. Keep its newer measured geometry for charged HP,
                                // while ordinary publication still obeys terminal ownership.
                                chargedImpactWatch.seedIfCurrent(side, measurement, frame.bitmap.width, frame.bitmap.height,
                                    frame.capturedAtMonotonicTimeNanos, referenceEpochs.getValue(side))
                                if (!terminalWatch.isCurrentGeneration(side, generations.getValue(side))) return@forEach
                                terminalWatch.seed(side, measurement, frame.bitmap.width, frame.bitmap.height,
                                    frame.capturedAtMonotonicTimeNanos)
                                // Freshness limits the next terminal probe, not
                                // preservation of these actually captured pixels.
                                if (!terminalWatch.canPublishOrdinary(side, frame.capturedAtMonotonicTimeNanos)) return@forEach
                                publishMeasurement(match, frame, side, measurement,
                                    terminalWatch.snapshotGeneration(side), false)
                            }
                        }
                    }
                } finally {
                    if (lastEnabled == true) availability(match, false, System.currentTimeMillis())
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.let { availability(it, false, System.currentTimeMillis()) }
        synchronized(stateLock) {
            ended = true
            earlyPreparationEpoch++
            terminalWatch.resetAll()
            completeTrack.resetAll()
            chargedImpactWatch.resetAll()
            departureChecks.stop(System.nanoTime())
            recipientWatch.reset(System.nanoTime())
        }
        detachEarlyPreparation?.invoke()
        detachEarlyPreparation = null
        activeMatch?.publishHpObservationDiagnostics(HpDiagnosticSummaryReason.SESSION_STOP)
        activeMatch?.let { terminalSummary(it, "SESSION_STOP") }
        activeMatch = null
        scope?.cancel("Live active HP bars witness stopped")
        scope = null
        synchronized(stateLock) {
            ActivePokemonSide.entries.forEach(tracker::reset)
            lastSpeciesNames.clear()
            lastSpeciesAt.clear()
            lastAppearanceAt.clear()
        }
        lastEnabled = false
    }
}

/** Measures border cadence from the live crop without retaining a PNG per frame. */
internal class LiveActiveHpBarBorderCadenceWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedBarSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarBorderPulseDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedBarSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    resetForNextCombatant()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedBarSide && payload.status == "OPENED" && payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                resetForNextCombatant()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        if (!sample.fastMoveEvidenceAllowed) {
                            synchronized(stateLock) { detector.reset() }
                            return@collect
                        }
                        val appearance = sample.appearance ?: return@collect
                        val cadence = synchronized(stateLock) {
                            detector.accept(
                                "${observerId.id}:${sample.capturedAtMonotonicTimeNanos}",
                                sample.capturedAtMonotonicTimeNanos,
                                appearance
                            )
                        } ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActiveHpBarBorderCadenceMeasured(
                                damagedBarSide = damagedBarSide,
                                intervalNanos = cadence.intervalNanos,
                                peakColorDistance = cadence.colorDistanceAtOnset,
                                sampleCount = cadence.baselineSampleCount,
                                peakOrangeFraction = cadence.orangeFractionAtOnset,
                                baselineWhiteFraction = cadence.whiteFractionBeforePulse
                            ),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = cadence.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP border cadence witness stopped")
        scope = null
        synchronized(stateLock) { resetForNextCombatant(); lastSpeciesName = null }
        lastEnabled = false
    }

    private fun resetForNextCombatant() = detector.reset()

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderCadenceWitness(
                frameHub,
                ActivePokemonSide.PLAYER,
                ObserverId(BattleWitnessContracts.playerActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Player Active HP Bar Border Cadence"
            )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderCadenceWitness(
                frameHub,
                ActivePokemonSide.OPPONENT,
                ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Opponent Active HP Bar Border Cadence"
            )
    }
}

/** Emits one border-pulse article for each live rising edge. */
internal class LiveActiveHpBarBorderPulseWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedBarSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarBorderPulseDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedBarSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    resetForNextCombatant()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedBarSide && payload.status == "OPENED" && payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                resetForNextCombatant()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        if (!sample.fastMoveEvidenceAllowed) {
                            synchronized(stateLock) { detector.reset() }
                            return@collect
                        }
                        val appearance = sample.appearance ?: return@collect
                        val transition = synchronized(stateLock) {
                            detector.acceptTransition(
                                "${observerId.id}:${sample.capturedAtMonotonicTimeNanos}",
                                sample.capturedAtMonotonicTimeNanos,
                                appearance
                            )
                        } ?: return@collect
                        val pulse = transition.pulse
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = HpBarBorderPulse(
                                barSide = damagedBarSide,
                                status = transition.status,
                                peakColorDistance = pulse?.colorDistanceAtOnset,
                                sampleCount = pulse?.baselineSampleCount,
                                peakOrangeFraction = pulse?.orangeFractionAtOnset,
                                baselineWhiteFraction = pulse?.whiteFractionBeforePulse
                            ),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = pulse?.confidence ?: 1f,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP border pulse witness stopped")
        scope = null
        synchronized(stateLock) { resetForNextCombatant(); lastSpeciesName = null }
        lastEnabled = false
    }

    private fun resetForNextCombatant() = detector.reset()

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderPulseWitness(
                frameHub,
                ActivePokemonSide.PLAYER,
                ObserverId(BattleWitnessContracts.playerActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Player Active HP Bar Border Pulse"
            )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderPulseWitness(
                frameHub,
                ActivePokemonSide.OPPONENT,
                ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Opponent Active HP Bar Border Pulse"
            )
    }
}

internal data class ActiveHpBarDamageTickMeasurement(
    val beforeFraction: Float,
    val afterFraction: Float,
    val lostFraction: Float,
    val confidence: Float,
    val observedAtNanos: Long,
)

/** Measures one persistent HP-fill decrease without naming its cause. */
internal class ActiveHpBarDamageTickDetector {
    /** Baseline-only handoffs cannot emit damage, even when replacing an older bar. */
    @Synchronized
    fun accept(sample: LiveActiveHpBarSample): ActiveHpBarDamageTickMeasurement? {
        if (!sample.fastMoveEvidenceAllowed || sample.baselineOnly) {
            reset()
            if (sample.baselineOnly) accept(sample.measurement, sample.capturedAtMonotonicTimeNanos, sample.fillColor)
            return null
        }
        return accept(sample.measurement, sample.capturedAtMonotonicTimeNanos, sample.fillColor)
    }

    private var geometryAnchor: ActiveHpBarMeasurement? = null
    private var lastGeometryAt: Long? = null

    /** Reacquire a moved bar without calling the unseen change one attack. */
    @Synchronized
    fun accept(
        bar: ActiveHpBarMeasurement, at: Long, fillColor: HpBarFillColor? = null,
    ): ActiveHpBarDamageTickMeasurement? {
        if (!bar.filledFraction.isFinite() || bar.filledFraction !in 0f..1f) return null
        if (lastGeometryAt?.let { at <= it } == true) return null
        lastGeometryAt = at
        val anchor = geometryAnchor
        val relocated = anchor != null && (
            kotlin.math.abs(bar.centerX - anchor.centerX) > max(4f, anchor.width * .03f) ||
            kotlin.math.abs(bar.width - anchor.width) > max(4f, anchor.width * .10f) ||
            kotlin.math.abs(bar.top - anchor.top) > max((anchor.bottom - anchor.top) * 3f, anchor.width * .20f)
        )
        if (anchor == null) geometryAnchor = bar
        if (relocated) {
            geometryAnchor = bar
            // Reset only at the discontinuity. A later drop at the new position
            // still goes through normal HP settling; do not absorb it into a
            // quarter-second rolling baseline while the player is attacking.
            reacquire(bar.filledFraction, at, bar)
            return null
        }
        return acceptSample(bar.filledFraction, bar.confidence, at, bar.width, bar,
            verifiedEmpty = fillColor == HpBarFillColor.EMPTY)
    }

    private var baselineFraction: Float? = null
    private var baselineMeasurement: ActiveHpBarMeasurement? = null
    private var baselineSupportedAtNanos: Long? = null
    private var baselineSupportCount = 0
    private var pendingAfterFraction: Float? = null
    private var pendingChangedAtNanos: Long? = null
    private var pendingConfidence = 0f
    private var pendingMeasurement: ActiveHpBarMeasurement? = null
    private var pendingSmallLoss = false
    private var pendingSampleCount = 0
    private var lastSampleAtNanos: Long? = null

    @Synchronized
    fun reset() {
        geometryAnchor = null
        lastGeometryAt = null
        baselineFraction = null
        baselineMeasurement = null
        baselineSupportedAtNanos = null
        baselineSupportCount = 0
        clearPending()
        lastSampleAtNanos = null
    }

    @Synchronized
    fun accept(
        filledFraction: Float,
        measurementConfidence: Float,
        monotonicTimeNanos: Long,
        measuredBarWidth: Int? = null,
    ): ActiveHpBarDamageTickMeasurement? = acceptSample(
        filledFraction, measurementConfidence, monotonicTimeNanos, measuredBarWidth, null
    )

    private fun acceptSample(
        filledFraction: Float,
        measurementConfidence: Float,
        monotonicTimeNanos: Long,
        measuredBarWidth: Int?,
        measurement: ActiveHpBarMeasurement?,
        verifiedEmpty: Boolean = false,
    ): ActiveHpBarDamageTickMeasurement? {
        if (!filledFraction.isFinite() || filledFraction !in 0f..1f) return null
        val current = filledFraction
        val lastAt = lastSampleAtNanos
        if (lastAt != null && monotonicTimeNanos <= lastAt) return null
        lastSampleAtNanos = monotonicTimeNanos
        if (lastAt != null && monotonicTimeNanos - lastAt > MAX_SAMPLE_GAP_NANOS) {
            // A gap may contain many fast/charged attacks or a camera cut.
            // Reacquire HP without calling the whole unseen loss one new hit.
            reacquire(current, monotonicTimeNanos, measurement)
            return null
        }
        val baseline = baselineFraction
        if (baseline == null) {
            reacquire(current, monotonicTimeNanos, measurement)
            return null
        }
        // A terminal outline can vanish on the next frame. The separate pixel
        // verifier already required a complete outline and clear dark interior;
        // do not demand a second empty frame as well. Restrict this shortcut to
        // a fresh, settled red-range baseline with unchanged horizontal caps.
        // Camera samples are excluded by the live witness before reaching here.
        // Unverified scalar zeroes retain the ordinary settling requirement.
        val previousBar = baselineMeasurement
        if (verifiedEmpty && current == 0f && baseline > 0f && baseline <= .20f &&
            pendingAfterFraction == null && measurement != null && previousBar != null &&
            lastAt != null && monotonicTimeNanos - lastAt in 1..VERIFIED_EMPTY_MAX_GAP_NANOS &&
            baselineSupportCount >= 2 && baselineSupportedAtNanos?.let {
                monotonicTimeNanos - it in 1..VERIFIED_EMPTY_MAX_GAP_NANOS
            } == true &&
            previousBar.left == measurement.left && previousBar.right == measurement.right &&
            abs((previousBar.bottom - previousBar.top) - (measurement.bottom - measurement.top)) <= 2 &&
            measurement.confidence >= VERIFIED_EMPTY_MIN_CONFIDENCE &&
            previousBar.confidence >= VERIFIED_EMPTY_MIN_CONFIDENCE) {
            beginPending(0f, minOf(previousBar.confidence, measurement.confidence),
                monotonicTimeNanos, measurement)
            val tick = settled(baseline, 0f, monotonicTimeNanos)
            clearPending()
            return tick
        }
        // The same active combatant cannot heal. An orange hit frame or a
        // tracking jump must not reset its baseline and manufacture another hit
        // when the real fill becomes visible again. Entry/species cues reset it.
        // The locator searches three pixels around an end cap, and antialiasing
        // can shift the chosen edge within that range. Such a change
        // was counted as an attack on a full, undamaged Abomasnow in the paired
        // idle test. Express this uncertainty in source pixels, not a fixed HP
        // percentage. Border pulses remain independent evidence for tiny hits.
        val innerWidth = measuredBarWidth?.takeIf { it > 4 }?.let { width ->
            width - 2 * max(2, width / 80)
        }
        if (measurement != null && innerWidth != null &&
            kotlin.math.abs(baseline - current) * innerWidth < .5f) {
            // An unchanged fill can establish the current end caps after entry
            // geometry settles by a pixel. Keep the HP baseline itself: a real
            // rebound or a lower fill must never refresh it into another hit.
            baselineMeasurement = measurement
            baselineSupportedAtNanos = monotonicTimeNanos
            baselineSupportCount = (baselineSupportCount + 1).coerceAtMost(2)
        }
        val minimumLoss = innerWidth?.let {
            max(MIN_DAMAGE_FRACTION, EDGE_UNCERTAINTY_PIXELS / it)
        } ?: MIN_DAMAGE_FRACTION
        if (baseline - current < minimumLoss) {
            // The paired Fire Fang recording contains real, repeated three-pixel
            // plateaus. The scalar uncertainty floor alone swallowed five of
            // them. Only relax it when both end caps still occupy the baseline's
            // exact X positions, so a changed denominator cannot mimic HP loss.
            // Require a longer three-sample plateau; an isolated low edge is not
            // an attack. Y may move with ordinary Pokémon bobbing.
            val stableBounds = measurement != null && baselineMeasurement?.let {
                it.left == measurement.left && it.right == measurement.right
            } == true
            val smallLoss = stableBounds && innerWidth != null &&
                baseline - current >= MIN_DAMAGE_FRACTION &&
                (baseline - current) * innerWidth >= PERSISTENT_SMALL_LOSS_PIXELS
            if (!smallLoss) {
                clearPending()
                return null
            }
            val pending = pendingAfterFraction
            val changedAt = pendingChangedAtNanos
            if (!pendingSmallLoss || pending == null || changedAt == null ||
                kotlin.math.abs(current - pending) > UPDATE_EPSILON) {
                beginPending(current, measurementConfidence, monotonicTimeNanos, measurement, smallLoss = true)
                return null
            }
            pendingSampleCount++
            pendingConfidence = minOf(pendingConfidence, measurementConfidence)
            if (pendingSampleCount < SMALL_LOSS_SAMPLES ||
                monotonicTimeNanos - changedAt < SMALL_LOSS_SETTLE_NANOS) return null
            val tick = settled(baseline, minOf(pending, current), changedAt)
            clearPending()
            return tick
        }

        // A deeper loss must not retroactively certify a small plateau that
        // failed its independent persistence requirement.
        if (pendingSmallLoss) clearPending()
        val pending = pendingAfterFraction
        val changedAt = pendingChangedAtNanos
        if (pending != null && changedAt != null && current < pending - UPDATE_EPSILON &&
            monotonicTimeNanos - changedAt >= SEPARATE_LOSS_NANOS) {
            // A later, lower level confirms the earlier decrease even when
            // capture missed its identical plateau frames. Keep both steps,
            // instead of extending the pending loss until the Pokémon is dead.
            val tick = settled(baseline, pending, changedAt)
            beginPending(current, measurementConfidence, monotonicTimeNanos, measurement)
            return tick
        }
        if (pending == null || kotlin.math.abs(current - pending) > UPDATE_EPSILON) {
            beginPending(current, measurementConfidence, monotonicTimeNanos, measurement)
            return null
        }
        if (changedAt == null) return null
        if (monotonicTimeNanos - changedAt < SETTLE_NANOS) return null

        val tick = settled(baseline, minOf(pending, current), changedAt)
        clearPending()
        return tick
    }

    private fun clearPending() {
        pendingAfterFraction = null
        pendingChangedAtNanos = null
        pendingConfidence = 0f
        pendingMeasurement = null
        pendingSmallLoss = false
        pendingSampleCount = 0
    }

    private fun beginPending(
        after: Float,
        confidence: Float,
        at: Long,
        measurement: ActiveHpBarMeasurement?,
        smallLoss: Boolean = false,
    ) {
        pendingAfterFraction = after
        pendingChangedAtNanos = at
        pendingConfidence = confidence
        pendingMeasurement = measurement
        pendingSmallLoss = smallLoss
        pendingSampleCount = 1
    }

    private fun reacquire(fraction: Float, at: Long, measurement: ActiveHpBarMeasurement?) {
        baselineFraction = fraction
        baselineMeasurement = measurement
        baselineSupportedAtNanos = at.takeIf { measurement != null }
        baselineSupportCount = if (measurement != null) 1 else 0
        clearPending()
        lastSampleAtNanos = at
    }

    private fun settled(before: Float, after: Float, observedAt: Long): ActiveHpBarDamageTickMeasurement {
        val lost = (before - after).coerceIn(0f, 1f)
        baselineFraction = after
        baselineMeasurement = pendingMeasurement
        baselineSupportedAtNanos = observedAt.takeIf { pendingMeasurement != null }
        baselineSupportCount = if (pendingMeasurement != null) 1 else 0
        return ActiveHpBarDamageTickMeasurement(
            beforeFraction = before,
            afterFraction = after,
            lostFraction = lost,
            confidence = (pendingConfidence * (0.75f + lost.coerceAtMost(0.25f))).coerceIn(0f, 0.99f),
            observedAtNanos = observedAt,
        )
    }

    private companion object {
        const val MIN_DAMAGE_FRACTION = 0.006f
        const val EDGE_UNCERTAINTY_PIXELS = 3.5f
        // Half-pixel boundary tolerates fraction rounding for a three-pixel edge.
        const val PERSISTENT_SMALL_LOSS_PIXELS = 2.5f
        const val SMALL_LOSS_SAMPLES = 3
        const val SMALL_LOSS_SETTLE_NANOS = 350_000_000L
        // One fill pixel on the approximately 190 px captured bar is 0.0053.
        const val UPDATE_EPSILON = 0.006f
        const val SETTLE_NANOS = 90_000_000L
        const val SEPARATE_LOSS_NANOS = 350_000_000L
        const val MAX_SAMPLE_GAP_NANOS = 1_250_000_000L
        const val VERIFIED_EMPTY_MAX_GAP_NANOS = 500_000_000L
        const val VERIFIED_EMPTY_MIN_CONFIDENCE = .80f
    }
}

/** Emits one article per settled active-HP loss. */
internal class LiveActiveHpBarDamageTickWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarDamageTickDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    detector.reset()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedSide && payload.status == "OPENED" &&
                            payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                detector.reset()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        val tick = synchronized(stateLock) { detector.accept(sample) } ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActiveHpBarDamageTickMeasured(
                                damagedSide = damagedSide,
                                beforeFraction = tick.beforeFraction,
                                afterFraction = tick.afterFraction,
                                lostFraction = tick.lostFraction
                            ),
                            timestamp = sample.capturedAtWallTimeMillis -
                                (sample.capturedAtMonotonicTimeNanos - tick.observedAtNanos) / 1_000_000L,
                            confidence = tick.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = tick.observedAtNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP damage tick witness stopped")
        scope = null
        synchronized(stateLock) {
            detector.reset()
            lastSpeciesName = null
        }
        lastEnabled = false
    }

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) = LiveActiveHpBarDamageTickWitness(
            frameHub,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerActiveHpBarDamageTick.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Active HP Bar Damage Tick"
        )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) = LiveActiveHpBarDamageTickWitness(
            frameHub,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentActiveHpBarDamageTick.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Active HP Bar Damage Tick"
        )
    }
}

/** Samples the visual territory surrounding the recipient without retaining a frame. */
internal object FastMoveRecipientVisualSampler {
    private const val GRID_SIZE = 24

    fun bounds(width: Int, height: Int, bar: ActiveHpBarMeasurement): BattleCropBounds? {
        val barWidth = bar.width.coerceAtLeast(1)
        val barHeight = (bar.bottom - bar.top).coerceAtLeast(1)
        val left = (bar.left - barWidth / 2).coerceAtLeast(0)
        val right = (bar.right + barWidth / 2).coerceAtMost(width)
        val top = (bar.top - barWidth / 4).coerceAtLeast(0)
        val bottom = (bar.bottom + max(barWidth * 3 / 2, barHeight * 12)).coerceAtMost(height)
        return if (right > left && bottom > top) BattleCropBounds(left, top, right, bottom) else null
    }

    fun sample(bitmap: Bitmap, bar: ActiveHpBarMeasurement): FastMoveRecipientVisualSample? {
        val region = bounds(bitmap.width, bitmap.height, bar) ?: return null
        val pixels = IntArray(GRID_SIZE * GRID_SIZE)
        for (gridY in 0 until GRID_SIZE) {
            val y = (region.top + ((gridY + .5f) * (region.bottom - region.top) / GRID_SIZE).toInt())
                .coerceIn(0, bitmap.height - 1)
            for (gridX in 0 until GRID_SIZE) {
                val x = (region.left + ((gridX + .5f) * (region.right - region.left) / GRID_SIZE).toInt())
                    .coerceIn(0, bitmap.width - 1)
                pixels[gridY * GRID_SIZE + gridX] = bitmap.getPixel(x, y)
            }
        }
        return FastMoveRecipientVisualSample(pixels, region)
    }
}

internal data class FastMoveRecipientVisualArtifactMeasurement(
    val changedPixelFraction: Float,
    val meanColorDistance: Float,
    val meanRed: Float,
    val meanGreen: Float,
    val meanBlue: Float,
    val centroidX: Float,
    val centroidY: Float,
    val changedSampleCount: Int,
    val confidence: Float,
    val observedAtMonotonicTimeNanos: Long
)

/**
 * Detects the rising edge of a transient color change in the recipient area.
 * It measures the artifact without claiming a move name or elemental type.
 */
internal class FastMoveRecipientVisualArtifactDetector {
    private data class Peak(
        val measurement: FastMoveRecipientVisualArtifactMeasurement,
        val score: Float
    )

    private var baseline: IntArray? = null
    private var transientStartedAtNanos: Long? = null
    private var peak: Peak? = null
    private var lastEmissionNanos: Long? = null
    private var lastSampleNanos: Long? = null
    private var lastBounds: BattleCropBounds? = null

    @Synchronized
    fun reset() {
        baseline = null
        transientStartedAtNanos = null
        peak = null
        lastEmissionNanos = null
        lastSampleNanos = null
        lastBounds = null
    }

    @Synchronized
    fun accept(sample: FastMoveRecipientVisualSample, monotonicTimeNanos: Long): FastMoveRecipientVisualArtifactMeasurement? {
        val previousAt = lastSampleNanos
        if (previousAt != null && monotonicTimeNanos <= previousAt) return null
        if (previousAt != null && monotonicTimeNanos - previousAt > MAX_SAMPLE_GAP_NANOS) reset()
        val beforeBounds = lastBounds
        val bounds = sample.bounds
        if (beforeBounds != null && bounds != null) {
            val tolerance = max(4, (beforeBounds.right - beforeBounds.left) / 12)
            if (abs(bounds.left - beforeBounds.left) > tolerance || abs(bounds.right - beforeBounds.right) > tolerance ||
                abs(bounds.top - beforeBounds.top) > tolerance || abs(bounds.bottom - beforeBounds.bottom) > tolerance) reset()
        }
        lastSampleNanos = monotonicTimeNanos
        lastBounds = bounds
        val earlier = baseline
        if (earlier == null || earlier.size != sample.pixels.size) {
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
            return null
        }

        if (transientStartedAtNanos?.let { monotonicTimeNanos - it >= MAX_TRANSIENT_NANOS } == true) {
            // Even a receding frame arriving at the limit must not promote a
            // long scene change into a completed hit.
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
            return null
        }

        var changed = 0
        var distanceTotal = 0f
        var redTotal = 0f
        var greenTotal = 0f
        var blueTotal = 0f
        var xTotal = 0f
        var yTotal = 0f
        val gridSize = kotlin.math.sqrt(sample.pixels.size.toFloat()).toInt().coerceAtLeast(1)
        sample.pixels.indices.forEach { index ->
            val before = earlier[index]
            val after = sample.pixels[index]
            val red = after ushr 16 and 0xff
            val green = after ushr 8 and 0xff
            val blue = after and 0xff
            val colorDistance = (
                abs(red - (before ushr 16 and 0xff)) +
                    abs(green - (before ushr 8 and 0xff)) +
                    abs(blue - (before and 0xff))
                ) / (255f * 3f)
            if (colorDistance < CHANGED_COLOR_DISTANCE) return@forEach
            changed++
            distanceTotal += colorDistance
            redTotal += red / 255f
            greenTotal += green / 255f
            blueTotal += blue / 255f
            xTotal += (index % gridSize + 0.5f) / gridSize
            yTotal += (index / gridSize + 0.5f) / gridSize
        }
        val fraction = changed.toFloat() / sample.pixels.size
        val meanDistance = if (changed == 0) 0f else distanceTotal / changed
        val score = fraction * meanDistance
        val currentPeak = peak

        val receded = currentPeak != null && (
            fraction <= REARM_FRACTION ||
                meanDistance < MIN_MEAN_COLOR_DISTANCE ||
                score <= currentPeak.score * PEAK_RELEASE_RATIO
            )
        if (receded) {
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
            val observed = currentPeak.measurement
            if (lastEmissionNanos?.let {
                    observed.observedAtMonotonicTimeNanos - it < MIN_EMISSION_INTERVAL_NANOS
                } == true
            ) return null
            lastEmissionNanos = observed.observedAtMonotonicTimeNanos
            return observed
        }

        if (fraction >= ONSET_FRACTION && meanDistance >= MIN_MEAN_COLOR_DISTANCE) {
            if (transientStartedAtNanos == null) transientStartedAtNanos = monotonicTimeNanos
            val measured = FastMoveRecipientVisualArtifactMeasurement(
                changedPixelFraction = fraction,
                meanColorDistance = meanDistance,
                meanRed = redTotal / changed,
                meanGreen = greenTotal / changed,
                meanBlue = blueTotal / changed,
                centroidX = xTotal / changed,
                centroidY = yTotal / changed,
                changedSampleCount = changed,
                confidence = ((fraction / 0.30f) * 0.55f + (meanDistance / 0.45f) * 0.45f).coerceIn(0.20f, 1f),
                observedAtMonotonicTimeNanos = monotonicTimeNanos
            )
            if (currentPeak == null || score > currentPeak.score) peak = Peak(measured, score)
        } else if (currentPeak == null) {
            // A quiet recipient frame is a better baseline for the next transient.
            baseline = sample.pixels.copyOf()
        }

        return null
    }

    private companion object {
        const val CHANGED_COLOR_DISTANCE = 0.12f
        const val ONSET_FRACTION = 0.08f
        const val REARM_FRACTION = 0.035f
        const val MIN_MEAN_COLOR_DISTANCE = 0.16f
        const val PEAK_RELEASE_RATIO = 0.55f
        const val MIN_EMISSION_INTERVAL_NANOS = 300_000_000L
        const val MAX_TRANSIENT_NANOS = 1_500_000_000L
        const val MAX_SAMPLE_GAP_NANOS = 500_000_000L
    }
}

/** Measures one signal: transient visual artifacts near the damaged Pokémon. */
internal class LiveFastMoveRecipientVisualArtifactWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastSpeciesName: String? = null
    private val detector = FastMoveRecipientVisualArtifactDetector()

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return@collect
                    if (species.side == damagedSide && species.speciesName != lastSpeciesName) {
                        detector.reset()
                        lastSpeciesName = species.speciesName
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    frameHub.visualSamples.collect { sample ->
                        if (sample.visual == null) {
                            detector.reset()
                            return@collect
                        }
                        val visual = sample.visual ?: return@collect
                        val measured = detector.accept(visual, sample.capturedAtMonotonicTimeNanos) ?: return@collect
                        val lagNanos = (sample.capturedAtMonotonicTimeNanos - measured.observedAtMonotonicTimeNanos)
                            .coerceAtLeast(0L)
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = FastMoveRecipientVisualArtifactMeasured(
                                damagedSide = damagedSide,
                                changedPixelFraction = measured.changedPixelFraction,
                                meanColorDistance = measured.meanColorDistance,
                                meanRed = measured.meanRed,
                                meanGreen = measured.meanGreen,
                                meanBlue = measured.meanBlue,
                                centroidX = measured.centroidX,
                                centroidY = measured.centroidY,
                                changedSampleCount = measured.changedSampleCount
                            ),
                            timestamp = sample.capturedAtWallTimeMillis - lagNanos / 1_000_000L,
                            confidence = measured.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = measured.observedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Fast move recipient visual artifact witness stopped")
        scope = null
        lastSpeciesName = null
        detector.reset()
    }

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) = LiveFastMoveRecipientVisualArtifactWitness(
            frameHub,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerFastMoveRecipientVisualArtifact.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Fast Move Recipient Visual Artifact"
        )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) = LiveFastMoveRecipientVisualArtifactWitness(
            frameHub,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentFastMoveRecipientVisualArtifact.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Fast Move Recipient Visual Artifact"
        )
    }
}

/** Measures one signal: elapsed monotonic time between recipient artifacts. */
internal class LiveFastMoveRecipientVisualCadenceWitness(
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var previousArticleId: String? = null
    private var previousMonotonicTimeNanos: Long? = null
    private var previousConfidence: Float? = null
    private var lastSpeciesName: String? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        resetCadence()
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    match.articles.collect { article ->
                        when (val payload = article.payload) {
                            is ActivePokemonSpeciesWitnessed -> if (
                                payload.side == damagedSide && payload.speciesName != lastSpeciesName
                            ) {
                                resetCadence()
                                lastSpeciesName = payload.speciesName
                            }
                            is SpeciesCheckMeasured -> if (
                                payload.side == damagedSide &&
                                payload.status == "OPENED" &&
                                payload.reason == "EMPTY_HP"
                            ) {
                                resetCadence()
                                lastSpeciesName = null
                            }
                            is FastMoveRecipientVisualArtifactMeasured -> if (payload.damagedSide == damagedSide) {
                                val currentNanos = article.monotonicTimeNanos ?: return@collect
                                val priorNanos = previousMonotonicTimeNanos
                                val priorId = previousArticleId
                                val priorConfidence = previousConfidence
                                previousMonotonicTimeNanos = currentNanos
                                previousArticleId = article.id.value
                                previousConfidence = article.confidence
                                if (priorNanos == null || priorId == null || currentNanos <= priorNanos) return@collect
                                match.custody.submitTestimony(
                                    sourceId = sourceId,
                                    payload = FastMoveRecipientVisualCadenceMeasured(
                                        damagedSide = damagedSide,
                                        intervalNanos = currentNanos - priorNanos
                                    ),
                                    timestamp = article.perceivedAt,
                                    confidence = listOfNotNull(priorConfidence, article.confidence).minOrNull(),
                                    evidenceReferences = listOf(priorId, article.id.value),
                                    monotonicTimeNanos = currentNanos
                                )
                            }
                        }
                    }
                } finally {
                    match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Fast move recipient visual cadence witness stopped")
        scope = null
        resetCadence()
        lastSpeciesName = null
    }

    private fun resetCadence() {
        previousArticleId = null
        previousMonotonicTimeNanos = null
        previousConfidence = null
    }

    companion object {
        fun player() = LiveFastMoveRecipientVisualCadenceWitness(
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerFastMoveRecipientVisualCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Fast Move Recipient Visual Cadence"
        )

        fun opponent() = LiveFastMoveRecipientVisualCadenceWitness(
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentFastMoveRecipientVisualCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Fast Move Recipient Visual Cadence"
        )
    }
}
