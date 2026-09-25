package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Recognizes Attack Incoming only from verified announcement crop artifacts. */
class PersistedAttackIncomingWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("ATTACK_INCOMING_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Attack Incoming Witness"
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
                    if (article.sourceId.id != "ANNOUNCEMENT_WITNESS") return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    if (!text.uppercase().replace(" ", "").contains("ATTACKINCOMING")) return@collect
                    match.custody.submitTestimony(sourceId, RawTestimony(text), article.perceivedAt, null, listOf(article.id.value), article.monotonicTimeNanos ?: return@collect)
                    accepted = true
                }
            }
        }
    }
    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
