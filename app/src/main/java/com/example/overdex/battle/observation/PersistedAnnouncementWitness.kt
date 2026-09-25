package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Reads announcement text only from verified, preserved announcement crops. */
class PersistedAnnouncementWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Announcement Witness"
) : Observer {
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles
                    .filter { article ->
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.announcement.cropName
                    }
                    // The raw crops remain on the Timeline. Text recognition only needs
                    // the newest visible badge, rather than a backlog of identical frames.
                    .conflate()
                    .collect { article ->
                    val crop = article.payload as CropCaptured
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact) ?: return@collect
                    try {
                        val text = AnnouncementRecognizer.recognize(bitmap).value ?: return@collect
                        match.custody.submitTestimony(sourceId, RawTestimony(text), article.perceivedAt, null, listOf(article.id.value), article.monotonicTimeNanos ?: return@collect)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
