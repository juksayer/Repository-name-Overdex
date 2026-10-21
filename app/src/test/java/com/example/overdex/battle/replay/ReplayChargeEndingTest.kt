package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayChargeEndingTest {
    @Test fun `finishing Earth Power survives the result and lands before the KO pose and zero hp`() {
        val archive = MatchArchive(matchId = "test", articles = listOf(
            timed("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            timed("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 0),
            timed("go", ArchivedMatchStarted, 1),
            timed("ready", ArchivedGetReadyWitnessed, 3),
            timed("earth", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "ANNOUNCEMENT"), 10),
            timed("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Vaporeon", 134, "POKE_BALLS"), 10)
                .copy(monotonicTimeNanos = 10 * SECOND + 100_000_000L),
            timed("result", ArchivedMatchEnded("WIN"), 10).copy(monotonicTimeNanos = 10 * SECOND + 200_000_000L),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, chargedMoveTypesByName = mapOf("EARTHPOWER" to PokemonType.GROUND))
        val before = model.sceneAt(11 * SECOND)
        assertEquals("Earth Power", before.chargedMoveActions.single().moveName)
        assertEquals(PokemonType.GROUND, before.chargedMoveActions.single().type)
        assertEquals(1f, before.opponentHp!!.filledFraction)
        val impact = 10 * SECOND + 1_800_000_000L
        assertEquals(impact, before.opponent!!.knockoutPresentationAtNanos)
        assertEquals(10 * SECOND + 100_000_000L, before.opponent.faintedAtNanos)
        assertNull(ReplayFaintPose.at(before.opponent.knockoutPresentationAtNanos, 11 * SECOND, false))
        val landed = model.sceneAt(impact)
        assertEquals(0f, landed.opponentHp!!.filledFraction)
        assertEquals(1f, landed.opponentHp.damageTrailFraction)
        assertEquals(1f, landed.chargedMoveActions.single().progress, 0f)
        assertEquals(1f, ReplayChargePose.at(landed.chargedMoveActions.single(), true).projectileProgress, 0f)
        assertTrue(model.endNanos >= impact + ReplayFaintPose.DURATION_NANOS)
        assertEquals(before, model.sceneAt(11 * SECOND))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `a result does not authorize starting a new charge or completing an open QTE`() {
        val model = model(timed("ready", ArchivedGetReadyWitnessed, 3),
            timed("result", ArchivedMatchEnded("WIN"), 5), timed("late", spend("PLAYER"), 8))
        assertTrue(model.sceneAt(6 * SECOND).chargedMoveActions.isEmpty())
        assertTrue(model.sceneAt(8 * SECOND).chargedMoveActions.isEmpty())
    }

    @Test fun `latest Camerupt recording retains known charges without guessing the missing final move from its button label`() {
        // Original IDs/payloads; only the clock origin is shifted to GO for readability.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/camerupt-final-charge-missed-20261013.json")).readText())
        val model = MatchReplayModel(archive)
        assertEquals("Rock Tomb", model.sceneAt(43 * SECOND).chargedMoveActions.single().moveName)
        assertTrue(archive.articles.any { (it.payload as? ArchivedPlayerChargeMoveLabelRead)?.text == "EARTH POWER" })
        assertFalse(archive.articles.any { (it.payload as? ArchivedChargedMoveEnergySpent)?.moveName == "Earth Power" })
        for (second in 53L..65L) assertTrue(model.sceneAt(second * SECOND).chargedMoveActions.isEmpty())
        assertEquals("Vaporeon", model.sceneAt(66 * SECOND).opponent?.speciesName)
    }

    @Test fun `recorded Flareon defeat ends the sprite pose without manufacturing KO evidence`() {
        val archive = recorded()
        val model = MatchReplayModel(archive)
        val at = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val before = model.sceneAt(at - 1)
        assertEquals("Flareon", before.opponent?.speciesName)
        assertTrue(before.opponentHp!!.filledFraction!! > 0.5f)
        val after = model.sceneAt(at + ReplayFaintPose.DURATION_NANOS)
        assertEquals(at, after.opponent?.defeatedAtNanos)
        assertFalse(after.opponent!!.isFainted) // no cause-of-defeat observation exists
        assertNull(after.opponentHp) // stale HP must not claim the loser is still fighting
        assertNotNull(after.playerHp)
        assertNull(after.player?.defeatedAtNanos)
        assertEquals(90f, ReplayFaintPose.at(at, at + ReplayFaintPose.DURATION_NANOS, false)!!.rotationDegrees)
        assertFalse(archive.articles.any { (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.speciesName == "Flareon" })
        assertNull(model.sceneAt(at - 1).opponent?.defeatedAtNanos) // reverse scrub
    }

    @Test fun `recorded Lunge has a visible preparation through the missing QTE interval`() {
        val model = MatchReplayModel(recorded())
        for (second in 74L..78L) {
            val charge = model.sceneAt(second * SECOND).chargedMoveActions.single()
            assertEquals("PLAYER", charge.side)
            assertEquals("Lunge", charge.moveName)
            assertEquals(ReplayChargePhase.CHARGING, charge.phase)
            assertTrue("RECONSTRUCTED_PREPARATION" in charge.evidenceKinds)
            assertNull(model.sceneAt(second * SECOND).qteMilestone)
        }
        assertEquals(ReplayChargePhase.STRIKING, model.sceneAt(80 * SECOND).chargedMoveActions.single().phase)
        // The earlier charge was witnessed but its performer was not resolved.
        val unknown = model.sceneAt(49 * SECOND).chargedMoveActions.single()
        assertEquals("UNKNOWN", unknown.side)
        assertNull(unknown.moveName)
    }

    @Test fun `both opening cues last until their own closing announcements and do not merge separate charges`() {
        val model = model(
            timed("ready", ArchivedGetReadyWitnessed, 1),
            timed("one", spend("PLAYER"), 10),
            timed("incoming", ArchivedAttackIncoming, 12),
            timed("two", spend("OPPONENT"), 20),
            timed("three", spend("PLAYER"), 22),
        )
        assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(8 * SECOND).chargedMoveActions.single().phase)
        assertEquals("OPPONENT", model.sceneAt(16 * SECOND).chargedMoveActions.single().side)
        assertEquals(ReplayChargePhase.STRIKING, model.sceneAt(22 * SECOND).chargedMoveActions.single().phase)
    }

    @Test fun `an unclosed opening cue expires without inventing a strike or QTE grades`() {
        val model = model(timed("ready", ArchivedGetReadyWitnessed, 1))
        assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(9 * SECOND).chargedMoveActions.single().phase)
        assertTrue(model.sceneAt(11 * SECOND).chargedMoveActions.isEmpty())
        assertNull(model.sceneAt(9 * SECOND).qteMilestone)
    }

    @Test fun `result only pose never kills the winner or changes ball counts`() {
        for ((result, defeated) in listOf("WIN" to "OPPONENT", "LOSS" to "PLAYER", "TIE" to null)) {
            val model = model(
                timed("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Pikachu", 25), 0),
                timed("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215), 0),
                timed("ended", ArchivedMatchEnded(result), 5),
            )
            val scene = model.sceneAt(model.endNanos)
            assertEquals(defeated == "PLAYER", scene.player!!.defeatedAtNanos != null)
            assertEquals(defeated == "OPPONENT", scene.opponent!!.defeatedAtNanos != null)
            assertFalse(scene.player.isFainted)
            assertFalse(scene.opponent.isFainted)
            assertNull(scene.opponentResources.pokeBalls)
            assertTrue(model.endNanos >= 5 * SECOND + ReplayFaintPose.DURATION_NANOS)
        }
    }

    @Test fun `confirmed faint still gets zero hp instead of result-only treatment`() {
        val model = model(
            timed("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215), 0),
            timed("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sneasel", 215, "POKE_BALLS"), 4),
            timed("result", ArchivedMatchEnded("WIN"), 5),
        )
        assertTrue(model.sceneAt(6 * SECOND).opponent!!.isFainted)
        assertEquals(0f, model.sceneAt(6 * SECOND).opponentHp!!.filledFraction)
    }

    @Test fun `charge poses are deterministic and land sharply`() {
        val action = ReplayChargedMoveAction("PLAYER", "Lunge", 0, 0.5f, elapsedNanos = 2 * SECOND,
            phase = ReplayChargePhase.CHARGING)
        val pose = ReplayChargePose.at(action, true)
        assertEquals(pose, ReplayChargePose.at(action, true))
        assertTrue(pose.risePixels < -20f)
        val apex = ReplayChargePose.at(action.copy(phase = ReplayChargePhase.STRIKING, progress = 0.78f), true)
        val landed = ReplayChargePose.at(action.copy(phase = ReplayChargePhase.STRIKING, progress = 0.95f), true)
        assertEquals(-48f, apex.risePixels, 0.001f)
        assertTrue(landed.risePixels > -1f)
    }

    @Test fun `QTE growth survives expired grade text and reverse scrubbing and resets for next charge`() {
        val archive = MatchArchive(matchId = "qte", articles = listOf(
            timed("ready", ArchivedGetReadyWitnessed, 1),
            timed("nice", motion(), 2), timed("great", motion(), 4), timed("excellent", motion(), 6),
            timed("pattern", ArchivedChargeMoveQteVibrationPatternInferred("PLAYER", 3, 4 * SECOND, "TEST"), 2)
                .copy(predecessorIds = listOf("nice", "great", "excellent")),
            timed("release", spend("PLAYER"), 8),
            timed("next", ArchivedGetReadyWitnessed, 11),
            timed("next-release", spend("PLAYER"), 19),
        ))
        val model = MatchReplayModel(archive, chargedMoveTypesByName = mapOf("LUNGE" to PokemonType.BUG))
        fun action(at: Long) = model.sceneAt(at).chargedMoveActions.single()
        assertEquals(0, action(SECOND).qteLevel)
        assertEquals(1, action(3 * SECOND).qteLevel)
        assertNull(model.sceneAt(3 * SECOND).qteMilestone)
        assertEquals(2, action(5 * SECOND).qteLevel)
        assertEquals(3, action(7 * SECOND).qteLevel)
        assertEquals(PokemonType.BUG, action(7 * SECOND).type)
        val release = action(8 * SECOND)
        assertEquals(3, release.qteLevel)
        assertEquals(ReplayChargePose.at(action(8 * SECOND - 1), true).iconScale,
            ReplayChargePose.at(release, true).iconScale, 0.0001f)
        assertEquals(0, action(12 * SECOND).qteLevel)
        assertEquals(1, action(3 * SECOND).qteLevel) // reverse scrubbing does not retain Excellent
    }

    @Test fun `milestone adds smooth persistent growth and the same icon launches only at the slam`() {
        val action = ReplayChargedMoveAction("PLAYER", "Lunge", 0, 0.5f,
            phase = ReplayChargePhase.CHARGING, type = PokemonType.BUG)
        val before = ReplayChargePose.at(action, true)
        val transition = ReplayChargePose.at(action.copy(qteLevel = 1, qteLevelProgress = 0f), true)
        val after = ReplayChargePose.at(action.copy(qteLevel = 1, qteLevelProgress = 1f), true)
        assertEquals(before.iconScale, transition.iconScale, 0.0001f)
        assertTrue(after.iconScale > before.iconScale)
        assertEquals(0f, after.projectileProgress, 0f)
        assertEquals(0f, ReplayChargePose.at(action.copy(phase = ReplayChargePhase.STRIKING, progress = .77f), true).projectileProgress, 0f)
        assertTrue(ReplayChargePose.at(action.copy(phase = ReplayChargePhase.STRIKING, progress = .89f), true).projectileProgress > 0f)
        assertNull(model(timed("ready", ArchivedGetReadyWitnessed, 1)).sceneAt(2 * SECOND).chargedMoveActions.single().type)
    }

    private fun motion() = ArchivedDeviceMotionPulseMeasured(36_000_000L, 1.2f, 0.7f, 8, "LINEAR_ACCELERATION")

    private fun recorded() = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource("/replay/charge-ending-20261013.json")).readText())
    private fun model(vararg articles: ArchivedRealityArticle) = MatchReplayModel(MatchArchive(matchId = "test", articles = articles.toList()))
    private fun spend(side: String) = ArchivedChargedMoveEnergySpent(side, "Example", "Lunge", 45, "TEST")
    private fun timed(id: String, payload: ArchivedTestimonyPayload, second: Long) = ArchivedRealityArticle(
        id, "test", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), second * SECOND)
    private companion object { const val SECOND = 1_000_000_000L }
}
