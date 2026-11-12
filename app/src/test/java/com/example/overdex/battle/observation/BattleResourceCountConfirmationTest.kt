package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class BattleResourceCountConfirmationTest {
    @Test fun `delayed confirmation retains first capture clock and both references`() {
        val confirmation = BattleResourceCountConfirmation()
        val first = frame(1, recordedAt = 8)
        val second = frame(4, recordedAt = 10)
        assertNull(confirmation.observe(first, 3))
        val result = confirmation.observe(second, 3)!!
        assertEquals(first, result.observed)
        assertEquals(listOf(first, second), result.evidence)
        assertEquals(3, result.count)
    }

    @Test fun `single baseline and subsequent single decrease do not manufacture counts`() {
        val confirmation = BattleResourceCountConfirmation()
        assertNull(confirmation.observe(frame(1), 3))
        assertNull(confirmation.observe(frame(40), 2))
        assertNull(confirmation.observe(frame(67), null))
    }

    @Test fun `duplicate or reordered source frames neither confirm nor erase a current candidate`() {
        val confirmation = BattleResourceCountConfirmation()
        assertNull(confirmation.observe(frame(10), 2))
        assertNull(confirmation.observe(frame(10, recordedAt = 11), 2))
        assertNull(confirmation.observe(frame(9, recordedAt = 12), 3))
        assertNull(confirmation.observe(frame(8, recordedAt = 13), null))
        assertEquals(2, confirmation.observe(frame(11, recordedAt = 14), 2)?.count)
    }

    @Test fun `distant matching frames need a fresh pair within the bounded opportunity`() {
        val confirmation = BattleResourceCountConfirmation()
        assertNull(confirmation.observe(frame(1), 3))
        assertNull(confirmation.observe(frame(8), 3))
        assertEquals(8 * SECOND, confirmation.observe(frame(9), 3)?.observed?.monotonicTimeNanos)
    }

    @Test fun `blank frame is unknown and interrupts consecutive agreement`() {
        val confirmation = BattleResourceCountConfirmation()
        assertNull(confirmation.observe(frame(1), 1))
        assertNull(confirmation.observe(frame(2), null))
        assertNull(confirmation.observe(frame(3), 1))
        assertEquals(1, confirmation.observe(frame(4), 1)?.count)
    }

    @Test fun `unchanged confirmed count cites current frame to finish a new assignment`() {
        val confirmation = BattleResourceCountConfirmation()
        confirmation.observe(frame(1), 3)
        confirmation.observe(frame(2), 3)
        assertEquals(3 * SECOND, confirmation.observe(frame(3), 3)?.observed?.monotonicTimeNanos)
    }

    @Test fun `stop restart and old lifecycle delivery cannot cross confirm attempts`() {
        val confirmation = BattleResourceCountConfirmation()
        confirmation.observe(frame(1), 3)
        confirmation.observeLifecycle(frame(2, payload = MatchEnded(BattleResult.WIN)))
        assertNull(confirmation.observe(frame(3), 3))
        confirmation.observeLifecycle(frame(4, payload = ObservationRestartedByUser))
        assertNull(confirmation.observe(frame(3, recordedAt = 5), 3))
        assertNull(confirmation.observe(frame(5), 3))
        confirmation.observeLifecycle(frame(2, payload = ObservationSessionStoppedByUser))
        confirmation.observeLifecycle(frame(4, payload = ObservationRestartedByUser))
        assertEquals(3, confirmation.observe(frame(6), 3)?.count)
    }

    @Test fun `match end allows queued pre-end confirmation but explicit stop cancels it`() {
        val confirmation = BattleResourceCountConfirmation()
        confirmation.observe(frame(1), 1)
        confirmation.observeLifecycle(frame(3, payload = MatchEnded(BattleResult.WIN)))
        assertNull(confirmation.observe(frame(4), 1))
        assertEquals(1, confirmation.observe(frame(2, recordedAt = 5), 1)?.count)

        val stopped = BattleResourceCountConfirmation()
        stopped.observe(frame(1), 1)
        stopped.observeLifecycle(frame(3, payload = ObservationSessionStoppedByUser))
        assertNull(stopped.observe(frame(2, recordedAt = 5), 1))
    }

    private fun frame(seconds: Long, recordedAt: Long = seconds, payload: TestimonyPayload = MatchStarted) =
        RealityArticle(ArticleId("frame-$seconds-$recordedAt"), seconds * 1_000, recordedAt * 1_000,
            SourceId("test"), payload, monotonicTimeNanos = seconds * SECOND)

    private companion object { const val SECOND = 1_000_000_000L }
}
