package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Identifies the VS screen from its own already-preserved central crop.
 * The cited crop is retained for later central-anchor calibration; this witness
 * itself produces only the VS-screen signal.
 */
class PersistedVsScreenWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("VS_SCREEN_WITNESS", ObservationSource.SCREEN_CAPTURE),
    override val name: String = "VS Screen Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var accepted = false
                match.articles.collect { article ->
                    if (accepted) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != BattleCropContracts.vsScreen.cropName) {
                        return@collect
                    }
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact) ?: return@collect
                    val isVsScreen = try {
                        // This crop contains the central VS token. A substring match can
                        // mistake a fast-moving, high-contrast Pokémon feature for “VS”.
                        AnnouncementRecognizer.recognize(bitmap).value
                            ?.uppercase()
                            ?.filter(Char::isLetterOrDigit) == "VS"
                    } finally {
                        bitmap.recycle()
                    }
                    if (!isVsScreen) return@collect

                    val monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                    accepted = true
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = VsScreenWitnessed,
                        timestamp = article.perceivedAt,
                        confidence = null,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = monotonicTimeNanos
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }
}
