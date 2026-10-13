package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.SupportingMatchStart
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.time.ClockReading
import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchInterpretationTest {

    @Test
    fun `missing GO is confirmed by post-countdown damage and starts the session once`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "MISSED_GO",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )
        val session = DroidballSession(match)
        val at = System.nanoTime()
        try {
            custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId),
                CountdownGlyphWitnessed("3", .986f, 1), 1_000L, null, emptyList(), at)
            custody.submitTestimony(SourceId("PLAYER_ACTIVE_HP_DAMAGE_WITNESS"),
                com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured(
                    com.example.overdex.battle.custody.ActivePokemonSide.PLAYER, 1f, .95f, .05f),
                4_100L, null, emptyList(), at + 3_100_000_000L)
            withTimeout(4_000) {
                while (session.phase.value != DroidballSessionPhase.BATTLE_ACTIVE) delay(10)
            }
            // A later OCR delivery of GO remains testimony without duplicating start.
            custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId),
                CountdownGlyphWitnessed("GO", .95f, 2), 4_000L, null, emptyList(), at + 3_000_000_000L)
            withTimeout(4_000) {
                while (timeline.getArticles().count { it.payload is CountdownGlyphWitnessed } < 2) delay(10)
            }
            val starts = timeline.getArticles().filter { it.payload is MatchStarted }
            assertEquals(1, starts.size)
            assertEquals(at + 3_000_000_000L, starts.single().monotonicTimeNanos)
            assertEquals(2, starts.single().predecessorIds.size)
        } finally { session.end(); match.release() }
    }

    @Test
    fun `explicit HUD start arms countdown observation without starting the Match clock`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MANUAL_HUD",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )
        val session = DroidballSession(match)

        session.armCountdown()

        assertEquals(DroidballSessionPhase.COUNTDOWN, session.phase.value)
        assertEquals(MatchState.CREATED, match.state)
        assertEquals(0, timeline.getArticles().count { it.payload is MatchStarted })
        session.end()
        match.release()
    }

    @Test
    fun `GO glyph creates one match start while timer support remains independent`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )
        val session = DroidballSession(match)
        assertEquals(MatchState.CREATED, match.state)

        custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId), CountdownGlyphWitnessed("2", 0.8f, 1), 1000L)
        custody.submitTestimony(SourceId("TRAINER_INACTIVE_TIMER_OVERLAY"), SupportingMatchStart(2, 0.1f, 0.2f), 1100L)
        custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId), CountdownGlyphWitnessed("GO", 0.9f, 3), 1200L)
        custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId), CountdownGlyphWitnessed("GO", 0.95f, 4), 1300L)
        // Match delivers on its own serial executor. Wait for that delivery,
        // rather than treating a busy test machine's 150 ms scheduling delay as a lost GO.
        withTimeout(4_000) {
            while (timeline.getArticles().count { it.payload is CountdownGlyphWitnessed } < 3 ||
                session.phase.value != DroidballSessionPhase.BATTLE_ACTIVE) delay(10)
        }

        val articles = timeline.getArticles()
        val starts = articles.filter { it.payload is MatchStarted }
        assertEquals(1, starts.size)
        val goArticle = articles.first { (it.payload as? CountdownGlyphWitnessed)?.glyph == "GO" }
        assertEquals(listOf(goArticle.id), starts.single().predecessorIds)
        assertEquals(goArticle.perceivedAt, starts.single().perceivedAt)
        assertEquals(goArticle.monotonicTimeNanos, starts.single().monotonicTimeNanos)
        assertEquals(MatchState.CREATED, match.state)
        assertEquals(DroidballSessionPhase.BATTLE_ACTIVE, session.phase.value)
        assertEquals(
            50L,
            match.clock.elapsedNanosBetween(
                ClockReading(goArticle.perceivedAt, goArticle.monotonicTimeNanos!!),
                ClockReading(1250L, goArticle.monotonicTimeNanos!! + 50L)
            )
        )
        assertEquals(2, articles.count { it.payload is CountdownGlyphWitnessed && (it.payload as CountdownGlyphWitnessed).glyph == "GO" })
        assertEquals(1, articles.count { it.payload is SupportingMatchStart })
        session.end()
        match.release()
    }

    @Test
    fun `species testimony is interpreted and recorded in battle memory`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        val speciesName = "Bulbasaur"
        val timestamp = 1000L

        // Submit Species Testimony
        custody.submitTestimony(
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony(speciesName),
            timestamp = timestamp,
            confidence = 1.0f
        )

        // Allow background collection and interpretation
        delay(150)

        // 1. Verify Reality Timeline preservation
        val articles = timeline.getArticles()
        assertEquals(1, articles.size)
        assertEquals(speciesName, (articles.first().payload as RawTestimony).data)

        // 2. Verify Battle Memory interpretation
        val events = match.battleMemory.timeline.events
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals(BattleEventType.POKEMON_IDENTIFIED, event.type)
        assertEquals(BattleActor.ENEMY, event.actor)
        assertEquals(speciesName, event.message)
        assertEquals(timestamp, event.timestamp)

        match.release()
    }

    @Test
    fun `unsupported testimony is preserved in reality but not recorded in battle memory`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        // Submit Unsupported Testimony
        custody.submitTestimony(
            sourceId = SourceId("UNKNOWN_SOURCE"),
            payload = RawTestimony("Some data"),
            timestamp = 2000L,
            confidence = 1.0f
        )

        delay(150)

        // 1. Verify Reality Timeline preservation
        val articles = timeline.getArticles()
        assertEquals(1, articles.size)

        // 2. Verify Battle Memory (should be empty)
        val events = match.battleMemory.timeline.events
        assertEquals(0, events.size)

        match.release()
    }

    @Test
    fun `you win witness testimony with you wvin variant creates derived reality article`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_MATCH",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        custody.submitTestimony(
            sourceId = SourceId("YOU_WIN_WITNESS"),
            payload = RawTestimony("YOU WVIN!"),
            timestamp = 1500L,
            confidence = null
        )

        delay(150)

        val articles = timeline.getArticles()
        assertEquals(2, articles.size)

        // First article: original witness article
        val witnessArticle = articles[0]
        assertEquals(SourceId("YOU_WIN_WITNESS"), witnessArticle.sourceId)
        assertEquals("YOU WVIN!", (witnessArticle.payload as RawTestimony).data)
        assertEquals(null, witnessArticle.confidence)
        assertEquals(emptyList<com.example.overdex.battle.reality.ArticleId>(), witnessArticle.predecessorIds)

        // Second article: derived interpreter article
        val derivedArticle = articles[1]
        assertEquals(SourceId("BATTLE_INTERPRETER"), derivedArticle.sourceId)
        assertEquals(listOf(witnessArticle.id), derivedArticle.predecessorIds)
        assertEquals(MatchEnded(com.example.overdex.model.BattleResult.WIN), derivedArticle.payload)

        match.release()
    }

    @Test
    fun `returning result screen preserves both readings but creates one match end`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "TEST_RETURNING_RESULT",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge()
        )

        custody.submitTestimony(
            SourceId("YOU_WIN_WITNESS"), RawTestimony("YOUWIN!"), 1_500L
        )
        custody.submitTestimony(
            SourceId("YOU_WIN_WITNESS"), RawTestimony("YOU WIN!"), 4_500L
        )
        delay(150)

        assertEquals(2, timeline.getArticles().count {
            it.sourceId == SourceId("YOU_WIN_WITNESS") && it.payload is RawTestimony
        })
        assertEquals(1, timeline.getArticles().count { it.payload is MatchEnded })

        match.release()
    }
}
