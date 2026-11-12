package com.example.overdex.battle.history

import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.MatchArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleRecordAggregateFaintTest {
    @Test
    fun `historical aggregate losses preserve the replacement's supported fast and charged metrics`() {
        val rows = opening() + hit("before-count", 4.0) + earlierLosses() +
            charge("charge-after-count", 6.0) + hit("fast-after-count", 7.0)
        for (input in listOf(rows, rows.reversed())) {
            val events = project(input).events
            val faints = events.filter { it.kind == BattleMetricKind.FAINT }
            assertEquals(setOf("Turtonator", "Gourgeist"), faints.map { it.speciesName }.toSet())
            assertEquals(2, faints.size)
            assertTrue("The replacement's identity is not evidence of an earlier owner's faint",
                faints.all { "c-entry" !in it.articleIds })
            val uses = events.filter { it.kind in setOf(BattleMetricKind.FAST_MOVE, BattleMetricKind.CHARGED_MOVE) }
            assertEquals(listOf(BattleMetricKind.FAST_MOVE, BattleMetricKind.CHARGED_MOVE, BattleMetricKind.FAST_MOVE),
                uses.map { it.kind })
            assertTrue(uses.all { it.speciesName == "Camerupt" && "c-entry" in it.articleIds })
            assertEquals("Earth Power", uses.single { it.kind == BattleMetricKind.CHARGED_MOVE }.moveName)
            assertFalse(events.any { it.kind == BattleMetricKind.FAINT && it.speciesName == "Camerupt" })
        }
    }

    @Test
    fun `historical aggregate losses do not clear the replacement's latest supported hit`() {
        val record = project(opening() + hit("c-hit", 4.0) + earlierLosses() +
            opponentFaint("opponent-faint", 5.5))
        val knockout = record.events.single { it.kind == BattleMetricKind.KNOCKOUT }
        assertEquals("Camerupt", knockout.speciesName)
        assertEquals("Stunfisk", knockout.opponentSpecies)
        assertTrue(knockout.articleIds.containsAll(listOf("c-hit", "c-entry", "opponent-faint")))
        assertEquals(0, record.uncreditedOpponentFaints)
    }

    @Test
    fun `the current owner's faint still stops later attacks and removes knockout credit`() {
        for (faintedName in listOf("Camerupt", " caMERUPT ", null)) {
            val record = project(opening() + hit("before-faint", 4.0) +
                row("current-faint", 5.0, ArchivedActivePokemonFaintedWitnessed(
                    "PLAYER", faintedName, 323, "PLAYER_POKE_BALL_COUNT_DECREASE")) +
                hit("after-faint", 5.2) + charge("charge-after-faint", 5.3) +
                opponentFaint("later-opponent-faint", 5.5))
            val uses = record.events.filter { it.kind in setOf(BattleMetricKind.FAST_MOVE, BattleMetricKind.CHARGED_MOVE) }
            assertEquals(1, uses.size)
            assertEquals("before-faint:FAST_MOVE", uses.single().id)
            assertEquals(1, record.events.count { it.kind == BattleMetricKind.FAINT })
            assertTrue(record.events.none { it.kind == BattleMetricKind.KNOCKOUT })
            assertEquals(1, record.uncreditedOpponentFaints)
        }
    }

    private fun opening() = listOf(
        row("a-entry", 0.1, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776)),
        row("opponent-entry", 0.2, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Stunfisk", 618)),
        row("start", 1.0, ArchivedMatchStarted),
        row("b-entry", 2.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711)),
        row("c-entry", 3.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
    )

    private fun earlierLosses() = listOf(
        row("a-aggregate-loss", 5.0, ArchivedActivePokemonFaintedWitnessed(
            "PLAYER", "Turtonator", 776, "PLAYER_POKE_BALL_COUNT_DECREASE_AGGREGATE")),
        row("b-aggregate-loss", 5.0, ArchivedActivePokemonFaintedWitnessed(
            "PLAYER", "Gourgeist", 711, "PLAYER_POKE_BALL_COUNT_DECREASE_AGGREGATE")),
    )

    private fun hit(id: String, seconds: Double) = row(id, seconds, ArchivedFastMoveUseObserved(
        id, "PLAYER", "OPPONENT", attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_BORDER_PULSE"),
        basis = "FUSED"))

    private fun charge(id: String, seconds: Double) = row(id, seconds,
        ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "ANNOUNCEMENT"))

    private fun opponentFaint(id: String, seconds: Double) = row(id, seconds,
        ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Stunfisk", 618, "OPPONENT_POKE_BALL_COUNT_DECREASE"))

    private fun row(id: String, seconds: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "match", (seconds * 1000).toLong(), (seconds * 1000).toLong(), "TEST", payload,
        emptyList(), null, null, emptyList(), (seconds * 1_000_000_000).toLong())

    private fun project(rows: List<ArchivedRealityArticle>) =
        BattleRecordProjector.project(MatchArchive(matchId = "match", articles = rows))
}
