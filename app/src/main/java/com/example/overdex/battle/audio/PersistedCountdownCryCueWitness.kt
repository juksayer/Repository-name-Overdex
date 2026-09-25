package com.example.overdex.battle.audio

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.observation.BattleCropContracts
import com.example.overdex.battle.observation.CountdownGlyphMatcher
import com.example.overdex.battle.observation.DroidballService
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

/**
 * Requests a cue-centred microphone window from the first countdown-shaped
 * visual crop. It does not identify a glyph or a Pokémon; the preserved crop
 * and the later AudioCaptured article remain separate Timeline evidence.
 */
class PersistedCountdownCryCueWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("COUNTDOWN_CRY_CUE_WITNESS", ObservationSource.AUDIO_CAPTURE),
    override val name: String = "Countdown Cry Cue Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var cueRequested = false
                match.articles.collect { article ->
                    if (cueRequested) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != BattleCropContracts.countdownGlyph.cropName) return@collect
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact) ?: return@collect
                    try {
                        if (!CountdownGlyphMatcher.hasGlyphLikePresence(bitmap)) return@collect
                        cueRequested = true
                        DroidballService.requestCueCenteredAudio(
                            article.id.value,
                            BattleCryCueKind.COUNTDOWN_VISUAL_PRESENCE
                        )
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
}
