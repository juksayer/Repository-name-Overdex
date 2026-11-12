package com.example.overdex.battle.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeamLossResolverAggregateTest {
    @Test
    fun `three to one resolves the two departed owners when the third survives`() {
        val observations = threeKnown() + count("one", 5, 1) + alive("c-alive", 6)
        val loss = TeamLossResolver.resolve(observations).single()

        assertEquals(listOf("A", "B"), names(loss))
        assertNull("An aggregate loss has no singular owner", loss.resolved)
        assertEquals("one", loss.lossId)
        assertEquals(5L, loss.sourceAtNanos)
        assertEquals(3 to 1, loss.beforeCount to loss.afterCount)
        assertTrue(loss.evidenceReferences.containsAll(listOf("a", "b", "c", "three", "one", "c-alive")))
        assertEquals("No intermediate count is invented", 1, TeamLossResolver.resolve(observations).size)
    }

    @Test
    fun `three to zero resolves all three known owners in one interval`() {
        val loss = TeamLossResolver.resolve(threeKnown() + count("zero", 5, 0)).single()
        assertEquals(listOf("A", "B", "C"), names(loss))
        assertNull(loss.resolved)
        assertEquals(3 to 0, loss.beforeCount to loss.afterCount)
        assertEquals(5L, loss.sourceAtNanos)
    }

    @Test
    fun `two out of three candidates stay ambiguous without independent survival`() {
        val loss = TeamLossResolver.resolve(threeKnown() + count("one", 5, 1)).single()
        assertEquals(listOf("A", "B", "C"), loss.candidates.map { it.speciesName })
        assertTrue(loss.resolvedCandidates.isEmpty())
        assertNull(loss.resolved)
    }

    @Test
    fun `a later uncertain single loss can still prove one owner of an aggregate loss`() {
        val losses = TeamLossResolver.resolve(threeKnown() + listOf(
            count("one", 5, 1), id("b-return", 6, "B"), count("zero", 7, 0),
        ))
        // The final owner can be B or C; either way A belongs to the earlier two-owner loss.
        assertEquals(listOf("A"), names(losses[0]))
        assertEquals(3 to 1, losses[0].beforeCount to losses[0].afterCount)
        assertNull("Partial aggregate certainty is not a single faint event", losses[0].resolved)
        assertTrue(losses[1].resolvedCandidates.isEmpty())
        assertTrue("The later constraint is part of the proof", "zero" in losses[0].evidenceReferences)
    }

    @Test
    fun `a later single loss constrains both owners of an earlier aggregate loss`() {
        val losses = TeamLossResolver.resolve(threeKnown() + listOf(count("one", 5, 1), count("zero", 6, 0)))
        assertEquals(listOf(listOf("A", "B"), listOf("C")), losses.map(::names))
        assertEquals(listOf(null, "C"), losses.map { it.resolved?.speciesName })
        assertEquals(listOf(5L, 6L), losses.map { it.sourceAtNanos })
        assertTrue("Both intervals constrain the result", losses.all {
            it.evidenceReferences.containsAll(listOf("three", "one", "zero"))
        })
    }

    @Test
    fun `an aggregate later loss constrains the earlier single loss`() {
        val losses = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), count("two", 4, 2),
            id("c", 5, "C"), count("zero", 6, 0),
        )
        assertEquals(listOf(listOf("A"), listOf("B", "C")), losses.map(::names))
        assertEquals(listOf("A", null), losses.map { it.resolved?.speciesName })
    }

    @Test
    fun `an impossible aggregate cannot be dropped to resolve a connected single loss`() {
        val losses = resolve(
            id("a", 1, "A"), count("three", 2, 3), count("one", 3, 1), count("zero", 4, 0),
        )
        assertEquals(2, losses.size)
        assertTrue(losses.all { it.resolvedCandidates.isEmpty() && it.resolved == null })
    }

    @Test
    fun `the same actor cannot fill slots in both aggregate and single losses`() {
        val losses = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"),
            count("one", 4, 1), count("zero", 5, 0),
        )
        // {A, B} would account for the first decrease, but B cannot also be the final loss.
        assertTrue(losses.all { it.resolvedCandidates.isEmpty() && it.resolved == null })
    }

    @Test
    fun `a contradicted zero loss remains a constraint on connected losses`() {
        val losses = TeamLossResolver.resolve(threeKnown() + listOf(
            count("one", 5, 1), count("zero", 6, 0), id("unknown-survivor", 7, "D"), alive("d-alive", 8),
        ))
        assertTrue(losses.all { it.resolvedCandidates.isEmpty() && it.resolved == null })
    }

    @Test
    fun `unknown initial slots cannot be replaced with named owners by cardinality`() {
        val loss = resolve(
            count("three", 1, 3), id("a", 2, "A"), id("b", 3, "B"), count("one", 4, 1),
        ).single()
        assertEquals(listOf("A", "B"), loss.candidates.map { it.speciesName })
        assertTrue(loss.resolvedCandidates.isEmpty())
    }

    @Test
    fun `all three known species exhaust an initial gap before an aggregate loss`() {
        val loss = resolve(
            count("three", 1, 3), id("a", 2, "A"), id("b", 3, "B"), id("c", 4, "C"),
            count("one", 5, 1), alive("c-alive", 6),
        ).single()
        assertEquals(listOf("A", "B"), names(loss))
    }

    @Test
    fun `an unknown identity interval still leaves unknown slots despite three known names`() {
        val loss = TeamLossResolver.resolve(threeKnown() + listOf(
            id("unknown", 5, ""), count("zero", 6, 0),
        )).single()
        assertTrue(loss.resolvedCandidates.isEmpty())
    }

    @Test
    fun `conflicting source contents leave aggregate owners unresolved and order independent`() {
        val observations = threeKnown() + listOf(
            count("bad-count", 5, 2), count("bad-count", 5, 1), count("one", 6, 1), alive("c-alive", 7),
        )
        val loss = TeamLossResolver.resolve(observations).single()
        assertTrue(loss.resolvedCandidates.isEmpty())
        assertEquals(listOf(loss), TeamLossResolver.resolve(observations.reversed()))
    }

    @Test
    fun `simultaneous conflicting counts cannot force aggregate attribution`() {
        val loss = TeamLossResolver.resolve(threeKnown() + listOf(
            count("bad-two", 5, 2), count("bad-one", 5, 1), count("one", 6, 1), alive("c-alive", 7),
        )).single()
        assertTrue(loss.resolvedCandidates.isEmpty())
    }

    @Test
    fun `later positive HP changes aggregate attribution but a returning name alone does not`() {
        val prefix = threeKnown() + count("one", 5, 1)
        assertTrue(TeamLossResolver.resolve(prefix).single().resolvedCandidates.isEmpty())
        val returning = prefix + id("b-return", 6, "B")
        assertTrue(TeamLossResolver.resolve(returning).single().resolvedCandidates.isEmpty())
        val after = TeamLossResolver.resolve(returning + alive("b-return-hp", 7)).single()
        assertEquals(listOf("A", "C"), names(after))
        assertEquals("one", after.lossId)
        assertEquals(5L, after.sourceAtNanos)
        assertTrue(after.evidenceReferences.containsAll(listOf("b-return", "b-return-hp")))
    }

    @Test
    fun `survival of too many candidates leaves the aggregate contradiction unresolved`() {
        val loss = TeamLossResolver.resolve(threeKnown() + listOf(
            count("one", 5, 1), alive("c-alive", 6), id("b-return", 7, "B"), alive("b-alive", 8),
        )).single()
        assertEquals(listOf("A"), loss.candidates.map { it.speciesName })
        assertTrue(loss.resolvedCandidates.isEmpty())
    }

    @Test
    fun `reordered duplicated and repeated counts preserve aggregate source and evidence`() {
        val observations = threeKnown() + listOf(
            count("one", 5, 1), alive("c-alive", 6), count("still-one", 7, 1),
        )
        val expected = TeamLossResolver.resolve(observations)
        val deliveries = listOf(observations.reversed(), observations + observations) +
            (0..7).map { (observations + observations.take(3)).shuffled(Random(it)) }
        deliveries.forEach { assertEquals(expected, TeamLossResolver.resolve(it)) }
        assertEquals(listOf("A", "B"), names(expected.single()))
        assertEquals("one", expected.single().lossId)
        assertEquals(5L, expected.single().sourceAtNanos)
    }

    @Test
    fun `ordinary switches with unchanged counts do not create aggregate or single losses`() {
        assertTrue(resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), count("still-three", 4, 3),
            id("c", 5, "C"), count("again-three", 6, 3),
        ).isEmpty())
    }

    private fun threeKnown() = listOf(id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), id("c", 4, "C"))
    private fun names(loss: TeamLossResolution) = loss.resolvedCandidates.map { it.speciesName }
    private fun resolve(vararg observations: TeamLossObservation) = TeamLossResolver.resolve(observations.toList())
    private fun id(id: String, at: Long, name: String) = TeamLossObservation.Identity(id, at, name)
    private fun count(id: String, at: Long, count: Int) = TeamLossObservation.Count(id, at, count)
    private fun alive(id: String, at: Long) = TeamLossObservation.Alive(id, at)
}
