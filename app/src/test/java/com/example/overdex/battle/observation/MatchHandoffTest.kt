package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.model.BattleEventType
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class MatchHandoffTest {

    @Test
    fun `Match records RealityArticle in timeline before interpretation`() = runBlocking {
        // 1. Setup production-like Match with Fake Knowledge
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "HANDOFF_TEST",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(),
        )

        val testimonyPayload = RawTestimony("Pikachu")
        val timestamp = 1000L
        val sourceId = SourceId("SPECIES_WITNESS")

        // 2. Submit testimony to custody (Production entry point)
        custody.submitTestimony(
            sourceId = sourceId,
            payload = testimonyPayload,
            timestamp = timestamp,
            confidence = 1.0f
        )

        // Give the background collection and interpretation a moment
        delay(200.milliseconds)

        // 3. Verify Timeline Chronology
        val records = match.battleMemory.timeline.records
        assertEquals("Timeline should contain exactly 2 records", 2, records.size)

        // Index 0: The Originating RealityArticle
        val article = records[0] as RealityArticle
        assertEquals("First record must be the RealityArticle", sourceId, article.sourceId)
        assertEquals("Pikachu", (article.payload as RawTestimony).data)
        assertEquals(timestamp, article.perceivedAt)

        // Index 1: The Derived BattleEvent
        val event = records[1] as BattleEvent
        assertEquals("Second record must be the interpreted BattleEvent", BattleEventType.POKEMON_IDENTIFIED, event.type)
        
        // 4. Verify Custody Chain Linkage
        assertEquals("BattleEvent must link back to the originating RealityArticle", article.id, event.sourceArticleId)
        
        match.release()
    }
}
