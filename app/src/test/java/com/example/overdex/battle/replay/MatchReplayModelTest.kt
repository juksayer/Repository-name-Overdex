package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedHpBarBorderPulse
import com.example.overdex.battle.archive.ArchivedActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedFastMoveEnergyDerived
import com.example.overdex.battle.archive.ArchivedFastMoveIdentified
import com.example.overdex.battle.archive.ArchivedFastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedPlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.archive.ArchivedPlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.archive.ArchivedPlayerTeamSlotConfigured
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedChargeMoveQteVibrationPatternInferred
import com.example.overdex.battle.archive.ArchivedChargeMoveUsedAnnounced
import com.example.overdex.battle.archive.ArchivedCountdownGlyphWitnessed
import com.example.overdex.battle.archive.ArchivedDeviceMotionPulseMeasured
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedVsScreenWitnessed
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReplayModelTest {
    @Test fun `replay exposes the witnessed match start separately from recording start`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("recording", com.example.overdex.battle.archive.ArchivedMatchRecordStarted, 1_000_000_000L),
            timed("go", ArchivedMatchStarted, 35_000_000_000L),
        ))

        val model = MatchReplayModel(archive)

        assertEquals(1_000_000_000L, model.startNanos)
        assertEquals(35_000_000_000L, model.matchStartNanos)
    }

    @Test fun `menus remain empty and countdown appears at its preserved time`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("recording", com.example.overdex.battle.archive.ArchivedMatchRecordStarted, 1_000_000_000L),
            timed("vs", ArchivedVsScreenWitnessed, 120_000_000_000L),
            timed(
                "three",
                ArchivedCountdownGlyphWitnessed("3", 0.95f, 1, basis = "TEST"),
                125_000_000_000L,
            ),
            article("player", "PLAYER", "Cresselia", 488, 126_000_000_000L),
            article("opponent", "OPPONENT", "Glaceon", 471, 126_000_000_000L),
        ))
        val model = MatchReplayModel(archive)

        assertEquals(null, model.sceneAt(60_000_000_000L).player)
        assertEquals(null, model.sceneAt(60_000_000_000L).opponent)
        assertEquals("3", model.sceneAt(125_400_000_000L).countdownGlyph)
        assertEquals(null, model.sceneAt(126_000_000_000L).countdownGlyph)
        assertEquals("Cresselia", model.sceneAt(126_000_000_000L).player?.speciesName)
        assertEquals("Glaceon", model.sceneAt(126_000_000_000L).opponent?.speciesName)
    }

    @Test fun `replay changes the right combatant at its witnessed switch time`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("p", "PLAYER", "GOURGEIST", 711, 10),
            article("o1", "OPPONENT", "VAPOREON", 134, 10),
            article("o2", "OPPONENT", "SNEASEL", 215, 20)
        )))

        assertEquals("VAPOREON", model.sceneAt(15).opponent?.speciesName)
        assertEquals("SNEASEL", model.sceneAt(20).opponent?.speciesName)
        assertEquals("GOURGEIST", model.sceneAt(15).player?.speciesName)
    }

    @Test fun `brief cry identity bracketed by stable badge OCR is omitted`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("before", "OPPONENT", "Sealeo", 364, 10_000_000_000L)
                .copy(sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE", confidence = 0.98f),
            article("cry", "OPPONENT", "Lillipup", 506, 11_000_000_000L)
                .copy(sourceId = "DECISIVE_BATTLE_CRY_SPECIES_WITNESS", confidence = 0.93f),
            article("after", "OPPONENT", "Sealeo", 364, 13_000_000_000L)
                .copy(sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE", confidence = 0.98f),
        )))

        assertEquals("Sealeo", model.sceneAt(11_500_000_000L).opponent?.speciesName)
    }

    @Test fun `matching active badges keep a mirror charged announcement unsided`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("team", ArchivedPlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711, "Incinerate", emptyList()), 1),
            article("player", "PLAYER", "Gourgeist", 711, 100)
                .copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE"),
            article("opponent", "OPPONENT", "Gourgeist", 711, 100)
                .copy(sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
            article("ambiguous-charge", "OPPONENT", "Gourgeist", 711, 200)
                .copy(sourceId = "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS")
        )))
        assertEquals(100L, model.sceneAt(300).player?.identityEstablishedAtNanos)
        assertEquals(100L, model.sceneAt(300).opponent?.identityEstablishedAtNanos)
    }

    @Test fun `opponent active badge outranks player roster membership for a named move`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("team", ArchivedPlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711, "Incinerate", emptyList()), 1),
            article("player", "PLAYER", "Camerupt", 323, 100)
                .copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE"),
            article("opponent", "OPPONENT", "Gourgeist", 711, 100)
                .copy(sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
            article("charge", "PLAYER", "Gourgeist", 711, 200)
                .copy(sourceId = "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS")
        )))
        assertEquals("Camerupt", model.sceneAt(300).player?.speciesName)
        assertEquals("Gourgeist", model.sceneAt(300).opponent?.speciesName)
    }

    @Test fun `late badge confirmation backdates immediate opening player switch to first hit`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("team", ArchivedPlayerTeamSlotConfigured(1, "Turtonator", 776, "Incinerate", emptyList()), 1L),
            timed("go", ArchivedMatchStarted, 1_000_000_000L),
            article("lead", "PLAYER", "Turtonator", 776, 1_000_000_000L)
                .copy(sourceId = "TEAM_SELECT_LEAD_INFERENCE"),
            timed(
                "first-hit",
                ArchivedActiveHpBarDamageTickMeasured("OPPONENT", 1f, 0.9f, 0.1f),
                3_000_000_000L,
            ),
            article("late-camerupt", "PLAYER", "Camerupt", 323, 9_000_000_000L)
                .copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE", confidence = 0.98f),
        )))

        assertEquals("Turtonator", model.sceneAt(2_000_000_000L).player?.speciesName)
        assertEquals("Camerupt", model.sceneAt(3_000_000_000L).player?.speciesName)
        assertEquals("OBSERVED SPECIES / FIRST PLAYER HIT", model.sceneAt(3_000_000_000L).player?.identityBasis)
    }

    @Test fun `completed replay uses the player roster to reconstruct the actual announcement sequence`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("team-1", ArchivedPlayerTeamSlotConfigured(1, "Pikachu", 25, "", emptyList()), 1),
            timed("team-2", ArchivedPlayerTeamSlotConfigured(2, "Camerupt", 323, "", emptyList()), 2),
            timed("team-3", ArchivedPlayerTeamSlotConfigured(3, "Gourgeist", 711, "", emptyList()), 3),
            raw("pikachu", "Go, Pikachu!", 10),
            raw("sneasel", "Go, Sneasel!", 20),
            raw("sealeo", "Go, Sealeo!", 30),
            raw("vaporeon", "Go, Vaporeon!", 40)
        ))
        val model = MatchReplayModel(
            archive,
            mapOf("Pikachu" to 25, "Camerupt" to 323, "Gourgeist" to 711,
                "Sneasel" to 215, "Sealeo" to 364, "Vaporeon" to 134)
        )

        // The completed archive is buffered: later identity evidence can fill
        // the opening interval while retaining its reconstruction basis.
        assertEquals("Pikachu", model.sceneAt(10).player?.speciesName)
        assertEquals("Sneasel", model.sceneAt(10).opponent?.speciesName)
        assertEquals("Sealeo", model.sceneAt(30).opponent?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(40).opponent?.speciesName)
        assertEquals("Pikachu", model.sceneAt(40).player?.speciesName)
        assertEquals("RECONSTRUCTED FROM ARCHIVED ANNOUNCEMENT", model.sceneAt(10).player?.identityBasis)
    }

    @Test fun `late first announcement does not become the player in replay`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("vs", ArchivedVsScreenWitnessed, 1_000_000_000L),
            raw("late-vaporeon", "Go, Vaporeon!", 40_000_000_000L)
        ))

        val scene = MatchReplayModel(archive, mapOf("Vaporeon" to 134))
            .sceneAt(40_000_000_000L)

        assertEquals(null, scene.player)
        assertEquals(null, scene.opponent)
    }

    @Test fun `replay exposes archived announcement species for reference lookup`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            raw("entry", "Go, Sneasel!", 10),
            raw("move", "Raichu used Trailblaze!", 20)
        ))

        assertEquals(setOf("Sneasel", "Raichu"), MatchReplayModel.referencedSpeciesNames(archive))
    }

    @Test fun `current team supplies the opening player when no player species witness survived`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "team",
                ArchivedPlayerTeamSlotConfigured(1, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb")),
                10
            )
        ))

        val model = MatchReplayModel(archive)

        assertEquals("Gourgeist", model.sceneAt(10).player?.speciesName)
        assertEquals("CURRENT TEAM CONFIGURATION", model.sceneAt(10).player?.identityBasis)
        assertEquals(setOf("Gourgeist"), MatchReplayModel.referencedSpeciesNames(archive))
    }

    @Test fun `replay reconstructs both sides and switches from archived species crops`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            ArchivedRealityArticle(
                articleId = "start", matchId = "match", perceivedAt = 0, recordedAt = 0,
                sourceId = "DROIDBALL_SESSION", payload = com.example.overdex.battle.archive.ArchivedMatchRecordStarted,
                predecessorIds = emptyList(), confidence = null, sequenceNumber = 0,
                evidenceReferences = emptyList(), monotonicTimeNanos = 1
            )
        ))
        val identities = listOf(
            ReplayIdentityObservation("PLAYER", "Turtonator", 776, 10, "ARCHIVED CROP"),
            ReplayIdentityObservation("PLAYER", "Camerupt", 323, 30, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Sneasel", 215, 10, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Sealeo", 364, 20, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Vaporeon", 134, 30, "ARCHIVED CROP")
        )
        val model = MatchReplayModel(archive, archivedCropIdentities = identities)

        assertEquals("Turtonator", model.sceneAt(10).player?.speciesName)
        assertEquals("Sneasel", model.sceneAt(10).opponent?.speciesName)
        assertEquals("Sealeo", model.sceneAt(20).opponent?.speciesName)
        assertEquals("Camerupt", model.sceneAt(30).player?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(30).opponent?.speciesName)
    }

    @Test fun `one live identity does not discard switches recovered from archived crops`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            article("accepted", "OPPONENT", "Sneasel", 215, 20)
        ))
        val identities = listOf(
            ReplayIdentityObservation("OPPONENT", "Sneasel", 215, 10, "RECONSTRUCTED FROM ARCHIVED SPECIES CROP"),
            ReplayIdentityObservation("OPPONENT", "Sealeo", 364, 30, "RECONSTRUCTED FROM ARCHIVED SPECIES CROP"),
            ReplayIdentityObservation("OPPONENT", "Vaporeon", 134, 50, "RECONSTRUCTED FROM ARCHIVED SPECIES CROP")
        )

        val model = MatchReplayModel(archive, archivedCropIdentities = identities)

        assertEquals("Sneasel", model.sceneAt(10).opponent?.speciesName)
        assertEquals("Sneasel", model.sceneAt(20).opponent?.speciesName)
        assertEquals("Sealeo", model.sceneAt(30).opponent?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(50).opponent?.speciesName)
    }

    @Test fun `replay grays the fainted combatant until the next entry`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            article("sneasel", "OPPONENT", "Sneasel", 215, 10),
            timed(
                "faint",
                ArchivedActivePokemonFaintedWitnessed(
                    side = "OPPONENT",
                    speciesName = "Sneasel",
                    speciesId = 215,
                    basis = "OPPONENT_POKE_BALL_COUNT_DECREASE"
                ),
                20
            ),
            article("repeated-sneasel-badge", "OPPONENT", "Sneasel", 215, 25),
            article("sealeo", "OPPONENT", "Sealeo", 364, 30)
        ))
        val model = MatchReplayModel(archive)

        assertEquals(true, model.sceneAt(20).opponent?.isFainted)
        assertEquals(true, model.sceneAt(25).opponent?.isFainted)
        assertEquals("Sealeo", model.sceneAt(30).opponent?.speciesName)
        assertEquals(false, model.sceneAt(30).opponent?.isFainted)
    }

    @Test fun `replay projects confirmed fast move type onto an observed hit`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("identified", ArchivedFastMoveIdentified("OPPONENT", "Sneasel", "Ice Shard", 1_000_000_000L, 1_000_000_000L, 3, "TEST"), 100),
            timed("hit", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 200),
            timed("energy", ArchivedFastMoveEnergyDerived("OPPONENT", "Ice Shard", 1, 10, 10, "TEST"), 200)
        ))
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("ICESHARD" to PokemonType.ICE))

        val scene = model.sceneAt(200)
        assertEquals(10, scene.opponentGeneratedEnergy)
        assertEquals(PokemonType.ICE, scene.fastMoveActions.single().type)
        assertEquals("OPPONENT", scene.fastMoveActions.single().side)
    }

    @Test fun `replay subtracts only confirmed named charged move costs`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("fast", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "TEST"), 100),
            timed("charged", ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Dragon Pulse", 60, "TEST"), 200)
        ))

        val scene = MatchReplayModel(archive).sceneAt(200)

        assertEquals(20, scene.playerGeneratedEnergy)
        assertEquals(60, scene.playerSpentEnergy)
    }

    @Test fun `replay fuses get ready vibration and named move into one stronger charge action`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("ready", ArchivedGetReadyWitnessed, 1_000_000_000L),
            timed(
                "haptic",
                ArchivedChargeMoveQteVibrationPatternInferred(
                    "PLAYER", 3, 2_000_000_000L, "THREE_SHORT_DEVICE_MOTION_PULSES"
                ),
                3_000_000_000L
            ),
            timed(
                "named",
                ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Dragon Pulse", 60, "TEST"),
                5_000_000_000L
            )
        ))

        val action = MatchReplayModel(archive).sceneAt(5_900_000_000L).chargedMoveActions.single()

        assertEquals("PLAYER", action.side)
        assertEquals("Dragon Pulse", action.moveName)
        assertEquals(5_000_000_000L, action.startedAtNanos)
        assertEquals(setOf("GET_READY", "QTE_VIBRATION_PATTERN", "NAMED_CHARGED_MOVE"), action.evidenceKinds)
        assertEquals(0.5f, action.progress, 0.001f)
    }

    @Test fun `vibration pattern alone animates an unnamed player charged move`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "haptic",
                ArchivedChargeMoveQteVibrationPatternInferred(
                    "PLAYER", 3, 2_000_000_000L, "THREE_SHORT_DEVICE_MOTION_PULSES"
                ),
                1_000_000_000L
            )
        ))

        val action = MatchReplayModel(archive).sceneAt(1_500_000_000L).chargedMoveActions.single()

        assertEquals("PLAYER", action.side)
        assertEquals(null, action.moveName)
        assertEquals(setOf("QTE_VIBRATION_PATTERN"), action.evidenceKinds)
    }

    @Test fun `forward replay crosses preserved vibration pattern exactly once`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("ready", ArchivedGetReadyWitnessed, 500_000_000L),
            timed(
                "haptic",
                ArchivedChargeMoveQteVibrationPatternInferred(
                    "PLAYER", 3, 2_000_000_000L, "THREE_SHORT_DEVICE_MOTION_PULSES"
                ),
                1_000_000_000L
            )
        ))
        val model = MatchReplayModel(archive)

        assertEquals(1, model.hapticEventsBetween(999_000_000L, 1_000_000_000L).size)
        assertEquals(3, model.hapticEventsBetween(999_000_000L, 1_000_000_000L).single().pulseCount)
        assertTrue(model.hapticEventsBetween(1_000_000_000L, 1_100_000_000L).isEmpty())
        assertTrue(model.hapticEventsBetween(1_100_000_000L, 900_000_000L).isEmpty())
    }

    @Test fun `linked physical QTE pulses replay exact nice great and excellent milestones`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("ready", ArchivedGetReadyWitnessed, 500_000_000L),
            timed("nice", motionPulse(), 1_000_000_000L),
            timed("great", motionPulse(), 1_600_000_000L),
            timed("excellent", motionPulse(), 2_300_000_000L),
            timed(
                "pattern",
                ArchivedChargeMoveQteVibrationPatternInferred(
                    "PLAYER", 3, 1_300_000_000L, "THREE_SHORT_DEVICE_MOTION_PULSES"
                ),
                1_000_000_000L,
            ).copy(predecessorIds = listOf("ready", "nice", "great", "excellent")),
        ))
        val model = MatchReplayModel(archive)

        assertEquals("NICE", model.sceneAt(1_100_000_000L).qteMilestone)
        assertEquals("GREAT", model.sceneAt(1_700_000_000L).qteMilestone)
        assertEquals("EXCELLENT", model.sceneAt(2_400_000_000L).qteMilestone)
        assertEquals("PLAYER", model.sceneAt(2_400_000_000L).qteMilestoneSide)
        assertEquals(1, model.hapticEventsBetween(900_000_000L, 1_000_000_000L).single().pulseCount)
        assertEquals(1, model.hapticEventsBetween(1_500_000_000L, 1_600_000_000L).single().pulseCount)
        assertEquals(1, model.hapticEventsBetween(2_200_000_000L, 2_300_000_000L).single().pulseCount)
    }

    @Test fun `old archive reconstructs QTE milestones from short pulses before charge announcement`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("nice", motionPulse(116_000_000L), 1_000_000_000L),
            timed("great", motionPulse(209_000_000L), 2_194_000_000L),
            timed("excellent", motionPulse(243_000_000L), 4_731_000_000L),
            timed("charge-used", ArchivedChargeMoveUsedAnnounced, 5_000_000_000L),
        ))
        val model = MatchReplayModel(archive)

        assertEquals("NICE", model.sceneAt(1_100_000_000L).qteMilestone)
        assertEquals("GREAT", model.sceneAt(2_300_000_000L).qteMilestone)
        assertEquals("EXCELLENT", model.sceneAt(4_800_000_000L).qteMilestone)
        assertEquals("PLAYER", model.sceneAt(4_800_000_000L).qteMilestoneSide)
        assertEquals(3, model.hapticEventsBetween(0L, 5_000_000_000L).size)
    }

    @Test fun `clipped get ready cue permits replay of a partial Great result`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            raw("ready", "Get Rea", 500_000_000L),
            timed("nice", motionPulse(), 1_000_000_000L),
            timed("great", motionPulse(), 2_000_000_000L),
            timed("charge-used", ArchivedChargeMoveUsedAnnounced, 3_000_000_000L),
        ))
        val model = MatchReplayModel(archive)

        assertEquals("NICE", model.sceneAt(1_100_000_000L).qteMilestone)
        assertEquals("GREAT", model.sceneAt(2_100_000_000L).qteMilestone)
        assertEquals(null, model.sceneAt(2_800_000_000L).qteMilestone)
    }

    @Test fun `two unprompted motion pulses do not invent a replay QTE`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("one", motionPulse(), 1_000_000_000L),
            timed("two", motionPulse(), 2_000_000_000L),
            timed("charge-used", ArchivedChargeMoveUsedAnnounced, 3_000_000_000L),
        ))

        assertEquals(null, MatchReplayModel(archive).sceneAt(2_100_000_000L).qteMilestone)
    }

    @Test fun `replay exposes the archived winner only after match end`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("started", ArchivedMatchStarted, 1_000_000_000L),
            timed("ended", ArchivedMatchEnded("WIN"), 5_000_000_000L),
        ))
        val model = MatchReplayModel(archive)

        assertEquals(null, model.sceneAt(4_999_999_999L).outcome)
        assertEquals("WIN", model.sceneAt(5_000_000_000L).outcome)
    }

    @Test fun `identification alone and aggregate totals never invent individual attacks`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("identity", ArchivedFastMoveIdentified("PLAYER", "Turtonator", "Incinerate", 2_500_000_000L, 2_500_000_000L, 3, "TEST"), 100),
            timed("batch", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 3, 20, 60, "TEST"), 200)
        ))
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("INCINERATE" to PokemonType.FIRE))
        assertTrue(model.sceneAt(100).fastMoveActions.isEmpty())
        assertTrue(model.sceneAt(200).fastMoveActions.isEmpty())
    }

    @Test fun `both sides animate at original evidence times and scrubbing is repeatable`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("player-hit", ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 0.4f, 8, 0.3f, 0.8f), 1_000_000_000L),
            timed("opponent-hit", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 1_100_000_000L),
            timed("player", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "TEST"), 1_000_000_000L),
            timed("opponent", ArchivedFastMoveEnergyDerived("OPPONENT", "Ice Shard", 1, 10, 10, "TEST"), 1_100_000_000L)
        ))
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("INCINERATE" to PokemonType.FIRE, "ICESHARD" to PokemonType.ICE))
        assertTrue(model.sceneAt(999_999_999L).fastMoveActions.isEmpty())
        val overlap = model.sceneAt(1_200_000_000L).fastMoveActions
        assertEquals(listOf("PLAYER", "OPPONENT"), overlap.map { it.side })
        assertTrue(overlap[0].progress > overlap[1].progress)
        assertTrue(model.sceneAt(1_600_000_000L).fastMoveActions.isEmpty())
        assertEquals(overlap, model.sceneAt(1_200_000_000L).fastMoveActions)
    }

    @Test fun `energy derived without a witnessed impact does not prove an attack happened`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("energy", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "TEST"), 100)
        ))
        assertTrue(MatchReplayModel(archive).sceneAt(100).fastMoveActions.isEmpty())
    }

    @Test fun `replay excludes pre-start charged and post-match hits from fast moves`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("prestart", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 500_000_000L),
            timed("start", ArchivedMatchStarted, 1_000_000_000L),
            timed("ready", ArchivedGetReadyWitnessed, 2_000_000_000L),
            timed("qte", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 3_000_000_000L),
            timed("charged", ArchivedChargeMoveUsedAnnounced, 5_000_000_000L),
            timed("charged-hit", ArchivedActiveHpBarDamageTickMeasured("PLAYER", 0.8f, 0.3f, 0.5f), 5_100_000_000L),
            timed("fast", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 6_000_000_000L),
            timed("end", ArchivedMatchEnded("WIN"), 7_000_000_000L),
            timed("postend", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f), 8_000_000_000L),
        ))
        val model = MatchReplayModel(archive)
        for (at in listOf(600_000_000L, 3_100_000_000L, 5_200_000_000L, 8_100_000_000L)) {
            assertTrue("False attack at $at", model.sceneAt(at).fastMoveActions.isEmpty())
        }
        assertEquals("OPPONENT", model.sceneAt(6_100_000_000L).fastMoveActions.single().side)
    }

    @Test fun `player hp border pulse animates an opponent fast move without requiring identification`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            )
        ))

        val action = MatchReplayModel(archive).sceneAt(1_100_000_000L).fastMoveActions.single()

        assertEquals("OPPONENT", action.side)
        assertEquals(null, action.moveName)
        assertEquals(null, action.type)
        assertEquals(setOf("HP_BORDER_PULSE"), action.evidenceKinds)
    }

    @Test fun `replay renders the certain type when all possible fast moves share it`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            article("species", "OPPONENT", "Glaceon", 471, 500_000_000L),
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            )
        ))
        val iceMoves = listOf(
            Move("Frost Breath", PokemonType.ICE, 7, 5, true, 2),
            Move("Ice Shard", PokemonType.ICE, 9, 10, true, 3)
        )

        val action = MatchReplayModel(
            archive,
            fastMovesBySpeciesName = mapOf("Glaceon" to iceMoves)
        ).sceneAt(1_100_000_000L).fastMoveActions.single()

        assertEquals(null, action.moveName)
        assertEquals(PokemonType.ICE, action.type)
    }

    @Test fun `completed replay resolves a noisy hp motion cluster and backfills its type icon`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            article("species", "PLAYER", "Samurott", 503, 100_000_000L),
            timed("hit", ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 0.4f, 8, 0.3f, 0.8f), 1_000_000_000L),
            timed("f1", ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 463_000_000L, 20f, 5), 1_000_000_000L),
            timed("noise", ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 1_962_000_000L, 20f, 5), 3_000_000_000L),
            timed("f2", ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 367_000_000L, 20f, 5), 3_500_000_000L),
            timed("f3", ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 459_000_000L, 20f, 5), 4_000_000_000L)
        ))
        val fastMoves = listOf(
            Move("Fury Cutter", PokemonType.BUG, 3, 4, true, 1),
            Move("Waterfall", PokemonType.WATER, 11, 10, true, 3)
        )

        val action = MatchReplayModel(
            archive,
            fastMovesBySpeciesName = mapOf("Samurott" to fastMoves)
        ).sceneAt(1_100_000_000L).fastMoveActions.single()

        assertEquals("Fury Cutter", action.moveName)
        assertEquals(PokemonType.BUG, action.type)
    }

    @Test fun `independent hit witnesses for one captured moment produce one replay attack`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            ),
            timed(
                "artifact",
                ArchivedFastMoveRecipientVisualArtifactMeasured(
                    "OPPONENT", 0.2f, 0.3f, 1f, 0.5f, 0.2f, 0.5f, 0.5f, 20
                ),
                1_100_000_000L
            )
        ))

        val actions = MatchReplayModel(archive).sceneAt(1_200_000_000L).fastMoveActions

        assertEquals(1, actions.size)
        assertEquals("PLAYER", actions.single().side)
        assertEquals(setOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT"), actions.single().evidenceKinds)
    }

    @Test fun `canonical fused use prevents replay from re-counting its raw witnesses`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            ),
            timed(
                "artifact",
                ArchivedFastMoveRecipientVisualArtifactMeasured(
                    "PLAYER", 0.2f, 0.3f, 1f, 0.5f, 0.2f, 0.5f, 0.5f, 20
                ),
                1_100_000_000L
            ),
            timed(
                "fused",
                ArchivedFastMoveUseObserved(
                    useId = "fast-move-use:pulse",
                    attackingSide = "OPPONENT",
                    damagedSide = "PLAYER",
                    appearanceId = "sealeo",
                    attackerSpeciesName = "Sealeo",
                    evidenceKinds = listOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT"),
                    basis = "FUSED_SIDE_ATTRIBUTED_FAST_MOVE_EVIDENCE"
                ),
                1_000_000_000L
            )
        ))

        val actions = MatchReplayModel(archive).sceneAt(1_200_000_000L).fastMoveActions

        assertEquals(1, actions.size)
        assertEquals("OPPONENT", actions.single().side)
        assertEquals(setOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT"), actions.single().evidenceKinds)
    }

    @Test fun `canonical use does not hide a later uncovered damage tick`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            ),
            timed(
                "fused",
                ArchivedFastMoveUseObserved(
                    useId = "fast-move-use:pulse",
                    attackingSide = "OPPONENT",
                    damagedSide = "PLAYER",
                    appearanceId = "sneasel",
                    attackerSpeciesName = "Sneasel",
                    evidenceKinds = listOf("HP_BORDER_PULSE"),
                    basis = "FUSED_SIDE_ATTRIBUTED_FAST_MOVE_EVIDENCE"
                ),
                1_000_000_000L
            ),
            timed(
                "later-damage",
                ArchivedActiveHpBarDamageTickMeasured("PLAYER", 0.8f, 0.72f, 0.08f),
                1_500_000_000L
            )
        ))
        val model = MatchReplayModel(archive)

        assertEquals(1, model.sceneAt(1_100_000_000L).fastMoveActions.size)
        assertEquals(1, model.sceneAt(1_600_000_000L).fastMoveActions.size)
        assertEquals(
            setOf("HP_DAMAGE_TICK"),
            model.sceneAt(1_600_000_000L).fastMoveActions.single().evidenceKinds
        )
    }

    @Test fun `later move identity backfills type onto earlier raw fast move observation`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            article("species", "OPPONENT", "SNEASEL", 215, 500_000_000L),
            timed(
                "pulse",
                ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 8, 0.3f, 0.8f),
                1_000_000_000L
            ),
            timed(
                "identity",
                ArchivedFastMoveIdentified(
                    "OPPONENT", "Sneasel", "Ice Shard", 1_500_000_000L, 1_500_000_000L, 3, "TEST"
                ),
                5_000_000_000L
            )
        ))
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("ICESHARD" to PokemonType.ICE))

        val action = model.sceneAt(1_100_000_000L).fastMoveActions.single()

        assertEquals("Ice Shard", action.moveName)
        assertEquals(PokemonType.ICE, action.type)
    }

    @Test fun `idle hp motion and charge shading do not create attacks`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("opponent-motion", ArchivedActiveHpBarMotionCadenceMeasured("OPPONENT", 500_000_000L, 25f, 5), 1_000_000_000L),
            timed(
                "player-fill",
                ArchivedPlayerChargeMoveEnergyFillIncreased(listOf(0.1f, 0.2f), listOf(0.2f, 0.3f), listOf(0, 1)),
                1_600_000_000L
            )
        ))
        val model = MatchReplayModel(archive)

        assertTrue(model.sceneAt(1_100_000_000L).fastMoveActions.isEmpty())
        assertTrue(model.sceneAt(1_700_000_000L).fastMoveActions.isEmpty())
    }

    @Test fun `configured player fast move overrides a false archived charge fill conclusion`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("team", ArchivedPlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711, "Incinerate", listOf("Seed Bomb")), 10),
            article("species", "PLAYER", "Gourgeist", 711, 20),
            timed(
                "false-identity",
                ArchivedFastMoveIdentified(
                    "PLAYER", "Gourgeist", "Razor Leaf", 1_000_000_000L, 900_000_000L, 3,
                    "CHARGE_MOVE_ENERGY_FILL_CADENCE_AND_REFERENCE_KNOWLEDGE"
                ),
                100
            ),
            timed("hit", ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 0.4f, 8, 0.3f, 0.8f), 1_000_000_000L)
        ))
        val moves = listOf(
            Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5),
            Move("Razor Leaf", PokemonType.GRASS, 10, 4, true, 2)
        )

        val action = MatchReplayModel(
            archive,
            fastMoveTypesByName = mapOf("INCINERATE" to PokemonType.FIRE, "RAZORLEAF" to PokemonType.GRASS),
            fastMovesBySpeciesName = mapOf("Gourgeist" to moves)
        ).sceneAt(1_100_000_000L).fastMoveActions.single()

        assertEquals("Incinerate", action.moveName)
        assertEquals(PokemonType.FIRE, action.type)
    }

    @Test fun `hp follows its own combatant and restores when scrubbing backwards`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("player", "PLAYER", "Turtonator", 776, 10),
            article("opponent", "OPPONENT", "Sneasel", 215, 10),
            hp("player-full", "PLAYER", 1f, 20),
            hp("opponent-low", "OPPONENT", 0.19f, 20),
            hp("player-hit", "PLAYER", 0.48f, 30),
        )))

        assertEquals(0.48f, model.sceneAt(30).playerHp?.filledFraction)
        assertEquals(0.19f, model.sceneAt(30).opponentHp?.filledFraction)
        assertEquals(1f, model.sceneAt(20).playerHp?.filledFraction)
        assertEquals(null, model.sceneAt(10).playerHp?.filledFraction)
    }

    @Test fun `hp persists across repeated names but never leaks across switches`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("sneasel", "OPPONENT", "Sneasel", 215, 10),
            hp("low", "OPPONENT", 0.1f, 20),
            article("name-repeat", "OPPONENT", "Sneasel", 215, 25),
            article("sealeo", "OPPONENT", "Sealeo", 364, 30),
            hp("replacement-hp", "OPPONENT", 0.95f, 40),
            article("sneasel-return", "OPPONENT", "Sneasel", 215, 50),
        )))

        assertEquals(0.1f, model.sceneAt(25).opponentHp?.filledFraction)
        assertEquals(null, model.sceneAt(30).opponentHp?.filledFraction)
        assertEquals(0.95f, model.sceneAt(40).opponentHp?.filledFraction)
        assertEquals(null, model.sceneAt(50).opponentHp?.filledFraction)
        assertEquals(0.1f, model.sceneAt(20).opponentHp?.filledFraction)
    }

    @Test fun `late opening identity uses earlier hp without showing hp in menus`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("recording", com.example.overdex.battle.archive.ArchivedMatchRecordStarted, 1),
            timed("vs", ArchivedVsScreenWitnessed, 10),
            hp("measured-before-name", "OPPONENT", 0.8f, 20),
            article("late-name", "OPPONENT", "Sneasel", 215, 30),
        )))

        assertEquals(null, model.sceneAt(1).opponentHp)
        assertEquals("Sneasel", model.sceneAt(20).opponent?.speciesName)
        assertEquals(0.8f, model.sceneAt(20).opponentHp?.filledFraction)
    }

    @Test fun `damage trail and border pulse replay separately at their recorded times`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("player", "PLAYER", "Turtonator", 776, 0),
            article("opponent", "OPPONENT", "Sneasel", 215, 0),
            hp("full", "PLAYER", 1f, 0),
            timed("pulse", ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.4f, 2), 1_000_000_000L),
            timed("damage", ArchivedActiveHpBarDamageTickMeasured("PLAYER", 1f, 0.75f, 0.25f), 1_050_000_000L),
        )))

        val pulseOnly = model.sceneAt(1_025_000_000L).playerHp!!
        assertEquals(1f, pulseOnly.filledFraction)
        assertEquals(null, pulseOnly.damageTrailFraction)
        assertTrue(pulseOnly.borderPulse)
        val hit = model.sceneAt(1_100_000_000L)
        assertEquals(0.75f, hit.playerHp?.filledFraction)
        assertEquals(1f, hit.playerHp?.damageTrailFraction)
        assertEquals(true, hit.playerHp?.borderPulse)
        assertEquals(false, hit.opponentHp?.borderPulse)
        assertEquals(false, model.sceneAt(1_200_000_000L).playerHp?.borderPulse)
        assertEquals(1f, model.sceneAt(1_200_000_000L).playerHp?.damageTrailFraction)
        assertEquals(null, model.sceneAt(1_400_000_000L).playerHp?.damageTrailFraction)
        assertEquals(hit.playerHp, model.sceneAt(1_100_000_000L).playerHp)
    }

    @Test fun `new appearance cannot inherit an old border flash or damage trail`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("first", "OPPONENT", "Sneasel", 215, 0),
            timed("pulse", ArchivedHpBarBorderPulse("OPPONENT", "PRESENT"), 1_000_000_000L),
            timed("hit", ArchivedActiveHpBarDamageTickMeasured("OPPONENT", 0.1f, 0f, 0.1f), 1_000_000_000L),
            article("next", "OPPONENT", "Sealeo", 364, 1_050_000_000L),
        )))

        assertEquals(true, model.sceneAt(1_025_000_000L).opponentHp?.borderPulse)
        assertEquals(ReplayHpState(null), model.sceneAt(1_100_000_000L).opponentHp)
    }

    @Test fun `invalid hp and unavailable pulse records cannot fabricate a hit`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("species", "PLAYER", "Turtonator", 776, 0),
            hp("valid", "PLAYER", 0.6f, 10),
            hp("nan", "PLAYER", Float.NaN, 20),
            hp("infinite", "PLAYER", Float.POSITIVE_INFINITY, 30),
            hp("negative", "PLAYER", -0.1f, 40),
            hp("too-big", "PLAYER", 1.5f, 50),
            timed("bad-damage", ArchivedActiveHpBarDamageTickMeasured("PLAYER", 0.1f, 0.9f, -0.8f), 60),
            timed("unavailable", ArchivedHpBarBorderPulse("PLAYER", "UNAVAILABLE"), 70),
        )))

        assertEquals(ReplayHpState(0.6f), model.sceneAt(70).playerHp)
    }

    @Test fun `confirmed faint empties hp without requiring a final empty measurement`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("sneasel", "OPPONENT", "Sneasel", 215, 10),
            hp("last-visible", "OPPONENT", 0.06f, 20),
            timed("faint", ArchivedActivePokemonFaintedWitnessed(
                "OPPONENT", "Sneasel", 215, "OPPONENT_POKE_BALL_COUNT_DECREASE"), 30),
            hp("stale", "OPPONENT", 0.06f, 35),
            article("sealeo", "OPPONENT", "Sealeo", 364, 40),
        )))

        assertEquals(0f, model.sceneAt(35).opponentHp?.filledFraction)
        assertEquals(null, model.sceneAt(40).opponentHp?.filledFraction)
        assertEquals(0.06f, model.sceneAt(20).opponentHp?.filledFraction)
    }

    private fun hp(id: String, side: String, fraction: Float, nanos: Long) =
        timed(id, ArchivedActiveHpBarMeasured(side, 10, 20, 110, 30, fraction), nanos)

    private fun article(id: String, side: String, name: String, speciesId: Int, nanos: Long) =
        ArchivedRealityArticle(
            articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
            sourceId = "test", payload = ArchivedActivePokemonSpeciesWitnessed(side, name, speciesId),
            predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
            evidenceReferences = emptyList(), monotonicTimeNanos = nanos
        )

    private fun raw(id: String, text: String, nanos: Long) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
        sourceId = "ANNOUNCEMENT_WITNESS", payload = ArchivedRawText(text),
        predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
        evidenceReferences = emptyList(), monotonicTimeNanos = nanos
    )

    private fun timed(id: String, payload: com.example.overdex.battle.archive.ArchivedTestimonyPayload, nanos: Long) =
        ArchivedRealityArticle(
            articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
            sourceId = "test", payload = payload,
            predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
            evidenceReferences = emptyList(), monotonicTimeNanos = nanos
        )

    private fun motionPulse(durationNanos: Long = 36_000_000L) = ArchivedDeviceMotionPulseMeasured(
        durationNanos = durationNanos,
        peakLinearAccelerationMetersPerSecondSquared = 1.2f,
        rmsLinearAccelerationMetersPerSecondSquared = 0.7f,
        sampleCount = 8,
        sensorType = "LINEAR_ACCELERATION",
    )
}
