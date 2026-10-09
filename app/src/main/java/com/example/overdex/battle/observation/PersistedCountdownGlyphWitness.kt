package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Recognizes countdown glyphs only from previously preserved crop artifacts. */
class PersistedCountdownGlyphWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId(BattleWitnessContracts.countdownGlyph.witnessId, ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Countdown Glyph Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var frameIndex = 0
                var acceptedGo = false
                var lastOcrAtNanos = Long.MIN_VALUE
                val acceptedGlyphs = mutableSetOf<String>()
                // The archive keeps every crop. Live recognition must inspect
                // the newest countdown image rather than drain minutes of old frames.
                match.articles.filter { article ->
                    (article.payload as? CropCaptured)?.cropProvenance?.cropName ==
                        BattleCropContracts.countdownGlyph.cropName
                }.conflate().collect { article ->
                    if (acceptedGo) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != BattleCropContracts.countdownGlyph.cropName) return@collect
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    try {
                        val templateResult = CountdownGlyphMatcher.match(bitmap)
                        val templateGlyph = templateResult.candidate
                        val atNanos = article.monotonicTimeNanos ?: return@collect
                        // Template geometry handles 3/2/1 without placing every
                        // 30-fps frame into the general OCR lane. OCR is reserved
                        // for GO and throttled uncertain frames.
                        val shouldRunOcr = templateGlyph == null &&
                            (lastOcrAtNanos == Long.MIN_VALUE || atNanos - lastOcrAtNanos >= OCR_INTERVAL_NANOS)
                        val ocrGlyph = if (shouldRunOcr) {
                            lastOcrAtNanos = atNanos
                            SharedLatinTextRecognizer.readTextIfIdle(bitmap)
                                ?.uppercase()
                                ?.replace(Regex("[^A-Z0-9]"), "")
                                ?.takeIf { it == "GO" }
                        } else null
                        val glyph = ocrGlyph ?: templateGlyph ?: return@collect
                        if (!acceptedGlyphs.add(glyph)) return@collect
                        frameIndex++
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = CountdownGlyphWitnessed(
                                glyph = glyph,
                                similarity = if (ocrGlyph != null) 0f else templateResult.similarity,
                                frameIndex = frameIndex,
                                cropProvenance = crop.cropProvenance,
                                basis = if (ocrGlyph != null) {
                                    "COUNTDOWN_GLYPH_OCR_EXACT_AFTER_PROGRESSION"
                                } else {
                                    templateResult.basis
                                }
                            ),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                        )
                        if (glyph == "GO") acceptedGo = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e("CountdownGlyphWitness", "Glyph recognition failed for ${article.id.value}", error)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }

    private companion object {
        const val OCR_INTERVAL_NANOS = 150_000_000L
    }
}
