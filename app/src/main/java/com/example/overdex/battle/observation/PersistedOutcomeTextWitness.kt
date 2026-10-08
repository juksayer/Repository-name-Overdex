package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.OutcomeTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Reads each visible Match Outcome crop once; typed outcome witnesses consume its text article. */
class PersistedOutcomeTextWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("MATCH_OUTCOME_TEXT_WITNESS", ObservationSource.SCREEN_CAPTURE),
    override val name: String = "Match Outcome Text Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            // Pay the one-time model startup cost while the battle is underway,
            // not during the first short-lived result screen.
            witnessScope.launch { OutcomeTextRecognizer.warmUp() }
            witnessScope.launch {
                match.articles
                    .filter { article ->
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName
                    }
                    .collect { article ->
                        val crop = article.payload as CropCaptured
                        val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                        // Result text has its own warm, serial lane. Never make a
                        // brief first YOU WIN wait behind species or announcement
                        // OCR, and never drop an already-preserved result crop in
                        // favor of a later summary-card crop.
                        val text = try {
                            OutcomeTextRecognizer.readText(bitmap).trim()
                                .takeIf { it.isNotBlank() }
                                ?: AnnouncementRecognizer.recognize(bitmap).value?.trim()
                        } finally {
                            bitmap.recycle()
                        }?.takeIf { it.isNotBlank() } ?: return@collect
                        match.custody.submitTestimony(
                            sourceId, RawTestimony(text), article.perceivedAt, null,
                            listOf(article.id.value), article.monotonicTimeNanos ?: return@collect
                        )
                    }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}

internal enum class MatchOutcomePhrase { WIN, LOSS }

/** Tolerates one OCR edit inside the gated phrase while refusing unrelated words. */
internal object MatchOutcomeTextResolver {
    fun resolve(rawText: String): MatchOutcomePhrase? {
        val letters = rawText.uppercase().filter(Char::isLetter)
        return when (letters) {
            "YOUWIN" -> MatchOutcomePhrase.WIN
            "GOODEFFORT" -> MatchOutcomePhrase.LOSS
            else -> when {
                editDistanceAtMostOne(letters, "YOUWIN") -> MatchOutcomePhrase.WIN
                editDistanceAtMostOne(letters, "GOODEFFORT") -> MatchOutcomePhrase.LOSS
                else -> null
            }
        }
    }

    private fun editDistanceAtMostOne(actual: String, expected: String): Boolean {
        if (kotlin.math.abs(actual.length - expected.length) > 1) return false
        if (actual == expected) return true
        if (actual.length == expected.length) {
            return actual.indices.count { actual[it] != expected[it] } <= 1
        }
        val shorter = if (actual.length < expected.length) actual else expected
        val longer = if (actual.length < expected.length) expected else actual
        var shortIndex = 0
        var longIndex = 0
        var skipped = false
        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else if (!skipped) {
                skipped = true
                longIndex++
            } else {
                return false
            }
        }
        return true
    }
}

/** Derives one typed outcome signal from the shared immutable outcome-text article. */
class PersistedOutcomePhraseWitness(
    private val accepts: (String) -> Boolean,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var accepted = false
                match.articles.collect { article ->
                    if (accepted || article.sourceId.id != "MATCH_OUTCOME_TEXT_WITNESS") return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    if (!accepts(text)) return@collect
                    match.custody.submitTestimony(
                        sourceId, RawTestimony(text), article.perceivedAt, null,
                        listOf(article.id.value), article.monotonicTimeNanos ?: return@collect
                    )
                    accepted = true
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
