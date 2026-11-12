package com.example.overdex.battle.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamLossResolverTest {
    @Test
    fun `first observed reduced count is a baseline and not a faint`() {
        assertTrue(resolve(id("a", 1, "A"), count("two", 2, 2)).isEmpty())
    }

    @Test
    fun `a missing initial identity leaves room for an unknown fainted actor`() {
        val loss = resolve(count("three", 1, 3), id("a", 2, "A"), count("two", 3, 2)).single()
        assertEquals(listOf("A"), loss.candidates.map { it.speciesName })
        assertNull(loss.resolved)
    }

    @Test
    fun `three identified team members exhaust the initial gap when later HP excludes two`() {
        val loss = resolve(
            count("three", 1, 3), id("a", 2, "A"), id("b", 3, "B"), id("c", 4, "C"),
            count("two", 5, 2), alive("c-alive", 6), id("b-return", 7, "B"), alive("b-alive", 8),
        ).single()
        assertEquals("A", loss.resolved?.speciesName)
        assertTrue(loss.evidenceReferences.containsAll(listOf("a", "b", "c", "c-alive", "b-alive")))
    }

    @Test
    fun `equal counts refresh the interval and increases cannot create another loss`() {
        val losses = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"),
            count("still-three", 4, 3), id("c", 5, "C"), count("two", 6, 2),
            alive("c-alive", 7), count("noisy-three", 8, 3), count("still-two", 9, 2),
        )
        assertEquals(1, losses.size)
        assertEquals("B", losses.single().resolved?.speciesName)
        assertTrue("still-three" in losses.single().evidenceReferences)
    }

    @Test
    fun `a count jump remains unresolved even when only one actor is known`() {
        val loss = resolve(id("a", 1, "A"), count("three", 2, 3), count("one", 3, 1)).single()
        assertEquals(3 to 1, loss.beforeCount to loss.afterCount)
        assertEquals(listOf("A"), loss.candidates.map { it.speciesName })
        assertNull(loss.resolved)
    }

    @Test
    fun `two losses cannot assign the same sole known actor twice`() {
        val losses = resolve(id("a", 1, "A"), count("three", 2, 3), count("two", 3, 2), count("one", 4, 1))
        assertEquals(2, losses.size)
        assertTrue(losses.all { it.resolved == null })
    }

    @Test
    fun `a uniquely supported later loss constrains an earlier ambiguous loss`() {
        val losses = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), count("two", 4, 2),
            count("one", 5, 1),
        )
        assertEquals(listOf("A", "B"), losses.map { it.resolved?.speciesName })
        assertTrue("one" in losses.first().evidenceReferences)
        assertTrue("three" in losses.last().evidenceReferences)
    }

    @Test
    fun `contradictory simultaneous identities prevent assigning positive HP`() {
        val loss = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), id("c", 3, "C"),
            count("two", 4, 2), alive("unknown-hp", 5),
        ).single()
        assertEquals(listOf("A"), loss.candidates.map { it.speciesName })
        assertNull(loss.resolved)
    }

    @Test
    fun `malformed identity sources cannot extend earlier HP ownership`() {
        val loss = resolve(
            id("a", 1, "A"), count("three", 2, 3), id("b", 3, "B"), count("two", 4, 2),
            id("", 5, "C"), alive("unknown-hp", 6),
        ).single()
        assertEquals(listOf("A", "B"), loss.candidates.map { it.speciesName })
        assertNull(loss.resolved)
        assertTrue(loss.evidenceReferences.none(String::isBlank))
    }

    @Test
    fun `a source ID with contradictory identities creates an unknown interval`() {
        val observations = listOf(
            id("a", 1, "A"), count("three", 2, 3), id("bad", 3, "B"), id("bad", 3, "C"),
            count("two", 4, 2), alive("unknown-hp", 5),
        )
        val loss = TeamLossResolver.resolve(observations).single()
        assertNull(loss.resolved)
        assertEquals(listOf("A"), loss.candidates.map { it.speciesName })
        assertEquals(listOf(loss), TeamLossResolver.resolve(observations.reversed()))
    }

    @Test
    fun `conflicting count observations cannot force a candidate attribution`() {
        val loss = resolve(
            id("a", 1, "A"), count("three", 2, 3), count("conflict-one", 3, 3),
            count("conflict-two", 3, 2), count("two", 4, 2),
        ).single()
        assertNull(loss.resolved)
    }

    @Test
    fun `contradictory species IDs across appearances cannot prove a survivor or a faint`() {
        val loss = resolve(
            id("a", 1, "A", 1), count("three", 2, 3), count("two", 4, 2),
            id("b", 5, "B", 1), alive("b-alive", 6),
        ).single()
        assertNull(loss.resolved)
        assertTrue(loss.candidates.isEmpty())
        assertTrue(loss.evidenceReferences.containsAll(listOf("a", "b")))
    }

    @Test
    fun `form refinement is one appearance with its original source anchor`() {
        val loss = resolve(
            id("bare", 1, "Gourgeist", 711), count("three", 2, 3),
            id("form", 3, "Gourgeist (Average)", 711), count("two", 4, 2),
        ).single()
        assertEquals("bare", loss.resolved?.appearanceArticleId)
        assertEquals(1L, loss.resolved?.appearanceAtNanos)
        assertEquals("Gourgeist (Average)", loss.resolved?.speciesName)
        assertEquals(711, loss.resolved?.speciesId)
    }

    @Test
    fun `positive HP at the count timestamp is not later survival proof`() {
        val loss = resolve(
            id("a", 1, "A"), count("three", 2, 3), count("two", 3, 2), alive("same-time", 3),
        ).single()
        assertEquals("A", loss.resolved?.speciesName)
    }

    @Test
    fun `zero survivors followed by positive HP is unresolved rather than assigned backwards`() {
        val loss = resolve(
            id("obstagoon", 1, "Obstagoon"), count("one", 5, 1), id("lucario", 10, "Lucario"),
            count("zero", 14, 0), alive("lucario-alive", 15),
        ).single()
        assertEquals(listOf("Obstagoon"), loss.candidates.map { it.speciesName })
        assertNull(loss.resolved)
        assertTrue("lucario-alive" in loss.evidenceReferences)
        assertTrue("lucario" in loss.evidenceReferences)
    }

    @Test
    fun `exact duplicate deliveries retain one loss and identical evidence`() {
        val observations = listOf(id("a", 1, "A"), count("three", 2, 3), count("two", 3, 2))
        assertEquals(TeamLossResolver.resolve(observations), TeamLossResolver.resolve(observations + observations))
    }

    private fun resolve(vararg observations: TeamLossObservation) = TeamLossResolver.resolve(observations.toList())
    private fun id(id: String, at: Long, name: String, dex: Int? = null) =
        TeamLossObservation.Identity(id, at, name, dex)
    private fun count(id: String, at: Long, count: Int) = TeamLossObservation.Count(id, at, count)
    private fun alive(id: String, at: Long) = TeamLossObservation.Alive(id, at)
}
