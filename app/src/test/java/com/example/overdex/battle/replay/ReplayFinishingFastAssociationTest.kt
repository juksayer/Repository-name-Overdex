package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayFinishingFastAssociationTest {
    private val fragment = ReplayAudioFragment("audio", S, "clip", "ref", 0, 0, 400_000_000, .6f)
    private val audio = ReplayMixedAudioCandidate("Incinerate", S, listOf(fragment, fragment.copy(referenceOffsetNanos = 400_000_000)))
    private val witness = ReplayFastMoveWitness("PLAYER", 2*S, "Incinerate", PokemonType.FIRE, S,
        0, 10*S, setOf("HP_DAMAGE_TICK"), setOf("hit"), setOf(2*S))
    private val cycle = ReplayFastMoveCycle(witness, S, 2*S)
    private val knockout = ReplayKnockout("OPPONENT", "Sealeo", 0, 3*S, 3*S, 4*S, listOf("count"))
    private fun actor(side: String, at: Long) = if (side == "PLAYER") ReplayCombatant("Gourgeist", 711, 0)
        else ReplayCombatant("Sealeo", 364, 0)
    private fun run(articles: List<ArchivedRealityArticle> = emptyList(), candidates: List<ReplayMixedAudioCandidate> = listOf(audio),
                    cycles: List<ReplayFastMoveCycle> = listOf(cycle), kos: List<ReplayKnockout> = listOf(knockout),
                    appearance: (String, Long) -> Long = { _, _ -> 0 }) =
        ReplayFinishingFastAssociator.resolve(articles, candidates, cycles, kos, ::actor, appearance) { true }
    private fun row(id: String, at: Long, payload: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, "match", 0, 0, "TEST", payload, emptyList(), 1f, null, emptyList(), at)
    @Test fun `sound plus supported hit and independent faint can be linked`() {
        val result = run().single()
        assertEquals("Incinerate", result.moveName)
        assertEquals(listOf("audio"), result.audioArticleIds)
        assertEquals(listOf("hit"), result.moveArticleIds)
        assertEquals(listOf("count"), result.faintArticleIds)
        assertEquals(2*S, result.impactAtNanos)
        assertEquals(2*S, result.moveSourceAtNanos)
        assertEquals(3*S, result.faintSourceAtNanos)
    }
    @Test fun `living switch sound alone result alone and missing hit never establish a link`() {
        assertTrue(run(kos = emptyList()).isEmpty())
        assertTrue(run(cycles = emptyList()).isEmpty())
        assertTrue(run(candidates = emptyList()).isEmpty())
        assertTrue(run(kos = listOf(knockout.copy(sourceArticleIds = emptyList()))).isEmpty())
        assertTrue(run(cycles = listOf(cycle.copy(witness = witness.copy(evidenceKinds = setOf("AUDIO"))))).isEmpty())
    }
    @Test fun `switches survivors later competing attacks and wrong move prevent credit`() {
        assertTrue(run(appearance = { side, at -> if (side == "OPPONENT" && at >= 3*S) 3*S else 0 }).isEmpty())
        assertTrue(run(appearance = { side, at -> if (side == "PLAYER" && at >= 2*S) 2*S else 0 }).isEmpty())
        assertTrue(run(candidates = listOf(audio.copy(moveName = "Water Gun"))).isEmpty())
        assertTrue(run(cycles = listOf(cycle.copy(witness = witness.copy(observedAtNanos = 4*S)))).isEmpty())
        val other = cycle.copy(witness = witness.copy(articleIds = setOf("other")), startedAtNanos = 2*S, impactAtNanos = 2*S + S/2)
        assertTrue(run(cycles = listOf(cycle, other)).isEmpty())
        val hp = ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 100, 10, .1f, 1080, 2400, "RED")
        assertTrue(run(articles = listOf(row("alive", 2*S + S/2, hp))).isEmpty())
        assertTrue(run(articles = listOf(row("charge", 2*S + S/2, ArchivedChargeMoveUsedAnnounced))).isEmpty())
    }
    @Test fun `a late count requires a source timed cry near the impact`() {
        val late = knockout.copy(recordedAtNanos = 20*S, presentationAtNanos = 19*S)
        assertTrue(run(kos = listOf(late)).isEmpty())
        val cry = row("cry", 3*S, ArchivedBattleCryLocalized(364, "ref", .9f, S, "test"))
        assertEquals(listOf("count", "cry"), run(articles = listOf(cry), kos = listOf(late)).single().faintArticleIds)
        val unrelated = row("unrelated", S/2, ArchivedMatchStarted)
        assertEquals(run(articles = listOf(unrelated, cry), kos = listOf(late)),
            run(articles = listOf(cry.copy(recordedAt = Long.MAX_VALUE), unrelated), kos = listOf(late)))
    }
    private companion object { const val S = 1_000_000_000L }
}
