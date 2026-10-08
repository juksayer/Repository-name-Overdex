package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.AudioCaptured
import com.example.overdex.battle.custody.FastMoveSoundMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Measures one short, visually-cued fast-move sound artifact. This witness does
 * not name a move: cadence plus reference knowledge remains the identity path,
 * while these durable acoustic measurements can corroborate it now and compare
 * against a future type/move sound catalog later.
 */
class PersistedFastMoveSoundWitness(private val root: File) : Observer {
    override val observerId = ObserverId("FAST_MOVE_SOUND_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Fast Move Sound Witness"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val audio = article.payload as? AudioCaptured ?: return@collect
                    if (audio.cueKind != BattleCryCueKind.FAST_MOVE_IMPACT.name) return@collect
                    val file = File(root, audio.artifact.relativePath)
                    val bytes = file.takeIf(File::isFile)?.readBytes() ?: return@collect
                    if (sha256(bytes) != audio.artifact.sha256) return@collect
                    val pcm = WavPcm16.decode(bytes) ?: return@collect
                    val measured = FastMoveSoundAnalyzer.measure(pcm)
                    val cueUse = article.evidenceReferences
                        ?.asSequence()
                        ?.mapNotNull { cueId ->
                            match.realityTimeline.getArticles().asReversed()
                                .firstOrNull { it.id.value == cueId }
                        }
                        ?.mapNotNull { it.payload as? FastMoveUseObserved }
                        ?.firstOrNull()
                    match.custody.submitTestimony(
                        sourceId = SourceId(observerId.id),
                        payload = measured.copy(
                            attackingSide = cueUse?.attackingSide,
                            soundOnsetMonotonicNanos = if (measured.audible) {
                                article.monotonicTimeNanos?.plus(requireNotNull(measured.onsetOffsetNanos))
                            } else null,
                        ),
                        timestamp = article.perceivedAt,
                        confidence = null,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Fast move sound witness stopped")
        scope = null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Small, deterministic acoustical feature extractor for a 16-bit PCM clip. */
internal object FastMoveSoundAnalyzer {


    fun measure(audio: Pcm16Audio): FastMoveSoundMeasured {
        if (audio.samples.isEmpty() || audio.sampleRateHz <= 0) {
            return FastMoveSoundMeasured(false, null, null, null, 0f)
        }
        val windowSamples = (audio.sampleRateHz / 100).coerceAtLeast(1) // 10 ms at any capture rate.
        val windows = audio.samples.asList().chunked(windowSamples).map { samples ->
            sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat() / Short.MAX_VALUE
        }
        val peak = windows.maxOrNull() ?: 0f
        val noise = windows.take(minOf(20, windows.size)).average().toFloat()
        val threshold = maxOf(0.025f, noise * 2.5f, peak * 0.22f)
        val onsetIndex = windows.indexOfFirst { it >= threshold }
        if (onsetIndex < 0 || peak < 0.025f) return FastMoveSoundMeasured(false, null, null, null, peak.coerceIn(0f, 1f))

        var endIndex = onsetIndex
        var quietWindows = 0
        for (index in onsetIndex until windows.size) {
            if (windows[index] >= threshold * 0.65f) quietWindows = 0
            else quietWindows++
            endIndex = index
            if (quietWindows >= 3) break
        }
        val startSample = onsetIndex * windowSamples
        val endSample = minOf(audio.samples.size, (endIndex + 1) * windowSamples)
        return FastMoveSoundMeasured(
            audible = true,
            onsetOffsetNanos = startSample.toLong() * 1_000_000_000L / audio.sampleRateHz,
            soundDurationNanos = (endSample - startSample).toLong() * 1_000_000_000L / audio.sampleRateHz,
            spectralCentroidHz = spectralCentroid(audio.samples, startSample, endSample, audio.sampleRateHz),
            peakAmplitude = peak.coerceIn(0f, 1f)
        )
    }

    /** A small DFT at the loudest available onset window is sufficient metadata, not classification. */
    private fun spectralCentroid(samples: ShortArray, start: Int, end: Int, sampleRateHz: Int): Float {
        val count = minOf(512, end - start)
        if (count < 16) return 0f
        var weighted = 0.0
        var total = 0.0
        for (bin in 1..count / 2) {
            var real = 0.0
            var imaginary = 0.0
            for (index in 0 until count) {
                val angle = 2.0 * Math.PI * bin * index / count
                val value = samples[start + index].toDouble()
                real += value * kotlin.math.cos(angle)
                imaginary -= value * kotlin.math.sin(angle)
            }
            val magnitude = sqrt(real * real + imaginary * imaginary)
            val frequency = bin.toDouble() * sampleRateHz / count
            weighted += frequency * magnitude
            total += magnitude
        }
        return if (total == 0.0) 0f else (weighted / total).toFloat()
    }
}
