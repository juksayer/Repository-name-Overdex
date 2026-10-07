package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
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
import com.example.overdex.battle.archive.ArchivedCountdownGlyphWitnessed
import com.example.overdex.battle.archive.ArchivedDeviceMotionPulseMeasured
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedMatchStarted
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

    @Test fun `replay projects confirmed fast move type and derived generation`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("identified", ArchivedFastMoveIdentified("OPPONENT", "Sneasel", "Ice Shard", 1_000_000_000L, 1_000_000_000L, 3, "TEST"), 100),
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

    @Test fun `hp motion and charge fill each create their side's attack`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("opponent-motion", ArchivedActiveHpBarMotionCadenceMeasured("OPPONENT", 500_000_000L, 25f, 5), 1_000_000_000L),
            timed(
                "player-fill",
                ArchivedPlayerChargeMoveEnergyFillIncreased(listOf(0.1f, 0.2f), listOf(0.2f, 0.3f), listOf(0, 1)),
                1_600_000_000L
            )
        ))
        val model = MatchReplayModel(archive)

        assertEquals("OPPONENT", model.sceneAt(1_100_000_000L).fastMoveActions.single().side)
        assertEquals("PLAYER", model.sceneAt(1_700_000_000L).fastMoveActions.single().side)
        assertEquals(
            setOf("PLAYER_CHARGE_FILL_INCREASE"),
            model.sceneAt(1_700_000_000L).fastMoveActions.single().evidenceKinds
        )
    }

    @Test fun `configured player fast move overrides a false archived charge fill conclusion`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("team", ArchivedPlayerTeamSlotConfigured(1, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb")), 10),
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

    private fun motionPulse() = ArchivedDeviceMotionPulseMeasured(
        durationNanos = 36_000_000L,
        peakLinearAccelerationMetersPerSecondSquared = 1.2f,
        rmsLinearAccelerationMetersPerSecondSquared = 0.7f,
        sampleCount = 8,
        sensorType = "LINEAR_ACCELERATION",
    )
}
