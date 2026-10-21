package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayChargeEpisodeTest {
    @Test fun `recorded Weather Ball uses Abomasnow Ice type throughout QTE and flight`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/abomasnow-weather-ball-20261013.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = mapOf(
            "Abomasnow" to listOf(Move("Weather Ball (Ice)", PokemonType.ICE, 60, 35, false),
                Move("Energy Ball", PokemonType.GRASS, 90, 45, false)),
            "Sneasel" to listOf(Move("Ice Punch", PokemonType.ICE, 60, 40, false)),
            "Camerupt" to listOf(Move("Earth Power", PokemonType.GROUND, 90, 50, false))))
        val scenes = (0L..740L).map { model.sceneAt(it * 100_000_000L) }
        val completed = scenes.flatMap { it.chargedMoveActions }.filter { it.phase == ReplayChargePhase.STRIKING }
            .distinctBy { it.side to it.startedAtNanos }
        assertEquals(listOf("Weather Ball (Ice)", "Ice Punch", "Energy Ball", "Earth Power"), completed.map { it.moveName })
        assertEquals(listOf(PokemonType.ICE, PokemonType.ICE, PokemonType.GRASS, PokemonType.GROUND), completed.map { it.type })
        assertTrue(scenes.all { it.chargedMoveActions.size <= 1 })
        assertTrue(scenes.flatMap { it.chargedMoveActions }.all { it.type != null })
        // Camerupt enters 1.259 s after the Energy Ball announcement. A fixed
        // 1.8 s wind-up never reached its throw before the sprite changed.
        val energyFlight = model.sceneAt(50_094_490_157L).chargedMoveActions.single()
        assertEquals("Energy Ball", energyFlight.moveName)
        assertTrue(ReplayChargePose.at(energyFlight, true).projectileProgress > 0f)
        assertTrue("Throw finishes before the player switches", model.sceneAt(50_300_000_000L).chargedMoveActions.none { it.moveName == "Energy Ball" })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `charge flight completes before the recipient is replaced without changing the archive`() {
        val archive = MatchArchive(matchId = "charges", articles = listOf(
            at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 0.0),
            at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215), 0.0),
            at("spent", spend(), 8.0),
            at("entry", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 9.0)))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = moves)
        val flight = model.sceneAt(8_900_000_000L).chargedMoveActions.single()
        assertTrue(ReplayChargePose.at(flight, true).projectileProgress > 0f)
        assertTrue(flight.evidenceKinds.contains("PRESENTATION_FIT_BEFORE_SPECIES_CHANGE"))
        assertEquals(8_000_000_000L, flight.startedAtNanos)
        assertTrue(model.sceneAt(9_000_000_000L).chargedMoveActions.isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `ambiguous Weather Ball types remain unresolved in replay`() {
        val archive = MatchArchive(matchId = "charges", articles = listOf(
            at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Example", 1), 0.0),
            at("ready", ArchivedGetReadyWitnessed, 1.0),
            at("raw", ArchivedRawText("Example used Weather Ball!"), 8.0).copy(sourceId = "ANNOUNCEMENT_WITNESS"),
            at("used", ArchivedChargeMoveUsedAnnounced, 8.0)))
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = mapOf("Example" to listOf(
            Move("Weather Ball (Fire)", PokemonType.FIRE, 60, 35, false),
            Move("Weather Ball (Ice)", PokemonType.ICE, 60, 35, false))))
        val action = model.sceneAt(9_000_000_000L).chargedMoveActions.single()
        assertNull(action.moveName)
        assertNull(action.type)
    }

    @Test fun `recorded repeat readings animate four charges once each with their known types`() {
        // Actual IDs and testimony, with the time origin shifted to GO. Includes
        // late raw repeats and the repaired "Roek Tomb" accepted announcement.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/repeated-charge-readings-20261013.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = moves)
        val scenes = (0L..1140L).map { model.sceneAt(it * 100_000_000L) }
        val completed = scenes.flatMap { it.chargedMoveActions }
            .filter { it.phase == ReplayChargePhase.STRIKING }.distinctBy { it.side to it.startedAtNanos }
        println("Recorded charged moves: " + completed.map { "${it.startedAtNanos / 1e9} ${it.side} ${it.moveName} ${it.type}" })
        assertEquals(listOf("Foul Play", "Brutal Swing", "Brutal Swing", "Rock Tomb"), completed.map { it.moveName })
        assertEquals(listOf(PokemonType.DARK, PokemonType.DARK, PokemonType.DARK, PokemonType.ROCK), completed.map { it.type })
        assertTrue("One animation per charge episode", scenes.all { it.chargedMoveActions.size <= 1 })
        assertTrue(scenes.flatMap { it.chargedMoveActions }.all { it.type != null })
        assertEquals(model.sceneAt(100_000_000_000L), model.sceneAt(100_000_000_000L))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `raw repeats do not duplicate a canonical charge but a fresh preparation permits another use`() {
        val model = model(
            at("ready", ArchivedGetReadyWitnessed, 1.0),
            at("spent", spend(), 8.0),
            raw("repeat", 9.6),
            at("next-ready", ArchivedGetReadyWitnessed, 10.0),
            raw("next-use", 11.5),
        )
        assertEquals(8_000_000_000L, model.sceneAt(9_650_000_000L).chargedMoveActions.single().startedAtNanos)
        assertEquals(11_500_000_000L, model.sceneAt(12_000_000_000L).chargedMoveActions.single().startedAtNanos)
    }

    @Test fun `repeated unnamed announcement supports one typed charge instead of an unknown second attacker`() {
        val model = model(at("spent", spend(), 8.0),
            at("announced-late", ArchivedChargeMoveUsedAnnounced, 9.4))
        val action = model.sceneAt(9_500_000_000L).chargedMoveActions.single()
        assertEquals("Brutal Swing", action.moveName)
        assertEquals(PokemonType.DARK, action.type)
    }

    @Test fun `separate accepted uses are retained even when their times are close`() {
        val model = model(at("one", spend(), 8.0), at("two", spend(), 11.0))
        assertEquals(8_000_000_000L, model.sceneAt(9_000_000_000L).chargedMoveActions.single().startedAtNanos)
        assertEquals(11_000_000_000L, model.sceneAt(12_000_000_000L).chargedMoveActions.single().startedAtNanos)
    }

    private fun model(vararg evidence: ArchivedRealityArticle) = MatchReplayModel(MatchArchive(matchId = "charges",
        articles = listOf(at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 0.0),
            at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215), 0.0)) + evidence),
        chargedMovesBySpeciesName = moves)
    private fun spend() = ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Brutal Swing", 35, "ANNOUNCEMENT")
    private fun raw(id: String, second: Double) = at(id, ArchivedRawText("Turtonator used Brutal Swing!"), second)
        .copy(sourceId = "ANNOUNCEMENT_WITNESS")
    private fun at(id: String, payload: ArchivedTestimonyPayload, second: Double) = ArchivedRealityArticle(
        id, "charges", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), (second * 1e9).toLong())

    private val moves = mapOf(
        "Sneasel" to listOf(Move("Foul Play", PokemonType.DARK, 60, 40, false)),
        "Turtonator" to listOf(Move("Brutal Swing", PokemonType.DARK, 55, 35, false)),
        "Camerupt" to listOf(Move("Rock Tomb", PokemonType.ROCK, 80, 50, false)),
    )
}
