package com.example.overdex.battle.archive

import org.junit.Assert.*
import org.junit.Test

class ArchivedPlayerTeamConfigurationTest {
    private fun event(id: String, at: Long, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "match", at, at, "TEST", payload, emptyList(), 1f, null, emptyList(), at)
    private fun config(id: String, at: Long, slot: Int = 1, fast: String = "Powder Snow") = event(id, at,
        ArchivedPlayerTeamSlotConfigured(slot, "Sandslash (Alolan)", 28, fast, listOf("Ice Punch")))
    private fun confirmation(at: Long, accepted: Boolean = true) = event("confirm-$at", at,
        ArchivedPlayerTeamRosterConfirmation(accepted, listOf("Sandslash (Alolan)", "Dragalge", "Aegislash")))

    @Test fun `confirmation removes old equipment even for same species and delayed publication`() {
        val original = listOf(confirmation(10), config("same-time", 10, 2), config("late-old", 5),
            event("start", 20, ArchivedMatchStarted))
        assertTrue(ArchivedPlayerTeamConfiguration(original).recordsAt().isEmpty())
        assertEquals(4, original.size)
        assertEquals("Powder Snow", (original[2].payload as ArchivedPlayerTeamSlotConfigured).fastMoveName)
    }

    @Test fun `later explicit save restores only saved slots and battle setup stays frozen at start`() {
        val source = listOf(config("old", 1, 1), config("old-second", 2, 2), confirmation(10),
            config("new", 11, 1, "Metal Claw"), event("start", 20, ArchivedMatchStarted),
            config("after-start", 30, 2))
        assertEquals(listOf("new"), ArchivedPlayerTeamConfiguration(source).recordsAt().map { it.articleId })
    }

    @Test fun `rejected roster and legacy recording keep existing saved equipment`() {
        assertEquals(listOf("old"), ArchivedPlayerTeamConfiguration(listOf(config("old", 1), confirmation(10, false)))
            .recordsAt().map { it.articleId })
        assertEquals(listOf("old"), ArchivedPlayerTeamConfiguration(listOf(config("old", 1)))
            .recordsAt().map { it.articleId })
    }

    @Test fun `stale configured conclusions and downstream energy are not restored by identical fresh move`() {
        val stale = event("stale-move", 15, ArchivedFastMoveIdentified("PLAYER", "Sandslash (Alolan)",
            "Powder Snow", 1_000_000_000L, cadenceSampleCount = 0, basis = "CONFIGURED_PLAYER_TEAM"))
            .copy(predecessorIds = listOf("old"))
        val gain = event("gain", 16, ArchivedFastMoveEnergyDerived("PLAYER", "Powder Snow", 1, 8, 8, "OBSERVED_USE"))
            .copy(predecessorIds = listOf("stale-move"))
        val fresh = stale.copy(articleId = "fresh", predecessorIds = listOf("new"))
        val measured = stale.copy(articleId = "measured", payload =
            (stale.payload as ArchivedFastMoveIdentified).copy(basis = "HP_BORDER_CADENCE"), predecessorIds = emptyList())
        val projection = ArchivedPlayerTeamConfiguration(listOf(config("old", 1), confirmation(10),
            config("new", 11), stale, gain, fresh, measured))
        assertFalse(projection.acceptsConfiguredMove(stale))
        assertFalse(projection.acceptsDerivedEvidence(gain))
        assertTrue(projection.acceptsConfiguredMove(fresh))
        assertTrue(projection.acceptsDerivedEvidence(measured))
    }
}
