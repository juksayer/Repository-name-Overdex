package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayCombatantLifetimeTest {
    // Moondrop 2026-10-13 17:34. Original testimony/ids, time relative to GO;
    // only identity, control, move and lifecycle articles retained, no media/OCR geometry.
    private fun recorded() = MatchArchiveSerializer.deserialize(checkNotNull(
        javaClass.getResource("/replay/camerupt-galvantula-return.json")
    ).readText())

    @Test fun `returning nicknamed Galvantula replaces fainted Camerupt at its entry`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("VOLTSWITCH" to PokemonType.ELECTRIC))
        val before = model.sceneAt(123_500_000_000)
        assertEquals("Camerupt", before.player?.speciesName)
        assertTrue(before.player!!.isFainted)
        assertEquals(0, before.playerEnergy?.recordedEnergy)
        val entry = model.sceneAt(124_100_000_000)
        assertEquals("Galvantula", entry.player?.speciesName)
        assertFalse(entry.player!!.isFainted)
        assertTrue(entry.player.identityBasis.contains("BOTH CHARGE LABELS"))
        val later = model.sceneAt(141_100_000_000)
        assertEquals("Galvantula", later.player?.speciesName)
        assertEquals("Jolteon", later.opponent?.speciesName)
        val attack = later.fastMoveActions.single { it.side == "PLAYER" }
        assertEquals("Volt Switch", attack.moveName)
        assertEquals(PokemonType.ELECTRIC, attack.type)
        assertEquals(before, model.sceneAt(123_500_000_000)) // reverse scrub restores the KO
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `unresolved replacement cannot make the fallen sprite attack`() {
        val archive = recorded().let { it.copy(articles = it.articles.filterNot { a ->
            a.payload is ArchivedPlayerTeamSlotConfigured
        }) }
        val scene = MatchReplayModel(archive).sceneAt(141_100_000_000)
        assertTrue(scene.player!!.isFainted)
        assertTrue(scene.fastMoveActions.none { it.side == "PLAYER" })
        assertEquals(0, scene.playerEnergy?.recordedEnergy)
    }

    @Test fun `a single label or ambiguous team pair cannot resolve a nickname`() {
        val archive = recorded()
        val team = archive.articles.mapNotNull { it.payload as? ArchivedPlayerTeamSlotConfigured }
        val oneLabel = archive.articles.filterNot {
            (it.payload as? ArchivedPlayerChargeMoveLabelRead)?.slot == 2
        }
        assertTrue(ReplayPlayerIdentityRecovery.recover(oneLabel, team).isEmpty())
        val ambiguous = team + team.first().copy(slot = 4, speciesName = "Another Pokemon", speciesId = 999)
        assertTrue(ReplayPlayerIdentityRecovery.recover(archive.articles, ambiguous).none { it.speciesName == "Galvantula" })
        val observation = ReplayPlayerIdentityRecovery.recover(archive.articles, team)
            .last { it.speciesName == "Galvantula" }
        assertTrue(observation.evidenceArticleIds.size >= 3)
    }

    @Test fun `the charge icon can resolve from raw announcement without an energy spend`() {
        val charge = Move("Earth Power", PokemonType.GROUND, 90, 55, false)
        val archive = MatchArchive(matchId = "test", articles = listOf(
            at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Jolteon", 135), 0),
            at("ready", ArchivedGetReadyWitnessed, 1),
            at("raw", ArchivedRawText("Camerupt used Earth Power!"), 8).copy(sourceId = "ANNOUNCEMENT_WITNESS"),
            at("used", ArchivedChargeMoveUsedAnnounced, 8),
        ))
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = mapOf("Camerupt" to listOf(charge)))
        val preparing = model.sceneAt(4 * SECOND).chargedMoveActions.single()
        assertEquals("Earth Power", preparing.moveName)
        assertEquals(PokemonType.GROUND, preparing.type)
        assertEquals(ReplayChargePhase.CHARGING, preparing.phase)
        val impact = model.sceneAt(8 * SECOND).chargedMoveActions.single()
        assertEquals(preparing.type, impact.type)
        assertEquals(ReplayChargePhase.STRIKING, impact.phase)
        assertEquals(0, model.sceneAt(9 * SECOND).playerSpentEnergy)
        assertFalse(archive.articles.any { it.payload is ArchivedChargedMoveEnergySpent })
    }

    @Test fun `a dead attacker cannot produce a charged projectile`() {
        val model = MatchReplayModel(MatchArchive(matchId = "test", articles = listOf(
            at("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            at("ko", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "BALLS"), 2),
            at("stale", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 3),
            at("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "STALE"), 8),
        )))
        assertTrue(model.sceneAt(8 * SECOND).chargedMoveActions.isEmpty())
        assertEquals(0, model.sceneAt(8 * SECOND).playerEnergy?.spent)
    }

    @Test fun `label touch and Get Ready name the preparation but cannot invent a completed impact`() {
        val charge = Move("Lunge", PokemonType.BUG, 60, 45, false)
        val articles = listOf(
            at("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Galvantula", 596), 0),
            at("label", ArchivedPlayerChargeMoveLabelRead(2, "LUNGE"), 1),
            at("touch", ArchivedPlayerChargeButtonTouchWitnessed(2, 800f, 2000f, 1080, 2400, "TOUCH"), 2),
            at("ready", ArchivedGetReadyWitnessed, 3),
        )
        fun model(rows: List<ArchivedRealityArticle>) = MatchReplayModel(MatchArchive(matchId = "test", articles = rows),
            chargedMovesBySpeciesName = mapOf("Galvantula" to listOf(charge)))
        val action = model(articles).sceneAt(5 * SECOND).chargedMoveActions.single()
        assertEquals(PokemonType.BUG, action.type)
        assertEquals("Lunge", action.moveName)
        assertTrue("PLAYER_BUTTON_LABEL_AND_TOUCH" in action.evidenceKinds)
        assertTrue(model(articles).sceneAt(14 * SECOND).chargedMoveActions.isEmpty())
        assertTrue(model(articles.dropLast(1)).sceneAt(5 * SECOND).chargedMoveActions.isEmpty())
        assertNull(model(articles.filterNot { it.articleId == "touch" }).sceneAt(5 * SECOND).chargedMoveActions.single().type)
        val blank = articles + at("blank", ArchivedPlayerChargeMoveLabelRead(2, ""), 2)
        assertNull(model(blank).sceneAt(5 * SECOND).chargedMoveActions.single().type)
    }

    private fun at(id: String, payload: ArchivedTestimonyPayload, second: Long) = ArchivedRealityArticle(
        id, "test", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), second * SECOND)
    private companion object { const val SECOND = 1_000_000_000L }
}
