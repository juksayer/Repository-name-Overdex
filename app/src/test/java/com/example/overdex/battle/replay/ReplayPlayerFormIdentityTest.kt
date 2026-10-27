package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReplayPlayerFormIdentityTest {
    private fun article(id: String, p: ArchivedTestimonyPayload, seconds: Long, source: String = "test") =
        ArchivedRealityArticle(id, "forms", 0, 0, source, p, emptyList(), null, null, emptyList(), seconds * 1_000_000_000L)
    private fun badge(id: String, name: String, seconds: Long, source: String = "PLAYER_SPECIES_OVERLAY_PIPELINE") =
        article(id, ArchivedActivePokemonSpeciesWitnessed("PLAYER", name, 26), seconds, source)
    private fun team() = listOf(
        article("a-config", ArchivedPlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Trailblaze", "Wild Charge")), 0),
        article("k-config", ArchivedPlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Brick Break", "Thunder")), 0),
        article("a-scan", ArchivedPlayerTeamRosterSlotWitnessed(1, "Raichu (Alolan)", 26, "fALOLA", .2f), 1),
        article("k-scan", ArchivedPlayerTeamRosterSlotWitnessed(2, "Raichu", 26, "base", .2f), 1),
        badge("lead", "Raichu", 3),
        article("go", ArchivedMatchStarted, 4),
    )

    @Test fun `recorded late faint belongs to outgoing Alolan not incoming ordinary Raichu`() {
        val text = requireNotNull(javaClass.getResource("/battle/2026-10-22-raichu-faint.json")).readText()
        val archive = Json { classDiscriminator = "kind"; ignoreUnknownKeys = true }.decodeFromString(MatchArchive.serializer(), text)
        val model = MatchReplayModel(archive)
        val start = archive.articles.first().monotonicTimeNanos!!
        assertEquals("Raichu (Alolan)", model.sceneAt(start + 100_000_000_000L).player?.speciesName)
        val replacement = model.sceneAt(start + 120_000_000_000L).player!!
        assertEquals("Raichu", replacement.speciesName)
        assertEquals(2, replacement.playerTeamSlot)
        assertFalse(replacement.isFainted)
    }

    @Test fun `Raichu recording now retains observed Alolan lead and Spark without altering any source article`() {
        val text = requireNotNull(javaClass.getResource("/battle/2026-10-21-raichu-identity.json")).readText()
        val archive = Json { classDiscriminator = "kind"; ignoreUnknownKeys = true }.decodeFromString(MatchArchive.serializer(), text)
        val original = archive.articles.toList()
        val model = MatchReplayModel(archive,
            fastMoveTypesByName = mapOf("SPARK" to PokemonType.ELECTRIC, "VOLTSWITCH" to PokemonType.ELECTRIC),
            fastMovesBySpeciesName = mapOf("Raichu (Alolan)" to listOf(Move("Spark", PokemonType.ELECTRIC, 4, 7, true, 2)),
                "Raichu" to listOf(Move("Volt Switch", PokemonType.ELECTRIC, 12, 16, true, 4))))
        val go = requireNotNull(model.matchStartNanos)
        assertEquals("Raichu (Alolan)", model.sceneAt(go).player?.speciesName)
        assertEquals(1, model.sceneAt(go).player?.playerTeamSlot)
        val allScenes = generateSequence(go) { it + 100_000_000L }.takeWhile { it <= model.endNanos }.map(model::sceneAt).toList()
        assertEquals(setOf("Raichu (Alolan)"), allScenes.mapNotNull { it.player?.speciesName }.toSet())
        val attacks = allScenes.flatMap { it.fastMoveActions }.filter { it.side == "PLAYER" }
        assertTrue(attacks.isNotEmpty())
        assertEquals(setOf("Spark"), attacks.map { it.moveName }.toSet())
        assertEquals(original, archive.articles)
    }

    @Test fun `same label replacement has its own moves hp and energy and Alolan return restores its energy`() {
        val raw = team() + listOf(
            article("a1", ArchivedFastMoveUseObserved("a1", "PLAYER", "OPPONENT", "alolan", "Raichu (Alolan)", listOf("HP_BORDER_PULSE"), "TEST"), 5),
            article("a-energy1", ArchivedFastMoveEnergyDerived("PLAYER", "Spark", 1, 7, 7, "OBSERVED_USE"), 5).copy(predecessorIds = listOf("a1")),
            article("a2", ArchivedFastMoveUseObserved("a2", "PLAYER", "OPPONENT", "alolan", "Raichu (Alolan)", listOf("HP_BORDER_PULSE"), "TEST"), 6),
            article("a-energy2", ArchivedFastMoveEnergyDerived("PLAYER", "Spark", 1, 7, 7, "OBSERVED_USE"), 6).copy(predecessorIds = listOf("a2")),
            article("hp-a", ArchivedActiveHpBarMeasured("PLAYER", 0, 0, 200, 10, .6f), 6),
            badge("k-entry", "Raichu", 20, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"),
            article("k-label1", ArchivedPlayerChargeMoveLabelRead(1, "BRICK BREAK"), 21),
            article("k-label2", ArchivedPlayerChargeMoveLabelRead(1, "BRICK BREAK"), 22),
            article("k1", ArchivedFastMoveUseObserved("k1", "PLAYER", "OPPONENT", "ordinary", "Raichu", listOf("HP_BORDER_PULSE"), "TEST"), 23),
            article("k-energy", ArchivedFastMoveEnergyDerived("PLAYER", "Volt Switch", 1, 16, 16, "OBSERVED_USE"), 23).copy(predecessorIds = listOf("k1")),
            badge("a-entry", "Raichu", 40, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"),
            article("a-label1", ArchivedPlayerChargeMoveLabelRead(1, "TRAILBLAZE"), 41),
            article("a-label2", ArchivedPlayerChargeMoveLabelRead(1, "TRAILBLAZE"), 42),
        )
        val model = MatchReplayModel(MatchArchive(matchId = "forms", articles = raw), fastMovesBySpeciesName = mapOf(
            "Raichu (Alolan)" to listOf(Move("Spark", PokemonType.ELECTRIC, 4, 7, true, 2)),
            "Raichu" to listOf(Move("Volt Switch", PokemonType.ELECTRIC, 12, 16, true, 4))))
        assertEquals("Raichu (Alolan)", model.sceneAt(10_000_000_000L).player?.speciesName)
        assertEquals(14, model.sceneAt(10_000_000_000L).playerEnergy?.recordedEnergy)
        val ordinary = model.sceneAt(25_000_000_000L)
        assertEquals("Raichu", ordinary.player?.speciesName)
        assertEquals(2, ordinary.player?.playerTeamSlot)
        assertEquals(16, ordinary.playerEnergy?.recordedEnergy)
        assertEquals(1f, ordinary.playerHp?.filledFraction)
        val returning = model.sceneAt(43_000_000_000L)
        assertEquals("Raichu (Alolan)", returning.player?.speciesName)
        assertEquals(14, returning.playerEnergy?.recordedEnergy)
    }

    @Test fun `Alolan faint never marks ordinary replacement or mirror opponent fainted`() {
        val model = MatchReplayModel(MatchArchive(matchId = "forms", articles = team() + listOf(
            article("mirror", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Raichu", 26), 3, "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
            article("ko", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Raichu", 26, "POKE_BALL_DECREASE"), 10),
            badge("lingering", "Raichu", 11),
            badge("replacement", "Raichu", 14, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"),
            article("resolved", ArchivedPlayerActiveIdentityResolved("Raichu", "Raichu", 26, 2,
                14_000_000_000L, "PLAYER_SPECIES_AND_UNIQUE_ROSTER_SLOT"), 14, "PLAYER_ACTIVE_IDENTITY_INFERENCE"),
        )))
        assertTrue(model.sceneAt(11_000_000_000L).player!!.isFainted)
        assertFalse(model.sceneAt(11_000_000_000L).opponent!!.isFainted)
        assertEquals("Raichu", model.sceneAt(15_000_000_000L).player?.speciesName)
        assertFalse(model.sceneAt(15_000_000_000L).player!!.isFainted)
    }
}
