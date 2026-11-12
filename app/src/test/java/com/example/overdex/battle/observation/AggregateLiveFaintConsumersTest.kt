package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FaintedCombatants
import com.example.overdex.battle.inference.LiveTeamLossHistory
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AggregateLiveFaintConsumersTest {
    @Before fun before() = DroidballOverlayPresentation.reset()
    @After fun after() = DroidballOverlayPresentation.reset()

    @Test fun `one count can emit two supported owners once without lending both the nearby cry`() {
        val tracker = TeamFaintTracker(SIDE)
        val otherSide = TeamFaintTracker(ActivePokemonSide.PLAYER)
        val inputs = prefix() + article("cry", 9, BattleCryCandidatesMeasured("SPECIES_ENTRY", listOf(
            BattleCryCandidateMeasurement(776, "a", .9f), BattleCryCandidateMeasurement(711, "b", .9f),
        ))) + count("one", 10, 1) + hp("c-alive", 11)
        val decisions = inputs.flatMap {
            assertTrue(otherSide.acceptAll(it).isEmpty())
            tracker.acceptAll(it)
        }
        assertEquals(setOf("Turtonator", "Gourgeist"), decisions.map { it.speciesName }.toSet())
        assertEquals(2, decisions.size)
        decisions.forEach {
            assertEquals("one", it.sourceArticle.id.value)
            assertEquals(10 * SECOND, it.sourceArticle.monotonicTimeNanos)
            assertEquals("OPPONENT_POKE_BALL_COUNT_DECREASE_AGGREGATE", it.basis)
            assertFalse(it.cryCorroborated)
            assertFalse("cry" in it.evidenceReferences)
        }
        assertTrue(inputs.flatMap(tracker::acceptAll).isEmpty())
        assertTrue(tracker.acceptAll(count("still-one", 12, 1)).isEmpty())
    }

    @Test fun `later evidence can name a second aggregate owner without republishing the first`() {
        val tracker = TeamFaintTracker(SIDE)
        val inputs = prefix() + count("one", 10, 1) + species("b-return", 12, "Gourgeist", 711) +
            count("zero", 14, 0)
        val first = inputs.flatMap(tracker::acceptAll)
        assertEquals(listOf("Turtonator"), first.map { it.speciesName })
        // This delayed image was captured before the final count and proves B survived the earlier interval.
        val next = tracker.acceptAll(hp("b-before-zero", 13))
        assertEquals(setOf("Camerupt", "Gourgeist"), next.map { it.speciesName }.toSet())
        assertEquals("one", next.single { it.speciesName == "Camerupt" }.sourceArticle.id.value)
        assertEquals("zero", next.single { it.speciesName == "Gourgeist" }.sourceArticle.id.value)
        assertTrue(tracker.acceptAll(hp("b-before-zero", 13)).isEmpty())
    }

    @Test fun `aggregate exclusions retain earlier attacks and leave the surviving actor usable`() {
        val fainted = FaintedCombatants()
        (prefix() + count("one", 10, 1) + hp("c-alive", 11)).forEach { fainted.observe(it) }
        for (name in listOf("Turtonator", "Gourgeist")) {
            assertTrue(fainted.contains(SIDE, name))
            assertFalse("No individual earlier KO time was witnessed", fainted.at(SIDE, name, 9 * SECOND))
            assertTrue(fainted.at(SIDE, name, 10 * SECOND))
        }
        assertFalse(fainted.contains(SIDE, "Camerupt"))
        assertFalse(fainted.contains(ActivePokemonSide.PLAYER, "Turtonator"))
        val unsupported = ActivePokemonFaintedWitnessed(SIDE, "Camerupt", 323,
            "OPPONENT_POKE_BALL_COUNT_DECREASE_AGGREGATE")
        fainted.observe(article("wrong-conclusion", 10, unsupported))
        assertFalse(fainted.supports(unsupported, 11 * SECOND))
        assertFalse(fainted.contains(SIDE, "Camerupt"))
    }

    @Test fun `contradictory later survival clears aggregate exclusions and retries the supported return`() {
        val fainted = FaintedCombatants()
        (prefix() + count("one", 10, 1) + hp("c-alive", 11)).forEach { fainted.observe(it) }
        val returning = species("b-return", 12, "Gourgeist", 711)
        assertTrue(fainted.observe(returning).isEmpty())
        assertEquals(listOf(returning), fainted.observe(hp("b-return-hp", 13)))
        // Two living candidates contradict a two-owner loss: do not retain a convenient subset as fact.
        assertFalse(fainted.contains(SIDE, "Gourgeist"))
        assertFalse(fainted.contains(SIDE, "Turtonator"))
        assertFalse(fainted.contains(SIDE, "Camerupt"))
    }

    @Test fun `HUD reconciles both aggregate owners while retaining current moves and raw resources`() {
        val history = LiveTeamLossHistory(SIDE)
        val presentation = DroidballOverlayPresentation
        val inputs = prefix() + count("one", 10, 1) + hp("c-alive", 11)
        inputs.forEach { input ->
            val at = input.monotonicTimeNanos!!
            (input.payload as? ActivePokemonSpeciesWitnessed)?.let {
                presentation.recordOpponentSpecies(it.speciesName, it.speciesId, emptyList(), emptyList(),
                    observedAtNanos = at)
            }
            (input.payload as? OpponentBattleResourceCountMeasured)?.let {
                presentation.recordOpponentResources(it, at)
            }
            if (history.accept(input)) presentation.reconcileOpponentFaints(
                history.resolutions.flatMap { it.resolvedCandidates.map { owner -> owner.speciesName } }.toSet(), at)
        }
        assertEquals(setOf("Turtonator", "Gourgeist"), presentation.opponentSpecies.value
            .filter { it.isFainted }.map { it.speciesName }.toSet())
        assertFalse(presentation.opponentSpecies.value.single { it.speciesName == "Camerupt" }.isFainted)
        assertEquals("Camerupt", presentation.activeOpponentMovePossibilities.value?.speciesName)
        val resources = presentation.opponentResources.value
        presentation.reconcileOpponentFaints(setOf("Turtonator", "Gourgeist"), 12 * SECOND)
        assertEquals(resources, presentation.opponentResources.value)
    }

    private fun prefix() = listOf(species("a", 1, "Turtonator", 776), count("three", 2, 3),
        species("b", 5, "Gourgeist", 711), species("c", 8, "Camerupt", 323))
    private fun species(id: String, at: Long, name: String, dex: Int) =
        article(id, at, ActivePokemonSpeciesWitnessed(SIDE, name, dex))
    private fun count(id: String, at: Long, count: Int) = article(id, at,
        OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, count, 3))
    private fun hp(id: String, at: Long) = article(id, at, ActiveHpBarMeasured(SIDE, 20, 30, 120, 40, .5f))
    private fun article(id: String, at: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), at * 1_000, at * 1_000, SourceId("test"), payload, monotonicTimeNanos = at * SECOND)

    private companion object {
        val SIDE = ActivePokemonSide.OPPONENT
        const val SECOND = 1_000_000_000L
    }
}
