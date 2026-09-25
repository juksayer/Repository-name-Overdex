package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Emits one typed signal at the start of each continuous visible announcement.
 * Its source is always the preceding, verified announcement crop article.
 */
class PersistedAnnouncementPhraseWitness(
    private val artifactStore: FileCropArtifactStore,
    private val phrase: String,
    private val payload: TestimonyPayload,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var phraseWasVisible = false
                match.articles.collect { article ->
                    if (article.sourceId.id != "ANNOUNCEMENT_WITNESS") return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    val phraseIsVisible = text.uppercase().replace(" ", "").contains(phrase)

                    if (!phraseIsVisible) {
                        phraseWasVisible = false
                        return@collect
                    }
                    if (phraseWasVisible) return@collect

                    phraseWasVisible = true
                    val monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = payload,
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

    companion object {
        fun getReady(artifactStore: FileCropArtifactStore) = PersistedAnnouncementPhraseWitness(
            artifactStore, "GETREADY", GetReadyWitnessed,
            ObserverId("GET_READY_WITNESS", ObservationSource.SCREEN_CAPTURE), "Get Ready Witness"
        )

        fun chargeMoveUsed(artifactStore: FileCropArtifactStore) = PersistedAnnouncementPhraseWitness(
            artifactStore, "USED", ChargeMoveUsedAnnounced,
            ObserverId("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS", ObservationSource.SCREEN_CAPTURE),
            "Charge Move Used Announcement Witness"
        )
    }
}
