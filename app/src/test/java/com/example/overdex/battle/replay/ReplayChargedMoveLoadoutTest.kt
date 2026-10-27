package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayChargedMoveLoadoutTest {
    @Test fun `confirmed roster does not reuse old charge buttons but fresh OCR can populate them`() {
        val old = event("old", ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate",
            listOf("Earthquake", "Overheat")), 0)
        val source = listOf(old, event("confirmed", ArchivedPlayerTeamRosterConfirmation(true,
            listOf("Camerupt", "Gourgeist", "Abomasnow")), 1), species("player", "PLAYER", "Camerupt", 2),
            event("go", ArchivedMatchStarted, 3))
        assertTrue(model(source).sceneAt(5).playerChargedMoves.isEmpty())
        val observed = model(source + event("ocr", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power"), 4))
            .sceneAt(5).playerChargedMoves.single()
        assertEquals("Earth Power", observed.moveName)
        assertEquals(setOf("ocr"), observed.evidenceArticleIds)
        assertEquals(listOf("Earthquake", "Overheat"), (old.payload as ArchivedPlayerTeamSlotConfigured).chargedMoveNames)
    }

    @Test fun `explicit team save after confirmation restores charge buttons`() {
        val source = listOf(event("confirmed", ArchivedPlayerTeamRosterConfirmation(true,
            listOf("Camerupt", null, null)), 1),
            event("saved", ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate",
                listOf("Earth Power")), 2), species("player", "PLAYER", "Camerupt", 3),
            event("go", ArchivedMatchStarted, 4))
        assertEquals("Earth Power", model(source).sceneAt(5).playerChargedMoves.single().moveName)
    }
    @Test fun `regional forms keep separate charge buttons and ambiguous bare appearance has neither loadout`() {
        val trail = Move("Trailblaze", PokemonType.GRASS, 65, 50, false)
        val brick = Move("Brick Break", PokemonType.FIGHTING, 40, 40, false)
        val alolan = ReplayCombatant("Raichu (Alolan)", 26, 0, playerTeamSlot = 1)
        val ordinary = ReplayCombatant("Raichu", 26, 10, playerTeamSlot = 2)
        val loadout = ReplayChargedMoveLoadout(listOf(
            event("a", ArchivedPlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Trailblaze")), 0),
            event("k", ArchivedPlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Brick Break")), 0),
        ), mapOf("Raichu (Alolan)" to listOf(trail), "Raichu" to listOf(brick))) { _, at ->
            if (at < 10) alolan else ordinary
        }
        assertEquals(listOf("Trailblaze"), loadout.movesFor(alolan).map { it.moveName })
        assertEquals(listOf(PokemonType.GRASS), loadout.movesFor(alolan).map { it.type })
        assertEquals(listOf("Brick Break"), loadout.movesFor(ordinary).map { it.moveName })
        assertTrue(loadout.movesFor(ordinary.copy(playerTeamSlot = null)).isEmpty())
        assertEquals(listOf("Trailblaze"), loadout.movesFor(alolan).map { it.moveName })
    }
    private val earth = Move("Earth Power", PokemonType.GROUND, 90, 55, false)
    private val overheat = Move("Overheat", PokemonType.FIRE, 130, 55, false)
    private val quake = Move("Earthquake", PokemonType.GROUND, 110, 65, false)
    private val seed = Move("Seed Bomb", PokemonType.GRASS, 65, 45, false)
    private val snowball = Move("Weather Ball (Ice)", PokemonType.ICE, 55, 35, false)
    private val references = mapOf("Camerupt" to listOf(earth, overheat, quake), "Gourgeist" to listOf(seed),
        "Abomasnow" to listOf(snowball), "Ninetales" to listOf(Move("Weather Ball (Fire)", PokemonType.FIRE, 55, 35, false)))

    @Test fun `equipped moves follow switches and rewind without exposing other legal moves`() {
        val model = model(listOf(
            event("team1", ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate", listOf("Earth Power", "Overheat")), 0),
            event("team2", ArchivedPlayerTeamSlotConfigured(2, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb")), 0),
            species("c", "PLAYER", "Camerupt", 1),
            species("g", "PLAYER", "Gourgeist", 20),
        ))
        assertEquals(listOf("Earth Power", "Overheat"), model.sceneAt(10).playerChargedMoves.map { it.moveName })
        assertEquals(listOf(PokemonType.GROUND, PokemonType.FIRE), model.sceneAt(10).playerChargedMoves.map { it.type })
        assertEquals(listOf("Seed Bomb"), model.sceneAt(21).playerChargedMoves.map { it.moveName })
        assertEquals(listOf(55, 55), model.sceneAt(10).playerChargedMoves.map { it.energyCost })
        assertEquals(setOf("team1"), model.sceneAt(10).playerChargedMoves.first().evidenceArticleIds)
    }

    @Test fun `late label readings fill opening icons but opponent mirror spends do not`() {
        val model = model(listOf(
            species("p", "PLAYER", "Camerupt", 1), species("o", "OPPONENT", "Camerupt", 1),
            event("opponent-charge", ArchivedChargedMoveEnergySpent("OPPONENT", "Camerupt", "Earthquake", 65, "ANNOUNCEMENT"), 3),
            event("label1", ArchivedPlayerChargeMoveLabelRead(1, "EARTH POWER"), 6),
            event("label2", ArchivedPlayerChargeMoveLabelRead(2, "OVERHEAT"), 7),
        ))
        val slots = model.sceneAt(2).playerChargedMoves
        assertEquals(listOf("Earth Power", "Overheat"), slots.map { it.moveName })
        assertEquals(setOf("label1"), slots.first().evidenceArticleIds)
        assertEquals(0, model.sceneAt(4).playerEnergy!!.recordedEnergy)
    }

    @Test fun `weather ball type is resolved only within active player species`() {
        val model = model(listOf(
            species("a", "PLAYER", "Abomasnow", 1), species("n", "OPPONENT", "Ninetales", 1),
            event("label", ArchivedPlayerChargeMoveLabelRead(1, "WEATHER BALL"), 2),
        ))
        assertEquals(PokemonType.ICE, model.sceneAt(1).playerChargedMoves.single().type)
    }

    @Test fun `unknown or conflicting labels never select arbitrary reference moves`() {
        val model = model(listOf(
            species("p", "PLAYER", "Camerupt", 1),
            event("bad", ArchivedPlayerChargeMoveLabelRead(2, "EAR"), 2),
            event("first", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power"), 3),
            event("tie", ArchivedPlayerChargeMoveLabelRead(1, "Earthquake"), 4),
        ))
        assertTrue(model.sceneAt(5).playerChargedMoves.isEmpty())
    }

    @Test fun `unconfigured spent move can be displayed with its evidence without inventing an unknown type`() {
        val model = model(listOf(
            species("p", "PLAYER", "Camerupt", 1),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Unknown charge", 40, "ANNOUNCEMENT"), 3),
        ))
        val move = model.sceneAt(2).playerChargedMoves.single()
        assertEquals("Unknown charge", move.moveName)
        assertNull(move.type)
        assertEquals(40, move.energyCost)
        assertEquals(setOf("charge"), move.evidenceArticleIds)
    }

    @Test fun `readings after a switch belong to new active pokemon`() {
        val model = model(listOf(
            species("p", "PLAYER", "Camerupt", 1),
            event("earth", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power"), 2),
            species("g", "PLAYER", "Gourgeist", 10),
            event("seed", ArchivedPlayerChargeMoveLabelRead(1, "Seed Bomb"), 11),
        ))
        assertEquals("Seed Bomb", model.sceneAt(12).playerChargedMoves.single().moveName)
        assertEquals("Earth Power", model.sceneAt(5).playerChargedMoves.single().moveName)
    }

    @Test fun `repeated independent OCR corrects saved moves without changing configured article`() {
        val configured = event("team", ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate", listOf("Earthquake", "Overheat")), 0)
        val basic = listOf(configured, species("p", "PLAYER", "Camerupt", 1),
            event("ocr", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power"), 2))
        assertEquals("Earthquake", model(basic).sceneAt(5).playerChargedMoves.first().moveName)
        assertEquals("Earthquake", model(basic + event("cached", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power", "UNCHANGED_LETTER_PIXELS"), 3))
            .sceneAt(5).playerChargedMoves.first().moveName)
        assertEquals("Earth Power", model(basic + event("second-ocr", ArchivedPlayerChargeMoveLabelRead(1, "Earth Power"), 4))
            .sceneAt(5).playerChargedMoves.first().moveName)
        assertEquals("Earthquake", (configured.payload as ArchivedPlayerTeamSlotConfigured).chargedMoveNames.first())
    }

    private fun model(articles: List<ArchivedRealityArticle>) = MatchReplayModel(
        MatchArchive(matchId = "match", articles = articles), chargedMovesBySpeciesName = references)

    private fun species(id: String, side: String, name: String, at: Long) =
        event(id, ArchivedActivePokemonSpeciesWitnessed(side, name, null), at)

    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = at, recordedAt = at,
        sourceId = "TEST", payload = payload, monotonicTimeNanos = at,
        predecessorIds = emptyList(), confidence = 1f, sequenceNumber = null, evidenceReferences = emptyList(),
    )
}
