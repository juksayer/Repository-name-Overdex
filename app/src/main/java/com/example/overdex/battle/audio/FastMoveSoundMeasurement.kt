package com.example.overdex.battle.audio

import android.content.Context
import android.util.Log
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.TestimonyPayload
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Measures one short, visually-cued fast-move sound artifact. This witness does
 * not assign a combatant: ranked reference matches and pulse metadata remain
 * independent from the visual cue that requested the recording.
 */
class PersistedFastMoveSoundWitness(private val root: File, private val context: Context) : Observer {
    override val observerId = ObserverId("FAST_MOVE_SOUND_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Fast Move Sound"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            // Subscribe before loading references; never hold up the Timeline collector.
            val queue = Channel<com.example.overdex.battle.reality.RealityArticle>(8)
            val inputAvailable = AtomicReference<Boolean?>(null)
            fun availability(value: Boolean, at: Long) {
                if (inputAvailable.getAndSet(value) != value)
                    match.custody.submitInputAvailability(SourceId(observerId.id), value, at)
            }
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    val audio = article.payload as? AudioCaptured ?: return@collect
                    if (audio.cueKind != BattleCryCueKind.FAST_MOVE_IMPACT.name) return@collect
                    if (!queue.trySend(article).isSuccess) {
                        availability(false, article.perceivedAt)
                    }
                }
            }
            witnessScope.launch {
                try {
                    val matcher = FastMoveSoundReferences.load(context)
                    match.custody.submitAvailability(SourceId(observerId.id), true, System.currentTimeMillis())
                    for (article in queue) {
                        val audio = article.payload as AudioCaptured
                        val file = File(root, audio.artifact.relativePath)
                        val bytes = file.takeIf(File::isFile)?.readBytes()
                        if (bytes == null || sha256(bytes) != audio.artifact.sha256) {
                            availability(false, article.perceivedAt); continue
                        }
                        val pcm = WavPcm16.decode(bytes)
                        if (pcm == null) { availability(false, article.perceivedAt); continue }
                        val mono = StatChangeSoundMatcher.mono8k(pcm)
                        val measured = FastMoveSoundAnalyzer.measureMono8k(mono)
                        val candidates = matcher.measureMono8k(mono)
                        val retained = (candidates.take(3) + candidates.sortedByDescending { it.changingSimilarity }.take(3))
                            .distinctBy { it.moveName }.sortedByDescending { it.similarity }
                        val cueSide = article.evidenceReferences
                            ?.asSequence()
                            ?.mapNotNull { cueId ->
                                match.realityTimeline.getArticles().asReversed()
                                    .firstOrNull { it.id.value == cueId }
                            }
                            ?.mapNotNull { fastMoveCueAttackingSide(it.payload) }
                            ?.firstOrNull()
                        witnessScope.ensureActive()
                        match.custody.submitTestimony(
                            sourceId = SourceId(observerId.id),
                            payload = measured.copy(
                                cueAttackingSide = cueSide,
                                analysisVersion = FastMoveReferenceMatcher.VERSION,
                                referenceCandidates = retained,
                                referenceMatchAccepted = FastMoveReferenceMatcher.accepts(candidates),
                                soundOnsetMonotonicNanos = if (measured.audible) {
                                    article.monotonicTimeNanos?.plus(requireNotNull(measured.onsetOffsetNanos))
                                } else null,
                            ),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                        )
                        availability(true, article.perceivedAt)
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    Log.e("FastMoveSound", "Reference sound analysis unavailable", error)
                    match.custody.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
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
        if (audio.samples.isEmpty() || audio.sampleRateHz <= 0 || audio.channelCount <= 0) {
            return FastMoveSoundMeasured(false, null, null, null, 0f)
        }
        return measureMono8k(StatChangeSoundMatcher.mono8k(audio))
    }

    internal fun measureMono8k(samples: ShortArray): FastMoveSoundMeasured {
        if (samples.isEmpty()) return FastMoveSoundMeasured(false, null, null, null, 0f)
        val rate = 8_000
        val windowSamples = rate / 100
        val windows = (samples.indices step windowSamples).map { start ->
            val end = minOf(start + windowSamples, samples.size)
            var sum = 0.0
            for (i in start until end) sum += samples[i].toDouble() * samples[i]
            sqrt(sum / (end - start)).toFloat() / Short.MAX_VALUE
        }
        val peak = windows.maxOrNull() ?: 0f
        // Earlier attacks or music may occupy the first 200 ms. They are not a noise calibration.
        val floor = windows.sorted()[(windows.size - 1) / 5]
        val threshold = maxOf(.025f, floor + maxOf(.012f, (peak - floor) * .35f))
        // A sound already clipped at the left edge supplies no observed onset.
        val onsetIndex = windows.indices.firstOrNull { index -> index > 0 && windows[index] >= threshold &&
            windows[index - 1] < threshold && windows.subList(maxOf(0, index - 3), index).average() < threshold * .9f
        } ?: -1
        if (onsetIndex < 0 || peak < 0.025f) return FastMoveSoundMeasured(false, null, null, null, peak.coerceIn(0f, 1f))

        var endIndex = onsetIndex
        var quietWindows = 0
        for (index in onsetIndex until windows.size) {
            if (windows[index] >= floor + (threshold - floor) * 0.65f) quietWindows = 0
            else quietWindows++
            endIndex = index
            if (quietWindows >= 3) break
        }
        val startSample = onsetIndex * windowSamples
        val endSample = minOf(samples.size, (endIndex + 1) * windowSamples)
        return FastMoveSoundMeasured(
            audible = true,
            onsetOffsetNanos = startSample.toLong() * 1_000_000_000L / rate,
            soundDurationNanos = (endSample - startSample).toLong() * 1_000_000_000L / rate,
            spectralCentroidHz = spectralCentroid(samples, startSample, endSample, rate),
            peakAmplitude = peak.coerceIn(0f, 1f)
        )
    }

    /** A small DFT at the loudest available onset window is sufficient metadata, not classification. */
    internal fun spectralCentroid(samples: ShortArray, start: Int, end: Int, sampleRateHz: Int): Float {
        val count = minOf(512, end - start)
        if (count < 16) return 0f
        var weighted = 0.0
        var total = 0.0
        for (bin in 1..count / 2) {
            // Goertzel gives the same unwindowed DFT-bin magnitude without two
            // transcendental functions per sample (up to 262,144 calls per clip).
            val coefficient = 2.0 * kotlin.math.cos(2.0 * Math.PI * bin / count)
            var previous = 0.0
            var older = 0.0
            for (index in 0 until count) {
                val next = samples[start + index] + coefficient * previous - older
                older = previous; previous = next
            }
            val magnitude = sqrt(maxOf(0.0, previous * previous + older * older - coefficient * previous * older))
            val frequency = bin.toDouble() * sampleRateHz / count
            weighted += frequency * magnitude
            total += magnitude
        }
        return if (total == 0.0) 0f else (weighted / total).toFloat()
    }
}

/** Prepare once per process off the UI thread, including back-to-back Matches. */
internal object FastMoveSoundReferences {
    @Volatile private var cached: FastMoveReferenceMatcher? = null
    @Synchronized fun load(context: Context): FastMoveReferenceMatcher {
        cached?.let { return it }
        val manifest = context.assets.open("fast_move_sounds/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val entries = manifest.getJSONArray("references")
        val references = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val bytes = context.assets.open("fast_move_sounds/${entry.getString("file")}").use { it.readBytes() }
            val sha = statSoundSha256(bytes)
            check(sha == entry.getString("sha256")) { "Fast Move reference hash mismatch" }
            FastMoveSoundReference(entry.getString("moveName"), sha, requireNotNull(WavPcm16.decode(bytes)))
        }
        return FastMoveReferenceMatcher(references).also { cached = it }
    }
}

/** A recipient-bar cue belongs to the opposite attacker, including mirror matches. */
internal fun fastMoveCueAttackingSide(payload: TestimonyPayload):
    ActivePokemonSide? {
    val damaged = when (payload) {
        is FastMoveUseObserved -> return payload.attackingSide
        is ActiveHpBarDamageTickMeasured -> payload.damagedSide
        is ActiveHpBarBorderPulseObserved -> payload.damagedBarSide
        is HpBarBorderPulse -> if (payload.status == ApertureStatus.PRESENT)
            payload.barSide else return null
        else -> return null
    }
    return if (damaged == ActivePokemonSide.PLAYER)
        ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER
}
