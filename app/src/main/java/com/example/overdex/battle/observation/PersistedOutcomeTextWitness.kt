package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.OutcomeTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import com.example.overdex.battle.reality.RealityArticle

/** Reads preserved result candidates promptly; typed outcome witnesses consume the source-timed text. */
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
            // Queue only immutable artifact references, never decoded bitmaps. The most
            // recent frame alternates with the oldest so a brief result is not discarded.
            val pending = FreshEvidenceQueue<RealityArticle>()
            val wake = Channel<Unit>(Channel.CONFLATED)
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.filter { article ->
                    (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName
                }.collect { article -> pending.offer(article); wake.trySend(Unit) }
            }
            // Pay the one-time model startup cost while the battle is underway,
            // not during the first short-lived result screen.
            witnessScope.launch { OutcomeTextRecognizer.warmUp() }
            witnessScope.launch {
                for (signal in wake) {
                    while (true) {
                        val article = pending.poll() ?: break
                        val crop = article.payload as CropCaptured
                        val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: continue
                        // Keep even blank frames on the reserved result lane.
                        // Retrying them via AnnouncementRecognizer queues the
                        // whole result stream behind unrelated battlefield OCR:
                        // October 12 evidence arrived 30-35 seconds late that way.
                        // Older crops stay queued even when a newer frame is tried first.
                        val text = try {
                            OutcomeTextRecognizer.readText(bitmap).trim()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            // A failed frame must not terminate the result reader
                            // for the remainder of this Match.
                            Log.e("OutcomeTextWitness", "Result OCR failed for ${article.id.value}", error)
                            null
                        } finally {
                            bitmap.recycle()
                        }?.takeIf { it.isNotBlank() } ?: continue
                        match.custody.submitTestimony(
                            sourceId, RawTestimony(text), article.perceivedAt, null,
                            listOf(article.id.value), article.monotonicTimeNanos ?: continue
                        )
                        // One decisive result is sufficient. The unused source crops are
                        // still in the immutable Timeline, available to forensic review.
                        if (MatchOutcomeTextResolver.resolve(text) != null) return@launch
                    }
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
