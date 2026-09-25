package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.SupportingMatchStart
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.PlayerInactiveHpBarMeasured
import com.example.overdex.battle.custody.PlayerInactiveSpeciesSpriteFingerprintMeasured
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import kotlin.reflect.KClass

/** Geographic territories in the published battle frame. */
enum class BattleRegionId {
    ANNOUNCEMENT,
    PLAYER_TEAM_INFO,
    OPPONENT_TEAM_INFO,
    PLAYER_ACTIVE_TYPE,
    OPPONENT_ACTIVE_TYPE,
    PLAYER_HP,
    OPPONENT_HP,
    CHARGE_MOVE_EXECUTION,
    PLAYER_CHARGE_MOVE_CONTROLS,
    PLAYER_INACTIVE_POKEMON,
    COUNTDOWN,
    VS_SCREEN,
    MATCH_OUTCOME,
    OUT_OF_BATTLE_MENU,
    BATTLE_PARTY_TABS,
    OPPONENT_SPECIES_NAME,
    OPPONENT_POKE_BALLS,
    OPPONENT_SHIELDS;

    fun regionIn(calibration: BattleCalibration): AnchorRegion = when (this) {
        ANNOUNCEMENT -> calibration.announcementRegion
        PLAYER_TEAM_INFO -> calibration.playerTeamInfoRegion
        OPPONENT_TEAM_INFO -> calibration.opponentTeamInfoRegion
        PLAYER_ACTIVE_TYPE -> calibration.trainerActiveTypeRegion
        OPPONENT_ACTIVE_TYPE -> calibration.opponentActiveTypeRegion
        PLAYER_HP -> calibration.trainerHpRegion
        OPPONENT_HP -> calibration.opponentHpRegion
        CHARGE_MOVE_EXECUTION -> calibration.chargeMoveExecutionRegion
        PLAYER_CHARGE_MOVE_CONTROLS -> calibration.trainerChargeMoveControlsRegion
        PLAYER_INACTIVE_POKEMON -> calibration.trainerInactivePokemonRegion
        COUNTDOWN -> calibration.countdownRegion
        VS_SCREEN -> calibration.vsScreenRegion
        MATCH_OUTCOME -> calibration.matchOutcomeRegion
        OUT_OF_BATTLE_MENU -> calibration.outOfBattleMenuRegion
        BATTLE_PARTY_TABS -> calibration.battlePartyTabsRegion
        OPPONENT_SPECIES_NAME -> calibration.opponentSpeciesNameRegion
        OPPONENT_POKE_BALLS -> calibration.opponentPokeBallsRegion
        OPPONENT_SHIELDS -> calibration.opponentShieldsRegion
    }
}

/**
 * A crop has an observation purpose and a geographic source. It is not itself
 * a region and does not claim an interpreted battle event.
 */
data class BattleCropContract(
    val cropName: String,
    val region: BattleRegionId,
    val minimumSize: Int = 1,
    val areaInRegion: CropAreaInRegion = CropAreaInRegion()
) {
    fun resolve(calibration: BattleCalibration, source: Bitmap): ResolvedBattleCrop? =
        BattleCropResolver.resolve(cropName, region.regionIn(calibration), source, minimumSize, areaInRegion)

    fun resolveRect(calibration: BattleCalibration, sourceWidth: Int, sourceHeight: Int) =
        BattleCropResolver.resolveRect(region.regionIn(calibration), sourceWidth, sourceHeight, minimumSize, areaInRegion)
}

