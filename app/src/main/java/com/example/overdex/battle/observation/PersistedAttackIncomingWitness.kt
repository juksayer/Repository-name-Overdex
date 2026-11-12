package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Recognizes incoming charges from announcement testimony or corroborated shield-button OCR. */
class PersistedAttackIncomingWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId("ATTACK_INCOMING_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Attack Incoming"
) : Observer {
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                val episodes = IncomingAttackAnnouncementEpisodes()
                val shieldText = BattleShieldDecisionTextEvidence()
                var ended = false
                match.articles.collect { article ->
                    if (article.payload is MatchEnded) ended = true
                    if (ended) return@collect
                    if (article.payload is ChargeMoveUsedAnnounced) {
                        article.monotonicTimeNanos?.let(episodes::observeChargeCompletion)
                    }
                    shieldText.accept(article)?.let { signal ->
                        match.custody.submitTestimony(SourceId("SHIELD_DECISION_TEXT_WITNESS"),
                            AttackIncoming, signal.wall, null, signal.ids, signal.at)
                    }
                    if (article.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS") {
                        val raw = article.payload as? RawTestimony ?: return@collect
                        val reading = raw.ocr ?: return@collect
                        val at = article.monotonicTimeNanos ?: return@collect
                        if (article.matchId?.value != match.matchId || raw.data != reading.rawText ||
                            !OutcomeShieldPromptEvidence.matchesText(reading.acceptedText)) return@collect
                        val crop = match.realityTimeline.getArticles().firstOrNull { candidate ->
                            candidate.id.value in article.evidenceReferences.orEmpty() &&
                                candidate.payload is CropCaptured && candidate.matchId == article.matchId &&
                                candidate.monotonicTimeNanos == at && candidate.perceivedAt == article.perceivedAt &&
                                OutcomeShieldPromptEvidence.resolve(candidate, reading) != null
                        } ?: return@collect
                        if (!episodes.accept(reading.acceptedText, at, OutcomeShieldPromptEvidence.SOURCE_ID)) return@collect
                        // The raw OCR and its crop are the canonical proof, with their original
                        // capture time. No new OCR pass or result-reader publication wait is needed.
                        match.custody.submitTestimony(sourceId, AttackIncoming, article.perceivedAt, null,
                            listOf(article.id.value, crop.id.value), at)
                        return@collect
                    }
                    if (article.sourceId.id !in ANNOUNCEMENT_PHASE_TEXT_SOURCES) return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    val at = article.monotonicTimeNanos ?: return@collect
                    if (!episodes.accept(text, at, article.sourceId.id)) return@collect
                    // Recognition is complete here. Preserve the raw announcement
                    // as its own article and publish the warning with that source
                    // time and reference; no downstream text interpretation is needed.
                    match.custody.submitTestimony(sourceId, AttackIncoming, article.perceivedAt, null, listOf(article.id.value), at)
                }
            }
        }
    }
    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}

/** One warning per appearance, rearmed for the next opponent charge. */
internal class IncomingAttackAnnouncementEpisodes {
    private val episodes = AnnouncementPhraseEpisodes(::announcementContainsIncomingAttackCue)

    fun accept(text: String, at: Long, source: String = "ANNOUNCEMENT_WITNESS"): Boolean =
        episodes.accept(source, text, at)

    fun observeChargeCompletion(at: Long) = episodes.retireVisibilityThrough(at)
}

/** Exact phase vocabulary; grades and general shield mentions are not start cues. */
internal fun announcementContainsIncomingAttackCue(text: String): Boolean =
    announcementContainsPhrase(text, "ATTACKINCOMING") ||
        announcementContainsPhrase(text, "USEAPROTECTSHIELD") ||
        announcementContainsPhrase(text, "NOPROTECTSHIELDSREMAINING")
