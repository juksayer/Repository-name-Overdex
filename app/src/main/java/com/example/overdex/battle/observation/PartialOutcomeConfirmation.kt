package com.example.overdex.battle.observation

/** Weak fragments need two independent source frames, never two OCR treatments. */
internal class PartialOutcomeConfirmation {
    data class Accepted(
        val decision: PartialOutcomeTextResolver.Decision,
        val rawArticleIds: List<String>,
    )
    private data class Candidate(
        val phrase: MatchOutcomePhrase,
        val cropId: String,
        val sourceAt: Long,
        val rawArticleId: String,
    )
    private var candidate: Candidate? = null
    private var latestSourceAt: Long? = null

    fun accept(
        decision: PartialOutcomeTextResolver.Decision?,
        cropId: String,
        sourceAt: Long,
        rawArticleId: String,
    ): Accepted? {
        // A complete-enough title is self-contained evidence. Recovery OCR may
        // finish after newer reward/blank text; only weak multi-frame support
        // needs ordered source times. The witness enforces attempt boundaries.
        if (decision != null && !decision.needsConfirmation) {
            candidate = null
            return Accepted(decision, listOf(rawArticleId))
        }
        val latest = latestSourceAt
        if (latest != null && sourceAt < latest) return null
        latestSourceAt = sourceAt
        if (decision == null) {
            // A failed alternate treatment of the same frame is not a second
            // observation. A later unrelated frame breaks the sequence.
            if (candidate?.sourceAt != sourceAt) candidate = null
            return null
        }
        val earlier = candidate
        if (earlier != null && earlier.phrase == decision.phrase &&
            earlier.cropId != cropId && sourceAt > earlier.sourceAt &&
            sourceAt - earlier.sourceAt in MIN_FRAME_SEPARATION..MAX_CONFIRMATION_GAP) {
            candidate = null
            return Accepted(decision, listOf(earlier.rawArticleId, rawArticleId))
        }
        if (earlier == null || sourceAt > earlier.sourceAt || earlier.phrase != decision.phrase) {
            candidate = Candidate(decision.phrase, cropId, sourceAt, rawArticleId)
        }
        return null
    }

    private companion object {
        const val MIN_FRAME_SEPARATION = 100_000_000L
        const val MAX_CONFIRMATION_GAP = 3_000_000_000L
    }
}
