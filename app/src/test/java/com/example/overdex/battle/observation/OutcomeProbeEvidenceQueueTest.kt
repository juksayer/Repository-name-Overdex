package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class OutcomeProbeEvidenceQueueTest {
    @Test fun `brief weak result survives newer blank until source ordered admission`() {
        val queue = OutcomeProbeEvidenceQueue<String>()
        queue.offer("YOU WIN", 2 * SECOND, 2 * SECOND)
        queue.offer("reward", 4 * SECOND, 4 * SECOND)
        queue.offer("summary", 6 * SECOND, 6 * SECOND)
        assertEquals("YOU WIN", queue.select(7 * SECOND)?.value)
        assertEquals("reward", queue.select(7 * SECOND)?.value)
        assertEquals("summary", queue.select(7 * SECOND)?.value)
        assertNull(queue.select(7 * SECOND))
    }

    @Test fun `sparse backlog retains at most four references and expires at admission`() {
        val queue = OutcomeProbeEvidenceQueue<Int>(capacity = 4)
        repeat(10) { queue.offer(it, it * SECOND, 9 * SECOND) }
        val first = requireNotNull(queue.select(9 * SECOND))
        assertEquals(6, first.value)
        assertEquals(3, first.pendingCount)
        assertEquals(5, first.overflowTotal)
        assertEquals(1, first.expiredTotal)
        // Exact age boundary is eligible; older frames cannot receive urgent OCR.
        assertEquals(7, queue.select(15 * SECOND)?.value)
        assertEquals(8, queue.select(16 * SECOND)?.value)
        assertFalse(queue.hasPending(18 * SECOND))
        assertNull(queue.select(18 * SECOND))
    }

    @Test fun `future and invalid clocks do not gain urgent admission`() {
        val queue = OutcomeProbeEvidenceQueue<String>(maximumAgeNanos = 10)
        queue.offer("future", 21, 20)
        queue.offer("valid", 20, 20)
        assertEquals("valid", queue.select(20)?.value)
        assertFalse(queue.hasPending(20))
        queue.offer("minimum", Long.MIN_VALUE, Long.MIN_VALUE + 1)
        assertEquals("minimum", queue.select(Long.MIN_VALUE + 1)?.value)
        queue.offer("stale", Long.MIN_VALUE, Long.MAX_VALUE)
        queue.offer("maximum", Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals("maximum", queue.select(Long.MAX_VALUE)?.value)
        assertNull(queue.select(Long.MAX_VALUE))
    }

    @Test fun `late delivery is ordered by source and clear releases every reference`() {
        val queue = OutcomeProbeEvidenceQueue<String>(maximumAgeNanos = 10)
        queue.offer("later", 20, 20)
        queue.offer("earlier", 18, 20)
        assertEquals("earlier", queue.select(20)?.value)
        queue.clear()
        assertFalse(queue.hasPending(20))
    }

    private companion object { const val SECOND = 1_000_000_000L }
}
