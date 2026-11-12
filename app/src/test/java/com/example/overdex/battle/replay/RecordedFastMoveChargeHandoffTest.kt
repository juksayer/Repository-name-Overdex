package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The actual next Incinerate was supported, but its entire sprite bob was covered by a prior charge. */
class RecordedFastMoveChargeHandoffTest {
    @Test fun `recorded completed charge lands before the next supported Incinerate sprite action`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, fastMovesBySpeciesName = moves)
        // Read lazy duration before any scene: its display projection must not
        // recursively change the source charge windows used to schedule attacks.
        assertTrue(model.endNanos > FAST_IMPACT)
        val release = model.sceneAt(CHARGE_RELEASE).chargedMoveActions.single { it.side == "PLAYER" }
        assertEquals(CHARGE_RELEASE, release.startedAtNanos)
        assertEquals(ReplayChargePhase.STRIKING, release.phase)
        assertTrue("PRESENTATION_FIT_BEFORE_NEXT_FAST_MOVE" in release.evidenceKinds)
        assertTrue(ReplayChargePose.at(release, player = true).risePixels < -30f)
        val flightAt = CHARGE_RELEASE + ((FAST_START - CHARGE_RELEASE) * .9).toLong()
        val flight = model.sceneAt(flightAt).chargedMoveActions.single { it.side == "PLAYER" }
        assertTrue("The existing throw retains a visible flight before handoff",
            ReplayChargePose.at(flight, player = true).projectileProgress > .4f)
        assertTrue(model.sceneAt(FAST_START - 1).chargedMoveActions.any { it.side == "PLAYER" })

        for (elapsed in listOf(0L, 150_000_000L, 270_000_000L)) {
            val scene = model.sceneAt(FAST_START + elapsed)
            assertTrue("The old charged pose must not mask any of the new bob",
                scene.chargedMoveActions.none { it.side == "PLAYER" })
            val fast = scene.fastMoveActions.single { FAST_USE_ID in it.sourceArticleIds }
            assertEquals("Incinerate", fast.moveName)
            assertEquals(FAST_START, fast.startedAtNanos)
            assertEquals(FAST_IMPACT, fast.impactAtNanos)
            assertEquals(2_500_000_000L, fast.durationNanos)
            assertEquals(setOf(FAST_IMPACT), fast.observedAtNanos)
            assertEquals(SOURCE_IDS, fast.sourceArticleIds)
        }
        assertEquals(.25f, model.sceneAt(FAST_IMPACT - 100_000_000L).opponentHp!!.filledFraction!!, .0001f)
        val impact = model.sceneAt(FAST_IMPACT + 20_000_000L)
        assertEquals(.14814815f, impact.opponentHp!!.filledFraction!!, .0001f)
        assertTrue(impact.fastMoveActions.any { FAST_USE_ID in it.sourceArticleIds })
        assertEquals(release, model.sceneAt(CHARGE_RELEASE).chargedMoveActions.single { it.side == "PLAYER" })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `handoff caps only the old impact hold when the supported next attack begins after landing`() {
        val afterImpactStart = 11_850_000_000L
        val held = synthetic(afterImpactStart)
        assertEquals(1f, held.sceneAt(afterImpactStart - 1).chargedMoveActions.single().progress, 0f)
        assertTrue(held.sceneAt(afterImpactStart).chargedMoveActions.isEmpty())
        assertEquals(1f, held.sceneAt(11_800_000_000L).chargedMoveActions.single().progress, 0f)
    }

    private fun recorded() = MatchArchiveSerializer.deserialize(File(
        "src/androidTest/assets/replay/final-dragon-pulse-20261111.json").readText())

    private fun synthetic(fastStart: Long): MatchReplayModel {
        fun at(id: String, payload: ArchivedTestimonyPayload, time: Long) = ArchivedRealityArticle(
            id, "handoff", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), time)
        val archive = MatchArchive(matchId = "handoff", articles = listOf(
            at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 0),
            at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Greninja", 658), 0),
            at("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Dragon Pulse", 60, "TEST"), 10_000_000_000L),
            at("fast", ArchivedFastMoveUseObserved("fast", "PLAYER", "OPPONENT", null,
                "Turtonator", listOf("HP_BORDER_PULSE"), "TEST"), fastStart + 2_500_000_000L),
            at("identified", ArchivedFastMoveIdentified("PLAYER", "Turtonator", "Incinerate",
                2_500_000_000L, cadenceSampleCount = 3, basis = "TEST"), 0),
        ))
        return MatchReplayModel(archive, fastMovesBySpeciesName = moves)
    }

    companion object {
        private const val CHARGE_RELEASE = 30875942239455L
        private const val FAST_START = 30877265086224L
        private const val FAST_IMPACT = 30879765086224L
        private const val FAST_USE_ID = "c8e5b483-f241-475f-be19-ce7819378edc"
        private val SOURCE_IDS = setOf("b47f8543-0a16-4726-9059-aac5d1b19c29",
            "9b1c2428-f3c9-48f7-93cf-41a3dccf4f52", FAST_USE_ID,
            "39058f8f-5acf-4cf6-b9a1-6b6255f8ab7c", "e4697783-08e2-4a3c-b5ca-578410a22f30")
        private val moves = mapOf("Turtonator" to listOf(Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5)))
    }
}