/** Established crop purposes. Additional phenomena belong here only after measurement is defined. */
object BattleCropContracts {
    val playerActiveTypeIcons = BattleCropContract(
        "PlayerActiveTypeIconsCrop", BattleRegionId.PLAYER_ACTIVE_TYPE, minimumSize = 24
    )
    val opponentActiveTypeIcons = BattleCropContract(
        "OpponentActiveTypeIconsCrop", BattleRegionId.OPPONENT_ACTIVE_TYPE, minimumSize = 24
    )
    val playerActiveSpeciesText = BattleCropContract(
        "PlayerActiveSpeciesTextCrop",
        BattleRegionId.PLAYER_TEAM_INFO,
        minimumSize = 16,
        // Measured name strip: source X 42–295, Y 250–275 within the player badge X 20–445, Y 225–350.
        areaInRegion = CropAreaInRegion(x = 22f / 425f, y = 25f / 125f, width = 253f / 425f, height = 25f / 125f)
    )
    val opponentActiveSpeciesText = BattleCropContract(
        "OpponentActiveSpeciesTextCrop", BattleRegionId.OPPONENT_SPECIES_NAME, minimumSize = 16
    )
    val opponentPokeBalls = BattleCropContract(
        "OpponentPokeBallsCrop", BattleRegionId.OPPONENT_POKE_BALLS, minimumSize = 16
    )
    val opponentShields = BattleCropContract(
        "OpponentShieldsCrop", BattleRegionId.OPPONENT_SHIELDS, minimumSize = 16
    )
    val countdownGlyph = BattleCropContract("CountdownGlyphCrop", BattleRegionId.COUNTDOWN, minimumSize = 32)
    val vsScreen = BattleCropContract("VsScreenCrop", BattleRegionId.VS_SCREEN, minimumSize = 32)
    val trainerInactiveTimerOverlayClearance = BattleCropContract(
        "TrainerInactiveTimerOverlayClearanceCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON
    )
    // The inactive-card territory is bisected horizontally. Upper and lower
    // witnesses receive separate artifacts and never inspect the other card.
    val playerInactiveUpperSpeciesSprite = BattleCropContract(
        "PlayerInactiveUpperSpeciesSpriteCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON,
        areaInRegion = CropAreaInRegion(y = 0f, height = 0.5f)
    )
    val playerInactiveLowerSpeciesSprite = BattleCropContract(
        "PlayerInactiveLowerSpeciesSpriteCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON,
        areaInRegion = CropAreaInRegion(y = 0.5f, height = 0.5f)
    )
    val playerInactiveUpperHpBar = BattleCropContract(
        "PlayerInactiveUpperHpBarCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON,
        areaInRegion = CropAreaInRegion(y = 0f, height = 0.5f)
    )
    val playerInactiveLowerHpBar = BattleCropContract(
        "PlayerInactiveLowerHpBarCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON,
        areaInRegion = CropAreaInRegion(y = 0.5f, height = 0.5f)
    )
    val inactiveMatchStartTimer = BattleCropContract(
        "InactiveMatchStartTimerCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON
    )
    val switchLockoutTimer = BattleCropContract(
        "SwitchLockoutTimerCrop", BattleRegionId.PLAYER_INACTIVE_POKEMON
    )
    val opponentHpEvidence = BattleCropContract("OpponentHpEvidenceCrop", BattleRegionId.OPPONENT_HP)
    val playerHpEvidence = BattleCropContract("PlayerHpEvidenceCrop", BattleRegionId.PLAYER_HP)
    val playerTeamStatus = BattleCropContract("PlayerTeamStatusCrop", BattleRegionId.PLAYER_TEAM_INFO)
    val opponentTeamStatus = BattleCropContract("OpponentTeamStatusCrop", BattleRegionId.OPPONENT_TEAM_INFO)
    val chargeMoveExecution = BattleCropContract("ChargeMoveExecutionCrop", BattleRegionId.CHARGE_MOVE_EXECUTION)
    val playerChargeMoveControls = BattleCropContract("PlayerChargeMoveControlsCrop", BattleRegionId.PLAYER_CHARGE_MOVE_CONTROLS)
    val announcement = BattleCropContract("AnnouncementCrop", BattleRegionId.ANNOUNCEMENT, minimumSize = 32)
    val matchOutcome = BattleCropContract("MatchOutcomeCrop", BattleRegionId.MATCH_OUTCOME, minimumSize = 32)
    val outOfBattleMenu = BattleCropContract("OutOfBattleMenuCrop", BattleRegionId.OUT_OF_BATTLE_MENU, minimumSize = 32)
    val battlePartyTabs = BattleCropContract("BattlePartyTabsCrop", BattleRegionId.BATTLE_PARTY_TABS, minimumSize = 32)
}

/** A Witness produces exactly one kind of testimony. */
data class BattleWitnessContract<T : TestimonyPayload>(
    val witnessId: String,
    val crop: BattleCropContract,
    val testimonyType: KClass<T>
)

