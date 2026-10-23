package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class TeamSelectSurfaceEvidenceWindowTest {
    @Test fun `late button reading finds its original badge despite newer visual frames`() {
        val window = TeamSelectSurfaceEvidenceWindow()
        (1L..12L).forEach { second ->
            assertNull(window.observe(second * SECOND) {
                it.copy(leagueBadge = signal("badge-$second", second))
            })
        }
        val result = window.observe(SECOND) { it.copy(useThisParty = signal("button-1", 1)) }
        assertEquals(listOf("badge-1", "button-1"), result?.evidenceArticleIds)
        assertEquals(SECOND, result?.latestSource?.observedAtNanos)
    }

    @Test fun `unrelated newer terrain cannot corroborate an older button`() {
        val window = TeamSelectSurfaceEvidenceWindow()
        window.observe(10 * SECOND) { it.copy(leagueBadge = signal("terrain", 10)) }
        assertNull(window.observe(SECOND) { it.copy(useThisParty = signal("old-button", 1)) })
    }

    @Test fun `nearby anchors retain newest source time even when older read finishes last`() {
        val window = TeamSelectSurfaceEvidenceWindow()
        window.observe(2 * SECOND) { it.copy(leagueBadge = signal("badge", 2)) }
        val result = window.observe(SECOND) { it.copy(useThisParty = signal("button", 1)) }
        assertEquals(2 * SECOND, result?.latestSource?.observedAtNanos)
        assertEquals(2_000L, result?.latestSource?.perceivedAtMillis)
    }

    @Test fun `retention is bounded and an evicted frame cannot be reconstructed from newer scenery`() {
        val window = TeamSelectSurfaceEvidenceWindow()
        (1L..40L).forEach { second ->
            window.observe(second * SECOND) { it.copy(leagueBadge = signal("badge-$second", second)) }
        }
        assertNull(window.observe(SECOND) { it.copy(useThisParty = signal("expired-button", 1)) })
    }

    private fun signal(id: String, second: Long) = TeamSelectSignal(id, second * SECOND, 0.96f, second * 1_000)
    private companion object { const val SECOND = 1_000_000_000L }
}
