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
    override val name: String,
    private val textSources: Set<String> = setOf("ANNOUNCEMENT_WITNESS"),
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                val episodes = AnnouncementPhraseEpisodes { announcementContainsPhrase(it, phrase) }
                match.articles.collect { article ->
                    if (article.sourceId.id !in textSources) return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    val monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                    if (!episodes.accept(article.sourceId.id, text, monotonicTimeNanos)) return@collect
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
            ObserverId("GET_READY_WITNESS", ObservationSource.SCREEN_CAPTURE), "Get Ready",
            textSources = ANNOUNCEMENT_PHASE_TEXT_SOURCES,
        )

        fun chargeMoveUsed(artifactStore: FileCropArtifactStore) = PersistedAnnouncementPhraseWitness(
            artifactStore, "USED", ChargeMoveUsedAnnounced,
            ObserverId("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS", ObservationSource.SCREEN_CAPTURE),
            "Charge Move Used Announcement"
        )
    }
}

/** Only these calibrated text lanes can supply the early charge-phase cues. */
internal val ANNOUNCEMENT_PHASE_TEXT_SOURCES = setOf("ANNOUNCEMENT_WITNESS", "QTE_PROGRESS_TEXT_WITNESS")

/** Empty readings in another crop cannot rearm a phrase that is still visible. */
internal class AnnouncementPhraseEpisodes(private val matches: (String) -> Boolean) {
    private val lastBySource = mutableMapOf<String, Pair<Long, Boolean>>()
    private var lastEmittedAt: Long? = null
    private var visibilityRetiredThrough = Long.MIN_VALUE

    /** A source-time phase completion retires visibility, not the historical observation. */
    fun retireVisibilityThrough(at: Long) {
        if (at < 0L || at <= visibilityRetiredThrough) return
        visibilityRetiredThrough = at
    }

    fun accept(source: String, text: String, at: Long): Boolean {
        val previous = lastBySource[source]
        if (previous != null && at <= previous.first) return false
        val alreadyVisible = lastBySource.values.any { (time, visible) ->
            visible && at - time in 0..6_000_000_000L &&
                (time > visibilityRetiredThrough || at <= visibilityRetiredThrough)
        }
        val seen = matches(text)
        // A delayed opening may still be published, but cannot leave a completed
        // phase visible and suppress the next warning. Retain source-order dedup.
        lastBySource[source] = at to seen
        if (!seen || alreadyVisible || lastEmittedAt?.let { at <= it } == true) return false
        lastEmittedAt = at
        return true
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
