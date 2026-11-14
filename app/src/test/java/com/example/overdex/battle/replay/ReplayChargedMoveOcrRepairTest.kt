package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReplayChargedMoveOcrRepairTest {
    private val reference by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
    }

    @Test fun `Candela final FouPay reading supplies a Foul Play icon without manufacturing archived energy`() {
        // Candela 2026-10-28: raw announcement at GO +99.308 seconds. This
        // minimal fixture retains that spelling/time with synthetic preparation context.
        val archive = archive("Scrafty used FouPay!")
        val before = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val preparing = model.sceneAt(96 * SECOND).chargedMoveActions.single()
        assertEquals("Foul Play", preparing.moveName)
        assertEquals(PokemonType.DARK, preparing.type)
        assertEquals(ReplayChargePhase.CHARGING, preparing.phase)
        val impact = model.sceneAt(CHARGE_AT).chargedMoveActions.single()
        assertEquals("Foul Play", impact.moveName)
        assertEquals("PLAYER", impact.side)
        assertEquals(ReplayChargePhase.STRIKING, impact.phase)
        assertTrue("OCR_MISSING_INTERNAL_CHARACTERS" in impact.evidenceKinds)
        assertEquals(0, model.sceneAt(CHARGE_AT + SECOND).playerSpentEnergy)
        assertFalse(archive.articles.any { it.payload is ArchivedChargedMoveEnergySpent })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals("Scrafty used FouPay!", (archive.articles.single { it.articleId == "raw" }.payload as ArchivedRawText).value)
    }

    @Test fun `repaired announcement does not duplicate an accepted charged spend or repeated raw reading`() {
        val archive = archive("Scrafty used FouPay!").let { original -> original.copy(articles = original.articles + listOf(
            at("repeat", ArchivedRawText("Scrafty used FouPlay!"), CHARGE_AT + 200_000_000L, "ANNOUNCEMENT_WITNESS"),
            at("spend", ArchivedChargedMoveEnergySpent("PLAYER", "Scrafty", "Foul Play", 40, "RECORDED"), CHARGE_AT),
        )) }
        val before = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        assertEquals("Foul Play", model.sceneAt(CHARGE_AT + 250_000_000L).chargedMoveActions.single().moveName)
        assertEquals(40, model.sceneAt(CHARGE_AT + SECOND).playerSpentEnergy)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `replay does not force clipped illegal ambiguous or mirror repairs`() {
        for (text in listOf("Scrafty used FoulPl!", "Flareon used FouPay!", "Scrafty used FauPay!")) {
            assertTrue(text, model(archive(text)).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
        }
        val mirror = archive("Scrafty used FouPay!", opponent = "Scrafty").let { it.copy(articles = it.articles.filterNot { a -> a.articleId == "ready" }) }
        assertTrue(model(mirror).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
        val ambiguous = MatchReplayModel(archive("Scrafty used FouPay!"), chargedMovesBySpeciesName = mapOf(
            "Scrafty" to listOf("Foul Play", "Foul Pay").map { Move(it, PokemonType.DARK, 60, 40, false) },
            "Flareon" to legalMoves("Flareon"),
        ))
        assertTrue(ambiguous.sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
    }

    @Test fun `mirror charge uses independent preparation but rejects contradictory cues and switches`() {
        val mirror = archive("Scrafty used FouPay!", opponent = "Scrafty")
        val action = model(mirror).sceneAt(CHARGE_AT).chargedMoveActions.single { it.moveName != null }
        assertEquals("PLAYER", action.side)
        assertEquals("Foul Play", action.moveName)
        assertTrue("SIDE_FROM_INDEPENDENT_CHARGE_PREPARATION" in action.evidenceKinds)
        val conflicting = mirror.copy(articles = mirror.articles + at("incoming", ArchivedAttackIncoming, 93 * SECOND))
        assertTrue(model(conflicting).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
        val changed = mirror.copy(articles = mirror.articles + listOf(
            at("away", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Flareon"), 94 * SECOND),
            at("back", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Scrafty"), 95 * SECOND)))
        assertTrue(model(changed).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
    }

    @Test fun `mirror preparation expires and late unrelated faint does not change the active attacker`() {
        val mirror = archive("Scrafty used FouPay!", opponent = "Scrafty")
        val stale = mirror.copy(articles = mirror.articles.map {
            if (it.articleId == "ready") it.copy(monotonicTimeNanos = 80 * SECOND) else it })
        assertTrue(model(stale).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
        val unrelated = mirror.copy(articles = mirror.articles + at("late-old-faint",
            ArchivedActivePokemonFaintedWitnessed("PLAYER", "Turtonator", basis = "BALL_COUNT"), 94 * SECOND))
        assertEquals("Foul Play", model(unrelated).sceneAt(CHARGE_AT).chargedMoveActions.single { it.moveName != null }.moveName)
        val active = mirror.copy(articles = mirror.articles + at("active-faint",
            ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Scrafty", basis = "BALL_COUNT"), 94 * SECOND))
        assertTrue(model(active).sceneAt(CHARGE_AT).chargedMoveActions.none { it.moveName != null })
    }

    private fun model(archive: MatchArchive) = MatchReplayModel(archive, chargedMovesBySpeciesName =
        listOf("Scrafty", "Flareon").associateWith(::legalMoves))

    private fun legalMoves(species: String): List<Move> = reference.pokemon.first { it.speciesName == species }
        .chargedMoves.map { id -> reference.moves.first { it.moveId == id }.let {
            Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power, kotlin.math.abs(it.energy), false)
        } }

    private fun archive(text: String, opponent: String = "Flareon") = MatchArchive(matchId = "candela-charge", articles = listOf(
        at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Scrafty", 560), 0),
        at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", opponent), 0),
        at("ready", ArchivedGetReadyWitnessed, 92 * SECOND),
        at("raw", ArchivedRawText(text), CHARGE_AT, "ANNOUNCEMENT_WITNESS"),
        at("used", ArchivedChargeMoveUsedAnnounced, CHARGE_AT),
        at("result", ArchivedMatchEnded("WIN"), 104_229_000_000L),
    ))

    private fun at(id: String, payload: ArchivedTestimonyPayload, nanos: Long, source: String = "TEST") =
        ArchivedRealityArticle(id, "candela-charge", 0, 0, source, payload, emptyList(), null, null, emptyList(), nanos)

    private companion object {
        const val SECOND = 1_000_000_000L
        const val CHARGE_AT = 99_308_000_000L
    }
}
