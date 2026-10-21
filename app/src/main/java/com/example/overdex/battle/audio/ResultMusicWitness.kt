package com.example.overdex.battle.audio

import android.content.Context
import android.util.Log
import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.ResultMusicCorroboration
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Uses the existing PCM stream. Only confirmed result music is durably retained (four seconds).
 * PCM collection never waits for matching or disk I/O; obsolete analysis windows are conflated.
 */
class ResultMusicWitness(
    private val context: Context,
    private val store: AudioArtifactStore,
    private val isEnabled: () -> Boolean,
    private val input: Flow<LivePcmChunk> = DroidballService.livePcm,
    private val loadMatcher: suspend () -> ResultMusicMatcher = { ResultMusicReferences.load(context) },
) : Observer {
    override val observerId = ObserverId("RESULT_MUSIC_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Result Music Witness"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var listening = ResultMusicListeningGate()
    @Volatile private var failed = false

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val worker = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = worker
        val source = SourceId(observerId.id)
        match.custody.submitAvailability(source, true, System.currentTimeMillis())
        val snapshots = Channel<ResultMusicWindowSnapshot>(Channel.CONFLATED)
        val pending = ConcurrentHashMap<String, MatchResultMusicMeasured>()
        val matcher = worker.async { loadMatcher() }

        // Subscribe before loading references so start/result Articles cannot be missed.
        worker.launch(start = CoroutineStart.UNDISPATCHED) {
            val comparison = ResultMusicCorroboration()
            match.articles.collect { article ->
                listening.observe(article.payload, article.monotonicTimeNanos)
                when (val payload = article.payload) {
                    is AudioCaptured -> if (payload.cueKind == CUE_KIND) {
                        pending.remove(payload.artifact.sha256)?.let { measurement ->
                            match.custody.submitTestimony(source, measurement, article.perceivedAt,
                                null, listOf(article.id.value), requireNotNull(article.monotonicTimeNanos))
                        }
                    }
                }
                for (derived in comparison.accept(article)) {
                    match.custody.submitTestimony(SourceId("RESULT_MUSIC_CORROBORATION"), derived.payload,
                        derived.music.perceivedAt, null, listOf(derived.music.id.value, derived.result.id.value),
                        requireNotNull(derived.music.monotonicTimeNanos))
                }
            }
        }
        worker.launch {
            val window = ResultMusicWindow()
            var available: Boolean? = null
            fun availability(value: Boolean, at: Long) {
                if (available == value) return
                available = value
                match.custody.submitInputAvailability(source, value, at)
            }
            try {
                input.collect { chunk ->
                    if (!canObserve(chunk.endMonotonicNanos)) {
                        window.reset()
                        availability(false, chunk.endWallTimeMillis)
                    } else {
                        val snapshot = window.ingest(chunk)
                        availability(window.ready, chunk.endWallTimeMillis)
                        if (snapshot != null) snapshots.trySend(snapshot)
                    }
                }
            } finally { snapshots.close() }
        }
        worker.launch {
            try {
                val recognizer = matcher.await()
                val confirmation = ResultMusicConfirmation()
                // A repeating result track is one observation. A contradictory track can still be preserved.
                val savedThemes = mutableSetOf<ResultMusicTheme>()
                for (window in snapshots) {
                    if (!canObserve(window.endNanos) || !canObserve(System.nanoTime()) ||
                        System.nanoTime() - window.endNanos > 2_000_000_000L) {
                        confirmation.reset(); continue
                    }
                    val found = recognizer.match(window.audio)
                    if (!confirmation.accept(window, found) || found == null || found.theme in savedThemes) continue
                    if (window.audio.samples.size < ResultMusicMatcher.RATE * 4) continue
                    // Independently recheck the first three seconds of the exact four-second artifact.
                    // This also prevents a dropped/conflated scan from supplying the confirmation.
                    val first = recognizer.match(window.audio.copy(samples = window.audio.samples.copyOfRange(0,
                        window.audio.samples.size - ResultMusicMatcher.RATE))) ?: continue
                    if (first.theme != found.theme || kotlin.math.abs(found.referenceOffsetNanos -
                            first.referenceOffsetNanos - 1_000_000_000L) > 80_000_000L) continue
                    val pcm = ByteArray(window.audio.samples.size * 2)
                    window.audio.samples.forEachIndexed { index, value ->
                        pcm[index * 2] = value.toByte(); pcm[index * 2 + 1] = (value.toInt() shr 8).toByte()
                    }
                    val artifact = withContext(Dispatchers.IO) {
                        store.preserveWav(Pcm16WavEncoder.encode(pcm, ResultMusicMatcher.RATE, 1))
                    } ?: continue
                    ensureActive()
                    pending[artifact.sha256] = MatchResultMusicMeasured(found.theme.name, found.referenceSha256,
                        first.similarity, first.runnerUpSimilarity, first.referenceOffsetNanos, window.durationNanos,
                        followingWindowSimilarity = found.similarity, followingWindowRunnerUpSimilarity = found.runnerUpSimilarity)
                    match.custody.submitTestimony(SourceId("RESULT_MUSIC_AUDIO_CAPTURE"),
                        AudioCaptured(artifact, ResultMusicMatcher.RATE, 1, window.durationNanos, CUE_KIND,
                            window.captureSource, PcmSignalLevel.peak(pcm)),
                        window.endWallMillis - window.durationNanos / 1_000_000, null, emptyList(),
                        window.endNanos - window.durationNanos)
                    savedThemes += found.theme
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                failed = true
                Log.e("ResultMusic", "Result music recognition unavailable", error)
                match.custody.submitAvailability(source, false, System.currentTimeMillis())
            }
        }
    }

    private fun canObserve(at: Long): Boolean {
        return !failed && listening.allows(at, isEnabled())
    }

    override fun stop() {
        scope?.cancel(); scope = null
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        listening = ResultMusicListeningGate(); failed = false
    }

    companion object { const val CUE_KIND = "MATCH_RESULT_MUSIC" }
}

/** Compile once per process, off the UI thread, and reuse across back-to-back Matches. */
internal object ResultMusicReferences {
    @Volatile private var cached: ResultMusicMatcher? = null
    @Synchronized fun load(context: Context): ResultMusicMatcher {
        cached?.let { return it }
        val manifest = context.assets.open("result_music/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val entries = manifest.getJSONArray("references")
        val references = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val bytes = context.assets.open("result_music/${entry.getString("file")}").use { it.readBytes() }
            val sha = statSoundSha256(bytes)
            check(sha == entry.getString("sha256")) { "Result music reference hash mismatch" }
            ResultMusicReference(ResultMusicTheme.valueOf(entry.getString("theme")), sha,
                requireNotNull(WavPcm16.decode(bytes)))
        }
        check(references.map { it.theme }.toSet() == ResultMusicTheme.entries.toSet())
        return ResultMusicMatcher(references).also { cached = it }
    }
}
