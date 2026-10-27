package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MatchRosterConfigurationTest {
    @Test fun `confirmed roster replaces live configuration while delayed prior saves remain unaltered testimony`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("roster-config-boundary", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        try {
            suspend fun submit(payload: TestimonyPayload, seconds: Long) = custody.submitTestimony(
                SourceId("USER_TEAM"), payload, seconds * 1_000, 1f, emptyList(), seconds * 1_000_000_000L)
            val old = PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))
            submit(old, 1)
            val confirmation = PlayerTeamRosterConfirmation(true, listOf("Raichu", "Dragalge", "Aegislash"))
            submit(confirmation, 5)
            val late = submit(old, 4)
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            assertEquals(setOf("Raichu", "Dragalge", "Aegislash"), match.playerRosterSpecies().toSet())
            val raw = timeline.getArticles().single { it.sequenceNumber == late.sequenceNumber }
            assertEquals(old, raw.payload)
            assertEquals(4_000_000_000L, raw.monotonicTimeNanos)
            assertEquals(4_000L, raw.perceivedAt)
            assertTrue(timeline.getArticles().any { it.payload == confirmation })
            assertEquals(2, timeline.getArticles().count { it.payload == old })

            val later = old.copy(speciesName = "Raichu", fastMoveName = "Volt Switch")
            submit(later, 6)
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            assertEquals(setOf("Raichu", "Dragalge", "Aegislash"), match.playerRosterSpecies().toSet())
            assertTrue(timeline.getArticles().any { it.payload == later })
        } finally { match.release() }
    }
}
