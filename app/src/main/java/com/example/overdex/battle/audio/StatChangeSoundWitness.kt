package com.example.overdex.battle.audio

import android.content.Context
import android.util.Log
import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

/** One responsibility: recognize the four audio cues, without assigning a Pokemon side. */
class StatChangeSoundWitness(
    private val context: Context,
    private val store: AudioArtifactStore,
    private val root: File,
    private val isEnabled: () -> Boolean,
    private val input: Flow<LivePcmChunk> = DroidballService.livePcm,
) : Observer {
    override val observerId = ObserverId("STAT_CHANGE_SOUND_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Stat Change Sound"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope ->
            val matcher = scope.async { loadStatSoundMatcher(context) }
            val listening = StatSoundListeningGate()
            data class Probe(val cue: AudioCaptureCue, val at: Long, val generation: Long)
            val probes = ConcurrentLinkedQueue<Probe>()
            val pendingProbeClips = ConcurrentHashMap<Pair<String, Long>, Pair<Long, Long>>()
            // Phase attention must not wait for reference loading, disk I/O or matching.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    listening.observe(article.payload, article.monotonicTimeNanos, article.sourceId.id)
                }
            }
            scope.launch {
                val recognizer = matcher.await()
                val window = StatSoundWindow()
                var lastSequence: Long? = null
                var lastEnd: Long? = null
                var lastSource: String? = null
                var inputAvailable: Boolean? = null
                fun availability(available: Boolean, atMillis: Long) {
                    if (inputAvailable == available) return
                    inputAvailable = available
                    match.custody.submitInputAvailability(SourceId(observerId.id), available, atMillis)
                }
                val saved = ArrayDeque<Pair<StatChangeCue, Long>>()
                val probeBuffer = TimedStatSoundProbeBuffer()
                val pendingProbes = mutableMapOf<String, Probe>()
                var generation = listening.generation
                fun probeStatus(status: String, refs: List<String>, chunk: LivePcmChunk) {
                    match.custody.submitTestimony(SourceId(observerId.id), StatChangeSoundChecked(status),
                        chunk.endWallTimeMillis, null, refs, chunk.endMonotonicNanos)
                }
                suspend fun preserve(pcm: ByteArray, kind: BattleCryCueKind, refs: List<String>, chunk: LivePcmChunk,
                    expectedGeneration: Long, cueAt: Long) {
                    val artifact = withContext(Dispatchers.IO) {
                        store.preserveWav(Pcm16WavEncoder.encode(pcm, StatChangeSoundMatcher.RATE, 1))
                    }
                    if (artifact == null) {
                        probeStatus("STORAGE_FAILED", refs, chunk)
                        return
                    }
                    if (!listening.acceptsPendingProbe(cueAt, expectedGeneration)) return
                    if (kind == BattleCryCueKind.STAT_CHANGE_SOUND &&
                        !listening.allowsScan(chunk.endMonotonicNanos, expectedGeneration, System.nanoTime())) return
                    val duration = pcm.size * 1_000_000_000L / (StatChangeSoundMatcher.RATE * 2)
                    if (kind == BattleCryCueKind.CHARGED_MOVE_EFFECT)
                        pendingProbeClips[artifact.sha256 to chunk.endMonotonicNanos - duration] =
                            cueAt to expectedGeneration
                    match.custody.submitTestimony(SourceId("STAT_CHANGE_AUDIO_CAPTURE"),
                        AudioCaptured(artifact, StatChangeSoundMatcher.RATE, 1, duration, kind.name,
                            chunk.captureSource, PcmSignalLevel.peak(pcm)),
                        chunk.endWallTimeMillis - duration / 1_000_000, null, refs, chunk.endMonotonicNanos - duration)
                }
                input.collect { chunk ->
                    if (generation != listening.generation) {
                        generation = listening.generation
                        window.reset(); probeBuffer.reset(); pendingProbes.clear(); pendingProbeClips.clear(); saved.clear()
                        lastSequence = null; lastEnd = null; lastSource = null
                    }
                    val gap = lastSequence?.let { chunk.sequence != it + 1 } == true ||
                        lastEnd?.let { chunk.endMonotonicNanos - it > 200_000_000L } == true ||
                        lastSource?.let { it != chunk.captureSource } == true
                    if (gap) {
                        window.reset()
                        if (pendingProbes.isNotEmpty()) probeStatus("INPUT_GAP", pendingProbes.keys.toList(), chunk)
                        pendingProbes.clear()
                        probeBuffer.reset()
                        availability(false, chunk.endWallTimeMillis)
                    }
                    lastSequence = chunk.sequence; lastEnd = chunk.endMonotonicNanos; lastSource = chunk.captureSource
                    while (true) {
                        val request = probes.poll() ?: break
                        if (listening.acceptsPendingProbe(request.at, request.generation))
                            pendingProbes[request.cue.articleId] = request
                    }
                    // Preserve bounded volatile history during ordinary fast play. A delayed
                    // charged announcement must read its original sound, not newer combat audio.
                    probeBuffer.ingest(chunk)
                    for ((id, request) in pendingProbes.toMap()) {
                        if (!listening.acceptsPendingProbe(request.at, request.generation)) {
                            pendingProbes.remove(id); continue
                        }
                        when (val capture = probeBuffer.read(request.at)) {
                            TimedStatSoundProbeBuffer.Read.Pending -> Unit
                            TimedStatSoundProbeBuffer.Read.Unavailable -> {
                                probeStatus("REQUEST_OUTSIDE_RETAINED_AUDIO", listOf(id), chunk)
                                pendingProbes.remove(id)
                            }
                            is TimedStatSoundProbeBuffer.Read.Ready -> {
                                if (listening.acceptsPendingProbe(request.at, request.generation))
                                    preserve(pcmBytes(capture.audio.samples), BattleCryCueKind.CHARGED_MOVE_EFFECT, listOf(id),
                                        chunk.copy(endMonotonicNanos = capture.endNanos, endWallTimeMillis = capture.endWallMillis),
                                        request.generation, request.at)
                                pendingProbes.remove(id)
                            }
                        }
                    }
                    if (!isEnabled() || !listening.allowsScan(chunk.endMonotonicNanos, generation, System.nanoTime())) {
                        window.reset()
                        availability(false, chunk.endWallTimeMillis)
                        return@collect
                    }
                    val query = window.ingest(chunk) ?: return@collect
                    availability(true, chunk.endWallTimeMillis)
                    val found = recognizer.match(query) ?: return@collect
                    if (!isEnabled() || !listening.allowsScan(chunk.endMonotonicNanos, generation, System.nanoTime())) return@collect
                    val duration = query.samples.size * 1_000_000_000L / query.sampleRateHz
                    val at = chunk.endMonotonicNanos - duration + found.offsetNanos
                    if (saved.any { it.first == found.cue && abs(it.second - at) < 1_200_000_000L }) return@collect
                    // Outside charged-move probes, unmatched continuous audio remains volatile.
                    preserve(pcmBytes(query.samples), BattleCryCueKind.STAT_CHANGE_SOUND, emptyList(), chunk,
                        generation, chunk.endMonotonicNanos)
                    saved += found.cue to at
                    while (saved.size > 64) saved.removeFirst()
                }
            }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var lastProbeAt: Long? = null
                val measured = ArrayDeque<Pair<StatChangeCue, Long>>()
                var generation = listening.generation
                match.articles.collect { article ->
                    if (generation != listening.generation) {
                        generation = listening.generation
                        lastProbeAt = null; measured.clear()
                    }
                    if (article.payload is ChargeMoveUsedAnnounced) {
                        val at = article.monotonicTimeNanos ?: return@collect
                        if (!listening.canRequestProbe(at)) return@collect
                        if (lastProbeAt?.let { abs(at - it) < 4_000_000_000L } == true) return@collect
                        lastProbeAt = at
                        if (probes.size < 4) {
                            probes += Probe(AudioCaptureCue(article.id.value, BattleCryCueKind.CHARGED_MOVE_EFFECT), at, generation)
                            match.custody.submitTestimony(SourceId(observerId.id), StatChangeSoundChecked("WINDOW_REQUESTED"),
                                article.perceivedAt, null, listOf(article.id.value), at)
                        }
                        return@collect
                    }
                    val captured = article.payload as? AudioCaptured ?: return@collect
                    val isProbe = captured.cueKind == BattleCryCueKind.CHARGED_MOVE_EFFECT.name
                    if (!isProbe && captured.cueKind != BattleCryCueKind.STAT_CHANGE_SOUND.name) return@collect
                    val clipAt = article.monotonicTimeNanos ?: return@collect
                    val probe = if (isProbe) pendingProbeClips.remove(captured.artifact.sha256 to clipAt)
                        ?: return@collect else null
                    val cueAt = probe?.first ?: clipAt
                    val analysisGeneration = probe?.second ?: listening.generation
                    if (!listening.acceptsPendingProbe(cueAt, analysisGeneration)) return@collect
                    if (!isProbe && !listening.allowsScan(clipAt + captured.durationNanos,
                            analysisGeneration, System.nanoTime())) return@collect
                    try {
                        val recognizer = matcher.await()
                        val bytes = withContext(Dispatchers.IO) { File(root, captured.artifact.relativePath).readBytes() }
                        if (statSoundSha256(bytes) != captured.artifact.sha256) return@collect
                        val candidate = WavPcm16.decode(bytes)?.let(recognizer::bestCandidate)
                        if (!listening.acceptsPendingProbe(cueAt, analysisGeneration)) return@collect
                        if (!isProbe && !listening.allowsScan(clipAt + captured.durationNanos,
                                analysisGeneration, System.nanoTime())) return@collect
                        val found = candidate?.takeIf(StatChangeSoundMatcher::accepts)
                        if (isProbe) match.custody.submitTestimony(SourceId(observerId.id),
                            StatChangeSoundChecked(if (found != null) "ACCEPTED" else "NO_CONFIDENT_MATCH",
                                candidate?.cue?.name, candidate?.similarity, candidate?.runnerUpSimilarity),
                            article.perceivedAt, null, listOf(article.id.value), clipAt)
                        if (found == null) return@collect
                        val at = clipAt + found.offsetNanos
                        if (measured.any { it.first == found.cue && abs(it.second - at) < 1_200_000_000L }) return@collect
                        measured += found.cue to at
                        while (measured.size > 64) measured.removeFirst()
                        match.custody.submitTestimony(SourceId(observerId.id),
                            StatChangeSoundMeasured(found.cue.name, found.referenceSha256,
                                found.similarity, found.runnerUpSimilarity, found.durationNanos),
                            article.perceivedAt + found.offsetNanos / 1_000_000,
                            null, listOf(article.id.value), clipAt + found.offsetNanos)
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (error: Exception) { Log.e("StatChangeSound", "Could not inspect preserved cue", error) }
                }
            }
        }
    }
    override fun stop() { scope?.cancel(); scope = null }

    private fun pcmBytes(samples: ShortArray) = ByteArray(samples.size * 2).also { pcm ->
        samples.forEachIndexed { index, value ->
            pcm[index * 2] = value.toByte(); pcm[index * 2 + 1] = (value.toInt() shr 8).toByte()
        }
    }
}

internal fun loadStatSoundMatcher(context: Context): StatChangeSoundMatcher {
    val manifest = context.assets.open("stat_change_sounds/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
    val entries = manifest.getJSONArray("references")
    val references = StatChangeCue.entries.map { cue ->
        val expected = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("file") == cue.fileName }
        val bytes = context.assets.open("stat_change_sounds/${cue.fileName}").use { it.readBytes() }
        val sha = statSoundSha256(bytes)
        check(sha == expected.getString("sha256")) { "Stat cue reference hash mismatch" }
        StatSoundReference(cue, sha, requireNotNull(WavPcm16.decode(bytes)))
    }
    return StatChangeSoundMatcher(references)
}

internal fun statSoundSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
