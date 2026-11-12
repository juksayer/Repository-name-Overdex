package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source-preserving metadata excerpt from the November 11 Moondrop 87612e9f battle. */
class RecordedFinalDragonPulseReplayTest {
    private val archive = MatchArchiveSerializer.deserialize(File(
        "src/androidTest/assets/replay/final-dragon-pulse-20261111.json").readText())
    private val finalCharge = archive.articles.single { it.articleId == FINAL_CHARGE_ID }
    private val chargeAt = finalCharge.monotonicTimeNanos!!
    private val model = MatchReplayModel(archive,
        chargedMoveTypesByName = mapOf("DRAGONPULSE" to PokemonType.DRAGON))

    @Test fun `final named Dragon Pulse retains one player preparation strike and impact without a Get Ready cue`() {
        val progress = archive.articles.single { it.articleId == FINAL_PROGRESS_ID }
        assertEquals("GREAT!", (progress.payload as ArchivedRawText).value)
        assertTrue(archive.articles.none {
            it.payload is ArchivedGetReadyWitnessed && it.monotonicTimeNanos!! > chargeAt - 15_000_000_000L
        })
        val preparing = model.sceneAt(progress.monotonicTimeNanos!! + 100_000_000L)
        val preparation = preparing.chargedMoveActions.single { it.side == "PLAYER" }
        assertEquals("Turtonator", preparing.player?.speciesName)
        assertFalse(preparing.player!!.isFainted)
        assertEquals("Dragon Pulse", preparation.moveName)
        assertEquals(PokemonType.DRAGON, preparation.type)
        assertEquals(ReplayChargePhase.CHARGING, preparation.phase)

        val striking = model.sceneAt(chargeAt + 1_200_000_000L)
        val action = striking.chargedMoveActions.single { it.side == "PLAYER" }
        assertEquals(1, striking.chargedMoveActions.size)
        assertEquals(ReplayChargePhase.STRIKING, action.phase)
        assertTrue(ReplayChargePose.at(action, player = true).risePixels < -45f)
        val landing = model.sceneAt(chargeAt + 1_800_000_000L).chargedMoveActions.single { it.side == "PLAYER" }
        val landed = ReplayChargePose.at(landing, player = true)
        assertEquals(0f, landed.risePixels, .001f)
        assertEquals(1f, landed.projectileProgress, .001f)
        assertTrue(model.sceneAt(chargeAt + 2_000_000_000L).chargedMoveActions.isEmpty())
    }

    @Test fun `showing the final charge never invents a finishing fast move faint or empty HP`() {
        val original = MatchArchiveSerializer.serialize(archive)
        val before = model.sceneAt(chargeAt + 1_200_000_000L)
        val impact = model.sceneAt(chargeAt + 1_800_000_000L)
        assertEquals("Leavanny", impact.opponent?.speciesName)
        assertFalse(impact.opponent!!.isFainted)
        assertEquals(.25f, impact.opponentHp!!.filledFraction!!, .0001f)
        assertTrue((0..120).all { step ->
            model.sceneAt(chargeAt + step * 100_000_000L).fastMoveActions.none { it.side == "PLAYER" }
        })
        val endAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val ended = model.sceneAt(endAt)
        assertEquals("WIN", ended.outcome)
        assertFalse("The observed result alone is not faint testimony", ended.opponent!!.isFainted)
        assertNotNull(ended.opponent.defeatedAtNanos)
        assertEquals(before, model.sceneAt(chargeAt + 1_200_000_000L))
        assertEquals(30905912675457L, finalCharge.monotonicTimeNanos)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    companion object {
        private const val FINAL_CHARGE_ID = "3429122f-72e6-416f-9a48-74400467ad1f"
        private const val FINAL_PROGRESS_ID = "670fdb3c-0535-465f-8efe-0cf12870a12c"
    }
}
