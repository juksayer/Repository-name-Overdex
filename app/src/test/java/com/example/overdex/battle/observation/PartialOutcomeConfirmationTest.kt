package com.example.overdex.battle.observation

import com.example.overdex.model.observation.OcrBox
import org.junit.Assert.*
import org.junit.Test

class PartialOutcomeConfirmationTest {
    private val at = 10_000_000_000L

    @Test fun `a strong partial result accepts one source frame with its own raw article`() {
        val tracker = PartialOutcomeConfirmation()
        val strong = decision(MatchOutcomePhrase.LOSS, weak = false)
        val accepted = tracker.accept(strong, "crop-1", at, "raw-1")!!
        assertEquals(strong, accepted.decision)
        assertEquals(listOf("raw-1"), accepted.rawArticleIds)
    }

    @Test fun `an older strong result remains valid when recovery finishes after newer unrelated or weak OCR`() {
        listOf(null, decision(MatchOutcomePhrase.WIN)).forEach { newer ->
            val tracker = PartialOutcomeConfirmation()
            assertNull(tracker.accept(newer, "newer-crop", at + 500_000_000L, "newer-raw"))
            val strong = decision(MatchOutcomePhrase.LOSS, weak = false)
            val accepted = tracker.accept(strong, "older-result-crop", at, "older-result-raw")!!
            assertEquals(strong, accepted.decision)
            assertEquals(listOf("older-result-raw"), accepted.rawArticleIds)
        }
    }

    @Test fun `weak result accepts two independent frames and returns both raw references in source order`() {
        listOf(MatchOutcomePhrase.WIN, MatchOutcomePhrase.LOSS).forEach { phrase ->
            val tracker = PartialOutcomeConfirmation()
            val weak = decision(phrase)
            assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
            val accepted = tracker.accept(weak, "crop-2", at + 250_000_000L, "raw-2")!!
            assertEquals(weak, accepted.decision)
            assertEquals(listOf("raw-1", "raw-2"), accepted.rawArticleIds)
        }
    }

    @Test fun `RAW and WHITE readings of the same crop cannot corroborate each other`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.WIN)
        assertNull(tracker.accept(weak, "same-crop", at, "raw-native"))
        assertNull(tracker.accept(weak, "same-crop", at, "raw-white"))
        val accepted = tracker.accept(weak, "next-crop", at + 250_000_000L, "raw-next")!!
        assertEquals(listOf("raw-native", "raw-next"), accepted.rawArticleIds)
    }

    @Test fun `a different crop identifier at the same source timestamp is not an independent frame`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.LOSS)
        assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
        assertNull(tracker.accept(weak, "duplicate-crop", at, "raw-duplicate"))
        val accepted = tracker.accept(weak, "crop-2", at + 250_000_000L, "raw-2")!!
        assertEquals(listOf("raw-1", "raw-2"), accepted.rawArticleIds)
    }

    @Test fun `an out of order frame neither confirms nor replaces the newer candidate`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.WIN)
        assertNull(tracker.accept(weak, "crop-current", at, "raw-current"))
        assertNull(tracker.accept(decision(MatchOutcomePhrase.LOSS), "crop-old", at - 250_000_000L, "raw-old"))
        assertNull(tracker.accept(null, "crop-older-unrelated", at - 100_000_000L, "raw-old-unrelated"))
        val accepted = tracker.accept(weak, "crop-next", at + 250_000_000L, "raw-next")!!
        assertEquals(listOf("raw-current", "raw-next"), accepted.rawArticleIds)
    }

    @Test fun `more than three seconds expires the earlier weak frame and starts a fresh pair`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.LOSS)
        assertNull(tracker.accept(weak, "crop-expired", at, "raw-expired"))
        val freshAt = at + 3_000_000_001L
        assertNull(tracker.accept(weak, "crop-fresh", freshAt, "raw-fresh"))
        val accepted = tracker.accept(weak, "crop-next", freshAt + 250_000_000L, "raw-next")!!
        assertEquals(listOf("raw-fresh", "raw-next"), accepted.rawArticleIds)
    }

    @Test fun `the minimum separation and three second confirmation boundary are inclusive`() {
        listOf(100_000_000L, 3_000_000_000L).forEach { gap ->
            val tracker = PartialOutcomeConfirmation()
            val weak = decision(MatchOutcomePhrase.WIN)
            assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
            assertEquals(listOf("raw-1", "raw-2"),
                tracker.accept(weak, "crop-2", at + gap, "raw-2")!!.rawArticleIds)
        }
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.WIN)
        assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
        assertNull(tracker.accept(weak, "crop-too-close", at + 99_999_999L, "raw-too-close"))
    }

    @Test fun `a later unrelated reading breaks weak confirmation`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.WIN)
        assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
        assertNull(tracker.accept(null, "crop-unrelated", at + 250_000_000L, "raw-unrelated"))
        assertNull(tracker.accept(weak, "crop-2", at + 500_000_000L, "raw-2"))
        val accepted = tracker.accept(weak, "crop-3", at + 750_000_000L, "raw-3")!!
        assertEquals(listOf("raw-2", "raw-3"), accepted.rawArticleIds)
    }

    @Test fun `an unrelated alternate treatment of the same frame does not erase its candidate`() {
        val tracker = PartialOutcomeConfirmation()
        val weak = decision(MatchOutcomePhrase.LOSS)
        assertNull(tracker.accept(weak, "crop-1", at, "raw-1"))
        assertNull(tracker.accept(null, "crop-1", at, "raw-white-unrelated"))
        val accepted = tracker.accept(weak, "crop-2", at + 250_000_000L, "raw-2")!!
        assertEquals(listOf("raw-1", "raw-2"), accepted.rawArticleIds)
    }

    @Test fun `a conflicting weak label resets the pair rather than corroborating an older result`() {
        val tracker = PartialOutcomeConfirmation()
        val win = decision(MatchOutcomePhrase.WIN)
        val loss = decision(MatchOutcomePhrase.LOSS)
        assertNull(tracker.accept(win, "crop-win-1", at, "raw-win-1"))
        assertNull(tracker.accept(loss, "crop-loss", at + 250_000_000L, "raw-loss"))
        assertNull(tracker.accept(win, "crop-win-2", at + 500_000_000L, "raw-win-2"))
        val accepted = tracker.accept(win, "crop-win-3", at + 750_000_000L, "raw-win-3")!!
        assertEquals(listOf("raw-win-2", "raw-win-3"), accepted.rawArticleIds)
    }

    private fun decision(phrase: MatchOutcomePhrase, weak: Boolean = true) = PartialOutcomeTextResolver.Decision(
        phrase = phrase,
        basis = if (weak) PartialOutcomeTextResolver.Basis.DISTINCTIVE_RESULT_SIGNATURE_WITH_POSITION
            else PartialOutcomeTextResolver.Basis.PARTIAL_EXPECTED_LABEL_WITH_RESULT_GEOMETRY,
        needsConfirmation = weak,
        observedLetters = if (phrase == MatchOutcomePhrase.WIN) "W" else "FF",
        matchedLetterCount = if (phrase == MatchOutcomePhrase.WIN) 1 else 2,
        bounds = OcrBox(330f, 700f, 420f, 790f),
        minimumHeightAt672 = 90f,
    )
}
