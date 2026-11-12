package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrTextEvidence

internal enum class AcceptedChargeCaptureCueKind { GET_READY, ATTACK_INCOMING, PLAYER_QTE_PROGRESS }
internal enum class AcceptedChargeCaptureConsumer { HP, TERMINAL_VISUAL }

/** A reader scheduling hint, not testimony of a move, impact, HP loss, or faint. */
internal data class AcceptedChargeCaptureCue(
    val kind: AcceptedChargeCaptureCueKind,
    val cropArticleId: String,
    val capturedAtNanos: Long,
    val recognizedAtNanos: Long,
    val capturedAtWallTimeMillis: Long,
)

/**
 * Match-owned handoff from accepted text to the currently running readers. It retains no crops,
 * Articles, or queued hints. Canonical raw and typed testimony still follows the normal custody path.
 */
internal class AcceptedChargeCapturePreparation(private val matchId: String) {
    private val lock = Any()
    private var closed = false
    private val subscribers = mutableMapOf<AcceptedChargeCaptureConsumer, Subscriber>()
    private val getReady = AnnouncementPhraseEpisodes { announcementContainsPhrase(it, "GETREADY") }
    private val incoming = IncomingAttackAnnouncementEpisodes()
    private var lastQteCaptureAt: Long? = null

    private class Subscriber(val listener: (AcceptedChargeCaptureCue) -> Unit) {
        @Volatile var active = true
    }

    /** Existing callers own the HP slot; attaching another consumer cannot replace it. */
    fun attach(listener: (AcceptedChargeCaptureCue) -> Unit): () -> Unit =
        attach(AcceptedChargeCaptureConsumer.HP, listener)

    /** Replaces only this consumer's listener; an older detach cannot remove its successor. */
    fun attach(consumer: AcceptedChargeCaptureConsumer, listener: (AcceptedChargeCaptureCue) -> Unit): () -> Unit {
        val attached = synchronized(lock) {
            if (closed) return {}
            subscribers[consumer]?.active = false
            Subscriber(listener).also { subscribers[consumer] = it }
        }
        return {
            synchronized(lock) {
                attached.active = false
                if (subscribers[consumer] === attached) subscribers.remove(consumer)
            }
        }
    }

    fun offer(
        readerSourceId: String,
        cropContract: BattleCropContract,
        crop: RealityArticle,
        acceptedText: String,
        recognizedAtNanos: Long,
    ) {
        val expectedContract = when (readerSourceId) {
            "ANNOUNCEMENT_WITNESS" -> BattleCropContracts.announcement
            "QTE_PROGRESS_TEXT_WITNESS" -> BattleCropContracts.qteProgressText
            else -> return
        }
        offerValidated(readerSourceId, cropContract, expectedContract, crop, acceptedText, recognizedAtNanos)
    }

    /** Reuse only the fully validated no-shields prompt; this lane never supplies grades or Get Ready. */
    fun offerOutcomePrompt(crop: RealityArticle, reading: OcrTextEvidence, recognizedAtNanos: Long) {
        val text = OutcomeShieldPromptEvidence.resolve(crop, reading) ?: return
        offerValidated(OutcomeShieldPromptEvidence.SOURCE_ID, BattleCropContracts.matchOutcome,
            BattleCropContracts.matchOutcome, crop, text, recognizedAtNanos, incomingOnly = true)
    }

    /** A completed charge cannot keep a positive-only outcome prompt visibly active. */
    fun observeChargeCompletion(at: Long) = synchronized(lock) {
        if (!closed) incoming.observeChargeCompletion(at)
    }

    private fun offerValidated(
        readerSourceId: String,
        cropContract: BattleCropContract,
        expectedContract: BattleCropContract,
        crop: RealityArticle,
        acceptedText: String,
        recognizedAtNanos: Long,
        incomingOnly: Boolean = false,
    ) {
        val captured = crop.payload as? CropCaptured ?: return
        val at = crop.monotonicTimeNanos ?: return
        if (matchId.isBlank() || crop.matchId?.value != matchId || crop.id.value.isBlank() ||
            crop.sourceId.id.isBlank() || cropContract != expectedContract ||
            captured.cropProvenance.cropName != expectedContract.cropName ||
            at < 0L || recognizedAtNanos < at || recognizedAtNanos - at > MAX_AGE_NANOS) return

        val delivery = synchronized(lock) {
            if (closed) return
            val targets = AcceptedChargeCaptureConsumer.entries.mapNotNull { subscribers[it]?.takeIf { it.active } }
            if (targets.isEmpty()) return
            // Trusted blank/nonmatching readings can rearm phrase episodes, just as they do in
            // the canonical witnesses. They do not emit a scheduling hint themselves.
            val ready = !incomingOnly && getReady.accept(readerSourceId, acceptedText, at)
            val attack = incoming.accept(acceptedText, at, readerSourceId)
            val grade = readerSourceId == "QTE_PROGRESS_TEXT_WITNESS" &&
                isPlayerQteGradeText(acceptedText) &&
                lastQteCaptureAt?.let { at > it } != false
            if (grade) lastQteCaptureAt = at
            val kind = when {
                ready -> AcceptedChargeCaptureCueKind.GET_READY
                attack -> AcceptedChargeCaptureCueKind.ATTACK_INCOMING
                grade -> AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS
                else -> return
            }
            targets to AcceptedChargeCaptureCue(kind, crop.id.value, at, recognizedAtNanos, crop.perceivedAt)
        }
        // Never acquire a reader's state lock while holding this handoff lock. Detach/close
        // reject pending callbacks where possible; the reader's own generation and lifecycle guards
        // also reject a callback already in flight when its listener was replaced or stopped.
        var failure: Throwable? = null
        delivery.first.forEach { target ->
            if (target.active) {
                try {
                    target.listener(delivery.second)
                } catch (error: Throwable) {
                    // Preserve exception propagation without skipping an independent active reader.
                    val first = failure
                    if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }

    /** Terminal for this Match. A subsequent attach cannot replay or reopen old preparations. */
    fun close() = synchronized(lock) {
        closed = true
        subscribers.values.forEach { it.active = false }
        subscribers.clear()
    }

    private companion object {
        const val MAX_AGE_NANOS = 12_000_000_000L
    }
}
