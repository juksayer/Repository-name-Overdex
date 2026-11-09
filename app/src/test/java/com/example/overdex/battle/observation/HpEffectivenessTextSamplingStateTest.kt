package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HpEffectivenessTextSamplingStateTest {
    @Test fun `charged camera sample is never eligible for fast move effectiveness OCR`() {
        val sampling = HpEffectivenessTextSamplingState()
        sampling.observeCue("hit-before-charge", SECOND)

        assertNull(sampling.request(sample(SECOND, allowed = false), SECOND))
        assertNull(sampling.request(sample(SECOND + 100_000_000L, allowed = false), SECOND + 100_000_000L))
    }

    @Test fun `excluded frame inside throttle interval clears old cue and immediately admits ordinary reentry`() {
        val sampling = HpEffectivenessTextSamplingState()
        sampling.observeCue("old-hit", SECOND)
        assertEquals("old-hit", sampling.request(sample(SECOND), SECOND)?.cueArticleId)
        sampling.queued(SECOND)

        // This is earlier than the 100 ms burst throttle. Eligibility must be
        // checked before that throttle or the old cue survives the camera move.
        assertNull(sampling.request(sample(SECOND + 10_000_000L, allowed = false), SECOND + 10_000_000L))
        val reentry = requireNotNull(sampling.request(sample(SECOND + 20_000_000L), SECOND + 20_000_000L))
        assertNull(reentry.cueArticleId)

        sampling.observeCue("delayed-old-hit", SECOND + 5_000_000L)
        assertNull(requireNotNull(sampling.request(sample(SECOND + 30_000_000L), SECOND + 30_000_000L)).cueArticleId)
        sampling.observeCue("new-hit", SECOND + 40_000_000L)
        assertEquals("new-hit", sampling.request(sample(SECOND + 40_000_000L), SECOND + 40_000_000L)?.cueArticleId)
    }

    @Test fun `normal source timed hit burst expires without extending for delayed processing`() {
        val sampling = HpEffectivenessTextSamplingState()
        sampling.observeCue("hit", SECOND)
        assertEquals("hit", sampling.request(sample(SECOND), SECOND)?.cueArticleId)
        sampling.queued(SECOND)
        assertNull(sampling.request(sample(SECOND + 99_000_000L), SECOND + 99_000_000L))
        assertNotNull(sampling.request(sample(SECOND + 100_000_000L), SECOND + 100_000_000L))

        val afterBurst = SECOND + 700_000_000L
        assertNull(requireNotNull(sampling.request(sample(afterBurst), afterBurst)).cueArticleId)
        sampling.queued(afterBurst)
        assertNull(sampling.request(sample(afterBurst + 100_000_000L), afterBurst + 100_000_000L))
        assertNotNull(sampling.request(sample(afterBurst + 200_000_000L), afterBurst + 200_000_000L))
    }

    @Test fun `stale bar lock cannot position an effectiveness crop`() {
        val sampling = HpEffectivenessTextSamplingState()
        assertNull(sampling.request(sample(SECOND), SECOND + 500_000_001L))
        assertNotNull(sampling.request(sample(SECOND), SECOND + 500_000_000L))
    }

    @Test fun `new match reset clears charge exclusion and old hit cue`() {
        val sampling = HpEffectivenessTextSamplingState()
        sampling.observeCue("old-hit", 2 * SECOND)
        assertNull(sampling.request(sample(2 * SECOND, allowed = false), 2 * SECOND))
        sampling.reset()
        assertNull(requireNotNull(sampling.request(sample(SECOND), SECOND)).cueArticleId)
        sampling.observeCue("fresh-match-hit", SECOND)
        assertEquals("fresh-match-hit", sampling.request(sample(SECOND), SECOND)?.cueArticleId)
    }

    private fun sample(at: Long, allowed: Boolean = true) = LiveActiveHpBarSample(
        measurement = ActiveHpBarMeasurement(10, 100, 310, 120, .8f, .9f),
        appearance = null,
        recipientVisual = null,
        capturedAtWallTimeMillis = at / 1_000_000L,
        capturedAtMonotonicTimeNanos = at,
        fastMoveEvidenceAllowed = allowed,
    )

    private companion object { const val SECOND = 1_000_000_000L }
}