object BattleWitnessContracts {
    val playerActiveTypeIconsCapture = BattleWitnessContract(
        witnessId = "PLAYER_ACTIVE_TYPE_ICONS_CAPTURE",
        crop = BattleCropContracts.playerActiveTypeIcons,
        testimonyType = CropCaptured::class
    )
    val opponentActiveTypeIconsCapture = BattleWitnessContract(
        witnessId = "OPPONENT_ACTIVE_TYPE_ICONS_CAPTURE",
        crop = BattleCropContracts.opponentActiveTypeIcons,
        testimonyType = CropCaptured::class
    )
    val playerActiveTypeIcons = BattleWitnessContract(
        witnessId = "PLAYER_ACTIVE_TYPE_ICONS_WITNESS",
        crop = BattleCropContracts.playerActiveTypeIcons,
        testimonyType = ActivePokemonTypesWitnessed::class
    )
    val opponentActiveTypeIcons = BattleWitnessContract(
        witnessId = "OPPONENT_ACTIVE_TYPE_ICONS_WITNESS",
        crop = BattleCropContracts.opponentActiveTypeIcons,
        testimonyType = ActivePokemonTypesWitnessed::class
    )
    val playerActiveSpeciesTextCapture = BattleWitnessContract(
        witnessId = "PLAYER_ACTIVE_SPECIES_TEXT_CAPTURE",
        crop = BattleCropContracts.playerActiveSpeciesText,
        testimonyType = CropCaptured::class
    )
    val opponentActiveSpeciesTextCapture = BattleWitnessContract(
        witnessId = "OPPONENT_ACTIVE_SPECIES_TEXT_CAPTURE",
        crop = BattleCropContracts.opponentActiveSpeciesText,
        testimonyType = CropCaptured::class
    )
    val opponentPokeBallsCapture = BattleWitnessContract(
        witnessId = "OPPONENT_POKE_BALLS_CAPTURE",
        crop = BattleCropContracts.opponentPokeBalls,
        testimonyType = CropCaptured::class
    )
    val opponentShieldsCapture = BattleWitnessContract(
        witnessId = "OPPONENT_SHIELDS_CAPTURE",
        crop = BattleCropContracts.opponentShields,
        testimonyType = CropCaptured::class
    )
    val countdownCropCapture = BattleWitnessContract(
        witnessId = "COUNTDOWN_CROP_CAPTURE",
        crop = BattleCropContracts.countdownGlyph,
        testimonyType = CropCaptured::class
    )
    val countdownGlyph = BattleWitnessContract(
        witnessId = "COUNTDOWN_GLYPH_WITNESS",
        crop = BattleCropContracts.countdownGlyph,
        testimonyType = CountdownGlyphWitnessed::class
    )
    val vsScreenCapture = BattleWitnessContract(
        witnessId = "VS_SCREEN_CAPTURE",
        crop = BattleCropContracts.vsScreen,
        testimonyType = CropCaptured::class
    )
    val opponentHpEvidenceCapture = BattleWitnessContract(
        witnessId = "OPPONENT_HP_EVIDENCE_CAPTURE",
        crop = BattleCropContracts.opponentHpEvidence,
        testimonyType = CropCaptured::class
    )
    val playerHpEvidenceCapture = BattleWitnessContract("PLAYER_HP_EVIDENCE_CAPTURE", BattleCropContracts.playerHpEvidence, CropCaptured::class)
    val playerTeamStatusCapture = BattleWitnessContract("PLAYER_TEAM_STATUS_CAPTURE", BattleCropContracts.playerTeamStatus, CropCaptured::class)
    val opponentTeamStatusCapture = BattleWitnessContract("OPPONENT_TEAM_STATUS_CAPTURE", BattleCropContracts.opponentTeamStatus, CropCaptured::class)
    val chargeMoveExecutionCapture = BattleWitnessContract("CHARGE_MOVE_EXECUTION_CAPTURE", BattleCropContracts.chargeMoveExecution, CropCaptured::class)
    val playerChargeMoveControlsCapture = BattleWitnessContract("PLAYER_CHARGE_MOVE_CONTROLS_CAPTURE", BattleCropContracts.playerChargeMoveControls, CropCaptured::class)
    val matchOutcomeCropCapture = BattleWitnessContract(
        witnessId = "MATCH_OUTCOME_CROP_CAPTURE",
        crop = BattleCropContracts.matchOutcome,
        testimonyType = CropCaptured::class
    )
    val outOfBattleMenuCapture = BattleWitnessContract("OUT_OF_BATTLE_MENU_CAPTURE", BattleCropContracts.outOfBattleMenu, CropCaptured::class)
    val battlePartyTabsCapture = BattleWitnessContract("BATTLE_PARTY_TABS_CAPTURE", BattleCropContracts.battlePartyTabs, CropCaptured::class)
    val announcementCropCapture = BattleWitnessContract(
        witnessId = "ANNOUNCEMENT_CROP_CAPTURE",
        crop = BattleCropContracts.announcement,
        testimonyType = CropCaptured::class
    )
    val playerInactiveUpperSpeciesSpriteCapture = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_UPPER_SPECIES_SPRITE_CAPTURE",
        crop = BattleCropContracts.playerInactiveUpperSpeciesSprite,
        testimonyType = CropCaptured::class
    )
    val playerInactiveLowerSpeciesSpriteCapture = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_LOWER_SPECIES_SPRITE_CAPTURE",
        crop = BattleCropContracts.playerInactiveLowerSpeciesSprite,
        testimonyType = CropCaptured::class
    )
    val playerInactiveUpperHpBarCapture = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_UPPER_HP_BAR_CAPTURE",
        crop = BattleCropContracts.playerInactiveUpperHpBar,
        testimonyType = CropCaptured::class
    )
    val playerInactiveLowerHpBarCapture = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_LOWER_HP_BAR_CAPTURE",
        crop = BattleCropContracts.playerInactiveLowerHpBar,
        testimonyType = CropCaptured::class
    )
    val inactiveMatchStartTimerCapture = BattleWitnessContract(
        witnessId = "INACTIVE_MATCH_START_TIMER_CAPTURE",
        crop = BattleCropContracts.inactiveMatchStartTimer,
        testimonyType = CropCaptured::class
    )
    val switchLockoutTimerCapture = BattleWitnessContract(
        witnessId = "SWITCH_LOCKOUT_TIMER_CAPTURE",
        crop = BattleCropContracts.switchLockoutTimer,
        testimonyType = CropCaptured::class
    )
    val trainerInactiveTimerOverlayClearanceCapture = BattleWitnessContract(
        witnessId = "TRAINER_INACTIVE_TIMER_OVERLAY_CAPTURE",
        crop = BattleCropContracts.trainerInactiveTimerOverlayClearance,
        testimonyType = CropCaptured::class
    )
    val trainerInactiveTimerOverlayClearance = BattleWitnessContract(
        witnessId = "TRAINER_INACTIVE_TIMER_OVERLAY",
        crop = BattleCropContracts.trainerInactiveTimerOverlayClearance,
        testimonyType = SupportingMatchStart::class
    )
    val vsScreen = BattleWitnessContract(
        witnessId = "VS_SCREEN_WITNESS",
        crop = BattleCropContracts.countdownGlyph,
        testimonyType = VsScreenWitnessed::class
    )
    val announcementText = BattleWitnessContract(
        witnessId = "ANNOUNCEMENT_WITNESS",
        crop = BattleCropContracts.announcement,
        testimonyType = RawTestimony::class
    )
    val attackIncomingText = BattleWitnessContract(
        witnessId = "ATTACK_INCOMING_WITNESS",
        crop = BattleCropContracts.announcement,
        testimonyType = RawTestimony::class
    )
    val getReady = BattleWitnessContract(
        witnessId = "GET_READY_WITNESS",
        crop = BattleCropContracts.announcement,
        testimonyType = GetReadyWitnessed::class
    )
    val chargeMoveUsedAnnouncement = BattleWitnessContract(
        witnessId = "CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS",
        crop = BattleCropContracts.announcement,
        testimonyType = ChargeMoveUsedAnnounced::class
    )
    val playerActiveSpeciesText = BattleWitnessContract(
        witnessId = "PLAYER_ACTIVE_SPECIES_WITNESS",
        crop = BattleCropContracts.playerActiveSpeciesText,
        testimonyType = RawTestimony::class
    )
    val opponentActiveSpeciesText = BattleWitnessContract(
        witnessId = "OPPONENT_ACTIVE_SPECIES_WITNESS",
        crop = BattleCropContracts.opponentActiveSpeciesText,
        testimonyType = RawTestimony::class
    )
    val playerActiveHpBar = BattleWitnessContract(
        witnessId = "PLAYER_ACTIVE_HP_BAR_WITNESS",
        crop = BattleCropContracts.playerHpEvidence,
        testimonyType = ActiveHpBarMeasured::class
    )
    val opponentActiveHpBar = BattleWitnessContract(
        witnessId = "OPPONENT_ACTIVE_HP_BAR_WITNESS",
        crop = BattleCropContracts.opponentHpEvidence,
        testimonyType = ActiveHpBarMeasured::class
    )
    val playerInactiveUpperHpBar = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_UPPER_HP_BAR_WITNESS",
        crop = BattleCropContracts.playerInactiveUpperHpBar,
        testimonyType = PlayerInactiveHpBarMeasured::class
    )
    val playerInactiveUpperSpeciesSprite = BattleWitnessContract(
        witnessId = "PLAYER_INACTIVE_UPPER_SPECIES_SPRITE_WITNESS",
        crop = BattleCropContracts.playerInactiveUpperSpeciesSprite,
        testimonyType = PlayerInactiveSpeciesSpriteFingerprintMeasured::class
    )
    val youWinText = BattleWitnessContract(
        witnessId = "YOU_WIN_WITNESS",
        crop = BattleCropContracts.matchOutcome,
        testimonyType = RawTestimony::class
    )
    val goodEffortText = BattleWitnessContract(
        witnessId = "GOOD_EFFORT_WITNESS",
        crop = BattleCropContracts.matchOutcome,
        testimonyType = RawTestimony::class
    )
}
