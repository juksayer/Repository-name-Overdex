package com.example.overdex.battle.audio

import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.custody.AudioCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Promotes every published microphone snippet to an immutable artifact before the Timeline
 * receives its AudioCaptured testimony. It intentionally performs no cry recognition.
 */
class AudioCaptureWitness(
    private val artifactStore: AudioArtifactStore,
    override val observerId: ObserverId,
    override val name: String = "Battle Cry Audio Capture Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                DroidballService.microphoneCaptureAvailable.collect { available ->
                    if (available != null) {
                        match.custody.submitAvailability(
                            sourceId = SourceId(observerId.id),
                            available = available,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
            witnessScope.launch {
                DroidballService.audioFrames.collect { frame ->
                    val wav = Pcm16WavEncoder.encode(frame.pcm16le, frame.sampleRateHz, frame.channelCount)
                    val artifact = artifactStore.preserveWav(wav) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = SourceId(observerId.id),
                        payload = AudioCaptured(
                            artifact = artifact,
                            sampleRateHz = frame.sampleRateHz,
                            channelCount = frame.channelCount,
                            durationNanos = frame.durationNanos,
                            cueKind = frame.cueKind.name
                        ),
                        timestamp = frame.capturedAtWallTimeMillis,
                        confidence = null,
                        evidenceReferences = listOf(frame.cueArticleId),
                        monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Audio capture witness stopped")
        scope = null
    }
}
