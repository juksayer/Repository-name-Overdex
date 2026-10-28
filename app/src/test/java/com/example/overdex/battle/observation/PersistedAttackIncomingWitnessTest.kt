package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistedAttackIncomingWitnessTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `recognized warning is typed once with original announcement and capture time preserved`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("typed-warning", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val witness = PersistedAttackIncomingWitness(FileCropArtifactStore(temporaryFolder.root))
        val at = System.nanoTime()
        try {
            witness.start(match)
            val readings = listOf(
                "Attack incoming!" to 0L,
                "Attack incoming!" to 300_000_000L,
                "Sneasel used Foul Play!" to 1_000_000_000L,
                "Attack incoming!" to 10_000_000_000L,
            )
            readings.forEach { (text, offset) ->
                custody.submitTestimony(SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony(text),
                    1_000L + offset / 1_000_000L, null, listOf("preserved-crop-$offset"), at + offset)
            }
            withTimeout(5_000) {
                while (timeline.getArticles().count { it.payload is AttackIncoming } < 2) delay(10)
                match.awaitAcceptedEvidencePublished()
            }

            val articles = timeline.getArticles()
            val raw = articles.filter { it.sourceId.id == "ANNOUNCEMENT_WITNESS" }
            val warnings = articles.filter { it.payload is AttackIncoming }
            assertEquals(readings.map { RawTestimony(it.first) }, raw.map { it.payload })
            assertEquals(2, warnings.size)
            listOf(raw.first(), raw.last()).zip(warnings).forEach { (source, warning) ->
                assertEquals(SourceId("ATTACK_INCOMING_WITNESS"), warning.sourceId)
                assertEquals(source.perceivedAt, warning.perceivedAt)
                assertEquals(source.monotonicTimeNanos, warning.monotonicTimeNanos)
                assertEquals(listOf(source.id.value), warning.evidenceReferences)
                assertNotNull(warning.sequenceNumber)
            }
            assertTrue(articles.none { it.sourceId.id == "ATTACK_INCOMING_WITNESS" && it.payload is RawTestimony })
            assertTrue(articles.none { it.sourceId.id == "BATTLE_INTERPRETER" && it.payload is AttackIncoming })
        } finally {
            witness.stop()
            match.release()
        }
    }

    @Test fun `typed warning still pauses Match fast move accounting and charge completion releases it`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("typed-warning-pause", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val witness = PersistedAttackIncomingWitness(FileCropArtifactStore(temporaryFolder.root))
        val at = System.nanoTime()
        fun submit(source: String, payload: TestimonyPayload, seconds: Long) {
            custody.submitTestimony(SourceId(source), payload, 1_000L + seconds * 1_000L,
                null, emptyList(), at + seconds * 1_000_000_000L)
        }
        fun hit(side: ActivePokemonSide, seconds: Long) = submit("HP_DAMAGE_FIXTURE",
            ActiveHpBarDamageTickMeasured(side, .9f, .8f, .1f), seconds)
        suspend fun drain() = withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
        try {
            witness.start(match)
            submit(BattleWitnessContracts.countdownGlyph.witnessId, CountdownGlyphWitnessed("GO", .99f, 1), 0)
            hit(ActivePokemonSide.OPPONENT, 1)
            submit("FIXTURE_CLOCK", RawTestimony("later frame"), 2)
            drain()

            submit("ANNOUNCEMENT_WITNESS", RawTestimony("Attack incoming!"), 3)
            withTimeout(5_000) {
                while (timeline.getArticles().none { it.payload is AttackIncoming }) delay(10)
                match.awaitAcceptedEvidencePublished()
            }
            // The boundary flushes the supported hit before the charge pause;
            // it does not count any evidence captured inside that pause.
            assertEquals(1, timeline.getArticles().count { it.payload is FastMoveUseObserved })
            hit(ActivePokemonSide.PLAYER, 4)
            submit("FIXTURE_CLOCK", RawTestimony("inside charged phase"), 5)
            drain()
            assertEquals(1, timeline.getArticles().count { it.payload is FastMoveUseObserved })

            submit("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS", ChargeMoveUsedAnnounced, 6)
            hit(ActivePokemonSide.PLAYER, 8)
            submit("FIXTURE_CLOCK", RawTestimony("after charged phase"), 9)
            submit("FIXTURE_RESULT", MatchEnded(com.example.overdex.model.BattleResult.WIN), 10)
            drain()
            val uses = timeline.getArticles().filter { it.payload is FastMoveUseObserved }
            assertEquals(listOf(at + 1_000_000_000L, at + 8_000_000_000L), uses.map { it.monotonicTimeNanos })
            assertEquals(1, timeline.getArticles().count { it.payload is AttackIncoming })
        } finally {
            witness.stop()
            match.release()
        }
    }
}
