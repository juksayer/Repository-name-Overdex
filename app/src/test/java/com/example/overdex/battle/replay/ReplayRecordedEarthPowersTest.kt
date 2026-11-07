package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReplayRecordedEarthPowersTest {
    private val reference by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
            File("src/main/assets/gamemaster.json").readText())
    }

    @Test fun `recorded Earth Powers recovers exactly one player Earth Power at its original raw source time`() {
        val archive = recordedArchive()
        val original = MatchArchiveSerializer.serialize(archive)
        val raw = archive.articles.single { it.articleId == EARTH_POWERS_ARTICLE }
        assertEquals("ANNOUNCEMENT_WITNESS", raw.sourceId)
        assertEquals("Camerupt used Earth Powers", (raw.payload as ArchivedRawText).value)
        assertEquals(EARTH_POWERS_AT, raw.monotonicTimeNanos)
        assertEquals(EARTH_POWERS_AT, archive.articles.single {
            it.payload is ArchivedChargeMoveUsedAnnounced
        }.monotonicTimeNanos)

        val model = model(archive)
        val scene = model.sceneAt(EARTH_POWERS_AT)
        assertEquals("Camerupt", scene.player!!.speciesName)
        assertEquals("Quagsire", scene.opponent!!.speciesName)
        val charge = scene.chargedMoveActions.single()
        assertEquals("PLAYER", charge.side)
        assertEquals("Earth Power", charge.moveName)
        assertEquals(PokemonType.GROUND, charge.type)
        assertEquals(EARTH_POWERS_AT, charge.startedAtNanos)
        assertEquals(ReplayChargePhase.STRIKING, charge.phase)
        assertTrue(charge.evidenceKinds.containsAll(setOf("NAMED_CHARGED_MOVE",
            "ARCHIVED_ANNOUNCEMENT_AND_REFERENCE_MOVE", "OCR_SINGLE_CHARACTER_REPAIR")))

        // The typed generic cue and raw named cue are one recorded episode.
        val sounds = model.attackSoundEvents.filter { it.charged }
        assertEquals(1, sounds.size)
        assertEquals("Earth Power", sounds.single().moveName)
        assertEquals(EARTH_POWERS_AT, sounds.single().atNanos)
        assertEquals(0, scene.playerSpentEnergy)
        assertEquals(0, scene.opponentSpentEnergy)
        assertFalse(archive.articles.any { it.payload is ArchivedChargedMoveEnergySpent })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `unrecorded finishing moves and final faint stay unknown despite known legal moves and win`() {
        val archive = recordedArchive()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val readyAt = archive.articles.single { it.payload is ArchivedGetReadyWitnessed }.monotonicTimeNanos!!
        val endedAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val lastHp = archive.articles.filter {
            (it.payload as? ArchivedActiveHpBarMeasured)?.side == "OPPONENT"
        }.maxBy { it.monotonicTimeNanos!! }
        assertEquals(.07978723f, (lastHp.payload as ArchivedActiveHpBarMeasured).filledFraction!!, 0f)
        assertEquals(2, archive.articles.count { it.payload is ArchivedActivePokemonFaintedWitnessed })
        assertTrue(archive.articles.none {
            (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.speciesName == "Obstagoon"
        })

        val preparing = model.sceneAt(readyAt).chargedMoveActions.single()
        assertEquals("PLAYER", preparing.side)
        assertEquals(ReplayChargePhase.CHARGING, preparing.phase)
        assertNull("Get Ready and the legal pool do not identify an unrecorded finishing move", preparing.moveName)
        for (cursor in generateSequence(model.startNanos) { previous ->
            (previous + 100_000_000L).takeIf { it <= model.endNanos }
        }) {
            val scene = model.sceneAt(cursor)
            assertEquals(0, scene.playerSpentEnergy)
            assertEquals(0, scene.opponentSpentEnergy)
            assertTrue(scene.chargedMoveActions.all { it.moveName == null || it.moveName == "Earth Power" })
            assertTrue(scene.chargedMoveActions.filter { it.moveName != null }.all {
                it.side == "PLAYER" && it.startedAtNanos == EARTH_POWERS_AT
            })
            if (scene.opponent?.speciesName == "Obstagoon") assertFalse(scene.opponent.isFainted)
        }
        assertEquals(.07978723f, model.sceneAt(lastHp.monotonicTimeNanos!!).opponentHp!!.filledFraction!!, .000001f)
        val result = model.sceneAt(endedAt)
        assertEquals("Obstagoon", result.opponent!!.speciesName)
        assertFalse(result.opponent.isFainted)
        assertEquals(endedAt, result.opponent.defeatedAtNanos)
        assertNull("WIN hides stale HP without asserting a zero measurement", result.opponentHp)
        assertEquals(listOf("Earth Power"), model.attackSoundEvents.filter { it.charged }.map { it.moveName })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    private fun model(archive: MatchArchive): MatchReplayModel {
        val species = listOf("Turtonator", "Camerupt", "Swampert", "Quagsire", "Obstagoon")
        return MatchReplayModel(archive, chargedMovesBySpeciesName = species.associateWith(::legalMoves))
    }

    private fun legalMoves(species: String): List<Move> = reference.pokemon.first { it.speciesName == species }
        .chargedMoves.map { id -> reference.moves.first { it.moveId == id }.let {
            Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power, kotlin.math.abs(it.energy), false)
        } }

    private fun recordedArchive(): MatchArchive = MatchArchiveSerializer.deserialize(checkNotNull(
        javaClass.getResource("/replay/nov7-finishing-earth-powers.json")).readText())

    private companion object {
        // Reduced 42-Article subset of 34d20b55, not a complete match export.
        // Every retained Article (including publication order/custody links) is
        // unchanged. No media or synthetic preparation/finishing event is added.
        const val EARTH_POWERS_ARTICLE = "8792e4d6-7b2b-4ecb-8a36-813c2e975f0a"
        const val EARTH_POWERS_AT = 147_592_300_559_104L
    }
}
