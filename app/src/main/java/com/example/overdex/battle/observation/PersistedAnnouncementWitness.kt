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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrSourceContext
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
        val rawSourceId = SourceId("${observerId.id}_OCR")
        val heightProfile = OcrHeightProfile(OcrField.ANNOUNCEMENT)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                val speciesNames = match.pokemonKnowledge.getAllSpeciesNames()
                match.articles
                    .filter { article ->
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.announcement.cropName
                    }
                    // The raw crops remain on the Timeline. Text recognition only needs
                    // the newest visible badge, rather than a backlog of identical frames.
                    .conflate()
                    .collect { article ->
                    val crop = article.payload as CropCaptured
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    try {
                        val at = article.monotonicTimeNanos ?: return@collect
                        val context = OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds)
                        val readings = AnnouncementRecognizer.recognizeEvidence(bitmap, context, heightProfile)
                        var acceptedText = ""
                        val references = mutableListOf(article.id.value)
                        for (reading in readings) {
                            val receipt = match.custody.submitTestimony(rawSourceId,
                                RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                                listOf(article.id.value), at)
                            val rawArticle = match.articles.first { it.sequenceNumber == receipt.sequenceNumber }
                            references += rawArticle.id.value
                            if (acceptedText.isBlank()) acceptedText = reading.acceptedText
                            val announcedSpecies = ENTRY.matchEntire(reading.acceptedText.trim())?.groupValues?.get(1)
                            heightProfile.learn(reading, context, announcedSpecies != null &&
                                speciesNames.any { it.equals(announcedSpecies, ignoreCase = true) })
                        }
                        // The established consumers receive only surviving words, while
                        // the preceding raw articles retain everything ML Kit reported.
                        match.custody.submitTestimony(sourceId, RawTestimony(acceptedText), article.perceivedAt,
                            null, references, at)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.w("ANNOUNCEMENT_WITNESS", "Recognition failed", error)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
    override fun stop() { scope?.cancel("Witness stopped"); scope = null }

    private companion object {
        val ENTRY = Regex("^Go,\\s+(.+?)[!.]?$", RegexOption.IGNORE_CASE)
    }
}
