package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/** Shared OCR admission is the only lock; optional coordinates never queue ahead of identity. */
internal object PlayerControlTextRecognizer {
    suspend fun read(bitmap: Bitmap): String = requireNotNull(readScheduled(bitmap))

    suspend fun readScheduled(bitmap: Bitmap, onlyIfIdle: Boolean = false, isFresh: () -> Boolean = { true }): String? {
        if (!isFresh()) return null
        return readPrepared(bitmap) { enlarged ->
            if (onlyIfIdle) SharedLatinTextRecognizer.readTextIfIdle(enlarged)
            else SharedLatinTextRecognizer.readTextFresh(enlarged, isFresh)
        }
    }

    /** Also used inside an already admitted OCR turn; never acquire that turn twice. */
    suspend fun readPrepared(bitmap: Bitmap, reader: suspend (Bitmap) -> String?): String? {
        // A 20-pixel high published strip has small digits even after ML Kit's
        // minimum-size padding. Enlarge the in-memory input, never the calibration.
        val scale = maxOf(2f, 64f / bitmap.height)
        val enlarged = if (scale > 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        return try { reader(enlarged) }
        finally { if (enlarged !== bitmap) enlarged.recycle() }
    }
}

/**
 * Each instance reads one field: the pointer strip OR one button label. A
 * conflated crop channel keeps OCR off the capture callback and cannot retain a
 * backlog of full-screen bitmaps. No touch interception or HUD changes.
 */
internal class LivePlayerControlTextWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val artifactStore: CropArtifactStore,
    private val isEnabled: () -> Boolean,
    /** 0 = pointer coordinates; 1 = left or lone center; 2 = right. */
    private val slot: Int,
    private val effectiveness: Boolean = false,
    private val controlLayout: PlayerChargeControlLayout = PlayerChargeControlLayout(),
) : Observer {
    init { require(slot in 0..2 && (!effectiveness || slot != 0)) }
    override val observerId = ObserverId(if (slot == 0) "PLAYER_CHARGE_BUTTON_TOUCH_WITNESS"
        else if (effectiveness) "PLAYER_CHARGE_MOVE_EFFECTIVENESS_${slot}_WITNESS"
        else "PLAYER_CHARGE_MOVE_LABEL_${slot}_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = if (slot == 0) "Player Charge Button Touch" else if (effectiveness) "Charged Button Effectiveness $slot" else "Player Charge Move Label $slot"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var samples: Channel<Sample>? = null
    private var activeMatch: Match? = null
    private val source get() = SourceId(observerId.id)
    private data class Sample(val crop: ResolvedBattleCrop, val wall: Long, val at: Long,
        val displayWidth: Int?, val displayHeight: Int?, val singleCentered: Boolean)

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val channel = Channel<Sample>(Channel.CONFLATED, onUndeliveredElement = { it.crop.bitmap.recycle() })
        samples = channel
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { workers ->
            workers.launch {
                var lastAt = Long.MIN_VALUE
                var available: Boolean? = null
                try {
                    input.supplyFrames { frame ->
                        val at = frame.capturedAtMonotonicTimeNanos
                        val singleCentered = controlLayout.centerAt(at) != null
                        val crop = PlayerChargeControlLayout.textCrop(slot, effectiveness, singleCentered)
                        val enabled = isEnabled() && (slot != 0 ||
                            (frame.displayWidth != null && frame.displayHeight != null)) && crop != null
                        if (enabled != available) {
                            match.custody.submitAvailability(source, enabled, frame.capturedAtWallTimeMillis)
                            available = enabled
                        }
                        if (!enabled) return@supplyFrames
                        val interval = if (slot == 0) 50_000_000L else if (effectiveness) 1_000_000_000L else 500_000_000L
                        if (lastAt != Long.MIN_VALUE && at - lastAt < interval) return@supplyFrames
                        if (slot == 0) {
                            // Only full-display capture currently supplies these dimensions.
                            val actual = frame.bitmap.width.toDouble() / frame.bitmap.height
                            val display = frame.displayWidth!!.toDouble() / frame.displayHeight!!
                            if (kotlin.math.abs(actual / display - 1) > 0.01) return@supplyFrames
                        }
                        val resolved = crop?.resolve(calibration, frame) ?: return@supplyFrames
                        lastAt = at
                        if (channel.trySend(Sample(resolved, frame.capturedAtWallTimeMillis, at,
                                frame.displayWidth, frame.displayHeight, singleCentered)).isFailure) resolved.bitmap.recycle()
                    }
                } finally { match.custody.submitAvailability(source, false, System.currentTimeMillis()) }
            }
            workers.launch(Dispatchers.IO) {
                var lastPublished = Long.MIN_VALUE
                var lastPreserved = Long.MIN_VALUE
                var lastDiagnostic = Long.MIN_VALUE
                var previousText: String? = null
                var stableReading = StableLabelReading()
                var previousCropName: String? = null
                for (queued in channel) {
                    var sample = queued
                    try {
                        if (!isEnabled()) continue
                        var ink: LabelInk? = null
                        var reused: StableLabelReading.Reading? = null
                        val text = if (slot == 0) {
                            // Pointer diagnostics are optional; never queue them.
                            PlayerControlTextRecognizer.readScheduled(sample.crop.bitmap, onlyIfIdle = true)
                        } else SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { read ->
                            // Wait for a fair routine turn, then take the newest
                            // crop. Selecting before admission discarded almost
                            // every label during combat as stale, while idle-only
                            // effectiveness checks could starve for a whole match.
                            channel.tryReceive().getOrNull()?.let { newest ->
                                sample.crop.bitmap.recycle()
                                sample = newest
                            }
                            if (!isEnabled() || System.nanoTime() - sample.at !in 0..1_500_000_000L) {
                                return@withReadingTurn null
                            }
                            // A moved reading territory must never reuse the other
                            // control's OCR or suppress its first fresh label.
                            val cropName = sample.crop.provenance.cropName
                            if (previousCropName != cropName) {
                                stableReading = StableLabelReading()
                                previousText = null
                                previousCropName = cropName
                            }
                            val bitmap = sample.crop.bitmap
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            ink = LabelInk.fromPixels(bitmap.width, bitmap.height, pixels)
                            reused = stableReading.reuse(requireNotNull(ink), sample.at)
                            reused?.text ?: PlayerControlTextRecognizer.readPrepared(bitmap) { read(it).text }
                        }
                        if (text == null) continue
                        val payload: TestimonyPayload
                        if (slot == 0) {
                            val width = sample.displayWidth ?: continue
                            val height = sample.displayHeight ?: continue
                            val point = PointerCoordinateParser.parse(text, width, height)
                            val button = point?.let { PointerCoordinateParser.chargedButtonSlot(it,
                                width, height, calibration.trainerChargeMoveControlsRegion, sample.singleCentered) }
                            if (point == null || button == null) {
                                // Sparse diagnostics show absent/released/outside-control readings.
                                if (lastDiagnostic != Long.MIN_VALUE && sample.at - lastDiagnostic < 5_000_000_000L) continue
                                lastDiagnostic = sample.at
                                payload = RawTestimony(text)
                            } else {
                                if (lastPublished != Long.MIN_VALUE && sample.at - lastPublished < 100_000_000L) continue
                                payload = PlayerChargeButtonTouchWitnessed(button, point.x, point.y, width, height, text)
                                lastPublished = sample.at
                            }
                        } else {
                            // Repeat slowly to establish that the label was still visible near
                            // a contact. Blank reads invalidate visibility, not the stored moveset.
                            if (text == previousText && lastPublished != Long.MIN_VALUE &&
                                sample.at - lastPublished < 1_000_000_000L) continue
                            previousText = text
                            val basis = if (reused == null) "OCR" else "UNCHANGED_LETTER_PIXELS"
                            payload = if (effectiveness) {
                                // This is a button's matchup label, never an executed Fast Move.
                                val reading = FastMoveEffectivenessTextResolver.resolve(text)
                                    ?.takeIf { it.confidence >= .9f } ?: continue
                                PlayerChargedMoveEffectivenessObserved(slot, reading.effectiveness, text, basis)
                            } else PlayerChargeMoveLabelRead(slot, text, basis)
                            lastPublished = sample.at
                        }
                        var references = emptyList<String>()
                        if (slot != 0 || lastPreserved == Long.MIN_VALUE || sample.at - lastPreserved >= 1_000_000_000L) {
                            val artifact = artifactStore.preservePng(sample.crop.bitmap)
                            if (artifact != null) {
                                val accepted = match.custody.submitTestimony(SourceId("${observerId.id}_CAPTURE"),
                                    CropCaptured(artifact, sample.crop.provenance), sample.wall, null, emptyList(), sample.at)
                                val article = withTimeoutOrNull(2_000.milliseconds) {
                                    match.articles.first { it.sequenceNumber == accepted.sequenceNumber && it.payload is CropCaptured }
                                }
                                references = listOfNotNull(article?.id?.value)
                                lastPreserved = sample.at
                            }
                        }
                        if (reused != null && references.isEmpty()) continue
                        if (ink != null && reused == null) stableReading.remember(requireNotNull(ink), sample.at, text, references)
                        // Keep the original OCR basis as well as this fresh visibility measurement.
                        references = (references + reused?.references.orEmpty()).distinct()
                        match.custody.submitTestimony(source, payload, sample.wall, null, references, sample.at)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Log.w("PLAYER_CONTROL_TEXT", "Read failed for $name", error) }
                    finally { sample.crop.bitmap.recycle() }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        samples?.cancel()
        samples = null
        activeMatch?.custody?.submitAvailability(source, false, System.currentTimeMillis())
        activeMatch = null
    }
}
