package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class OutcomeEvidenceQueueTest {
    @Test fun `brief winning frame is read before summary without traversing old backlog`() {
        val queue = OutcomeEvidenceQueue<String>()
        val now = 211_800_000_000L
        repeat(83) { index -> queue.offer("old $index", index * SECOND, now) }
        queue.offer("brief YOU WIN", 211_287_000_000L, now)
        queue.offer("summary", 211_787_000_000L, now)

        assertEquals(OutcomeEvidenceQueue.Counts(2, 83), queue.counts(now))
        assertEquals("brief YOU WIN", queue.pollRecent(now))
        assertEquals("summary", queue.pollRecent(now))
        assertNull(queue.pollRecent(now))
        assertEquals("old 0", queue.pollRecovery(now))
    }

    @Test fun `capture rate above reader capacity bounds live work and preserves every reference`() {
        val queue = OutcomeEvidenceQueue<Int>()
        val read = mutableListOf<Int>()
        var nextReadAt = 600_000_000L
        repeat(120) { index ->
            val now = index * 250_000_000L
            assertTrue(queue.offer(index, now, now))
            if (now >= nextReadAt) {
                queue.pollRecent(now)?.let(read::add)
                nextReadAt += 600_000_000L
            }
            val counts = queue.counts(now)
            assertTrue("The live lane must remain bounded", counts.recent <= 12)
            assertEquals(index + 1, read.size + counts.total)
        }

        val afterCapture = 40 * SECOND
        assertEquals(0, queue.counts(afterCapture).recent)
        while (true) read += queue.pollRecovery(afterCapture) ?: break
        assertEquals((0 until 120).toSet(), read.toSet())
        assertEquals("No reference may be read twice", 120, read.size)
        assertEquals(0, queue.counts(afterCapture).total)
    }

    @Test fun `quarter second captures and slow reads do not bury brief result among 83 old references`() {
        val queue = OutcomeEvidenceQueue<String>()
        val start = 211_287_000_000L
        repeat(83) { queue.offer("old $it", start - 20 * SECOND + it, start) }
        // Admission is initially occupied for a normal 0.6-second native read.
        // The winning text lasts one capture; later captures show the summary.
        var nextReadAt = start + 600_000_000L
        var winningReadAt: Long? = null
        repeat(32) { index ->
            val now = start + index * 250_000_000L
            queue.offer(if (index == 0) "YOU WIN" else "summary $index", now, now)
            if (now >= nextReadAt) {
                if (queue.pollRecent(now) == "YOU WIN") winningReadAt = now
                nextReadAt += 600_000_000L
            }
        }
        assertNotNull("A brief result must not wait for old recovery", winningReadAt)
        assertEquals(750_000_000L, requireNotNull(winningReadAt) - start)
        assertEquals("old 0", queue.pollRecovery(start + 8 * SECOND))
    }

    @Test fun `background recovery can progress independently during sustained live arrivals`() {
        val queue = OutcomeEvidenceQueue<Int>()
        val now = 100 * SECOND
        repeat(20) { queue.offer(it, it.toLong(), now) }
        val recovered = mutableListOf<Int>()
        repeat(20) { index ->
            val at = now + index * 250_000_000L
            queue.offer(20 + index, at, at)
            assertEquals(20 + index, queue.pollRecent(at))
            recovered += requireNotNull(queue.pollRecovery(at))
        }
        assertEquals((0 until 20).toList(), recovered)
        assertEquals(0, queue.counts(now + 5 * SECOND).total)
    }

    @Test fun `overflow becomes source ordered recovery rather than being discarded`() {
        val queue = OutcomeEvidenceQueue<Int>(maxRecentSamples = 2, recentWindowNanos = 10)
        listOf(4, 1, 3, 2).forEach { queue.offer(it, it.toLong(), 5) }
        assertEquals(OutcomeEvidenceQueue.Counts(2, 2), queue.counts(5))
        assertEquals(1, queue.pollRecovery(5))
        assertEquals(2, queue.pollRecovery(5))
        assertEquals(3, queue.pollRecent(5))
        assertEquals(4, queue.pollRecent(5))
    }

    @Test fun `out of order delivery and tied captures remain stable in each lane`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("late", 19, 20)
        queue.offer("early first", 11, 20)
        queue.offer("early second", 11, 20)
        queue.offer("middle", 15, 20)
        queue.offer("old late", 8, 20)
        queue.offer("old early", 2, 20)
        assertEquals("early first", queue.pollRecent(20))
        assertEquals("early second", queue.pollRecent(20))
        assertEquals("middle", queue.pollRecent(20))
        assertEquals("late", queue.pollRecent(20))
        assertEquals("old early", queue.pollRecovery(20))
        assertEquals("old late", queue.pollRecovery(20))
    }

    @Test fun `freshness is checked at admission and exact window boundary remains eligible`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("expired", 10, 10)
        queue.offer("boundary", 11, 11)
        assertEquals("boundary", queue.pollRecent(21))
        assertNull(queue.pollRecent(21))
        assertEquals("expired", queue.pollRecovery(21))
    }

    @Test fun `future timestamps go to recovery without taking live priority`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("future", 101, 100)
        queue.offer("current", 100, 100)
        assertEquals("current", queue.pollRecent(100))
        assertNull(queue.pollRecent(101))
        assertEquals("future", queue.pollRecovery(101))
    }

    @Test fun `clock reversal cannot leave a future entry in live lane`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("past", 10, 12)
        queue.offer("future after reversal", 12, 12)
        assertEquals("past", queue.pollRecent(11))
        assertNull(queue.pollRecent(11))
        assertEquals("future after reversal", queue.pollRecovery(11))
    }

    @Test fun `close clears both lanes and refuses future offers`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("old", 1, 20)
        queue.offer("recent", 20, 20)
        queue.close()
        queue.close()
        assertFalse(queue.offer("after close", 21, 21))
        assertEquals(OutcomeEvidenceQueue.Counts(0, 0), queue.counts(21))
        assertNull(queue.pollRecent(21))
        assertNull(queue.pollRecovery(21))
    }

    @Test fun `invalid queue limits are rejected`() {
        for (limit in listOf(0, -1)) {
            assertTrue(runCatching { OutcomeEvidenceQueue<String>(maxRecentSamples = limit) }
                .exceptionOrNull() is IllegalArgumentException)
        }
        for (window in listOf(0L, -1L)) {
            assertTrue(runCatching { OutcomeEvidenceQueue<String>(recentWindowNanos = window) }
                .exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun `source age arithmetic does not wrap at long boundaries`() {
        val queue = OutcomeEvidenceQueue<String>(recentWindowNanos = 10)
        queue.offer("near minimum", Long.MIN_VALUE, Long.MIN_VALUE + 1)
        assertEquals("near minimum", queue.pollRecent(Long.MIN_VALUE + 1))
        queue.offer("far past", Long.MIN_VALUE, Long.MAX_VALUE)
        queue.offer("near maximum", Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals("near maximum", queue.pollRecent(Long.MAX_VALUE))
        assertEquals("far past", queue.pollRecovery(Long.MAX_VALUE))
    }

    private companion object { const val SECOND = 1_000_000_000L }
}
