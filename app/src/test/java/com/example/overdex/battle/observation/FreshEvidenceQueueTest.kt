package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class FreshEvidenceQueueTest {
    @Test fun `current result bypasses backlog while a fleeting older result is retained`() {
        val queue = FreshEvidenceQueue<String>()
        queue.offer("blank"); queue.offer("brief YOU WIN"); queue.offer("summary")
        assertEquals("summary", queue.poll())
        assertEquals("blank", queue.poll())
        queue.offer("current YOU WIN")
        assertEquals("current YOU WIN", queue.poll())
        assertEquals("brief YOU WIN", queue.poll())
        assertNull(queue.poll())
    }
    @Test fun `old frames make progress even while fresh ones keep arriving`() {
        val queue = FreshEvidenceQueue<Int>()
        (0..9).forEach(queue::offer)
        val seen = mutableListOf<Int>()
        (10..29).forEach { queue.offer(it); seen += requireNotNull(queue.poll()) }
        assertTrue(seen.containsAll((0..9).toList()))
    }
}
