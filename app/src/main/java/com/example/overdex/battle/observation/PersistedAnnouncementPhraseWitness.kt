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
                    val phraseIsVisible = announcementContainsPhrase(text, phrase)

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

/** OCR commonly clips the last one or two letters of the brief Get Ready cue. */
internal fun announcementContainsPhrase(text: String, phrase: String): Boolean {
    val normalizedText = text.uppercase().filter(Char::isLetterOrDigit)
    val normalizedPhrase = phrase.uppercase().filter(Char::isLetterOrDigit)
    return when (normalizedPhrase) {
        "GETREADY" -> normalizedText.contains("GETREADY") || normalizedText.contains("GETREA") ||
            (normalizedText.length in 6..9 &&
                (phraseEditDistance(normalizedText, "GETREADY") <= 2 ||
                    (normalizedText.startsWith("GET") && phraseEditDistance(normalizedText, "GETREADY") <= 3)))
        else -> normalizedText.contains(normalizedPhrase)
    }
}

/** Bounded phrase vocabulary; never rewrite the raw OCR or repair a species here. */
private fun phraseEditDistance(actual: String, expected: String): Int {
    var previous = IntArray(expected.length + 1) { it }
    actual.forEachIndexed { row, character ->
        val next = IntArray(expected.length + 1)
        next[0] = row + 1
        expected.forEachIndexed { column, target ->
            next[column + 1] = minOf(previous[column + 1] + 1, next[column] + 1,
                previous[column] + if (character == target) 0 else 1)
        }
        previous = next
    }
    return previous.last()
}
