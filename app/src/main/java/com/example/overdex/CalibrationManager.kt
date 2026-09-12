package com.example.overdex

import android.content.Context
import android.util.Log
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion

/**
 * Manages the persistence of user-defined calibration offsets for battle UI regions.
 */
class CalibrationManager(context: Context) {

    //overmon is historical artifact. a reminder of where we came from
    private val prefs =
        context.getSharedPreferences("overmon_calibration", Context.MODE_PRIVATE)

    private companion object {
        const val ENEMY_X = "enemy_x"
        const val ENEMY_Y = "enemy_y"
        const val ENEMY_W = "enemy_w"
        const val ENEMY_H = "enemy_h"

        const val HP_X = "hp_x"
        const val HP_Y = "hp_y"
        const val HP_W = "hp_w"
        const val HP_H = "hp_h"

        const val TEAM_X = "team_x"
        const val TEAM_Y = "team_y"
        const val TEAM_W = "team_w"
        const val TEAM_H = "team_h"

        const val MOVE_X = "move_x"
        const val MOVE_Y = "move_y"
        const val MOVE_W = "move_w"
        const val MOVE_H = "move_h"
    }

    fun save(calibration: BattleCalibration) {
        prefs.edit()
            .putFloat(ENEMY_X, calibration.enemyNameRegion.x)
            .putFloat(ENEMY_Y, calibration.enemyNameRegion.y)
            .putFloat(ENEMY_W, calibration.enemyNameRegion.width)
            .putFloat(ENEMY_H, calibration.enemyNameRegion.height)

            .putFloat(HP_X, calibration.hpBarRegion.x)
            .putFloat(HP_Y, calibration.hpBarRegion.y)
            .putFloat(HP_W, calibration.hpBarRegion.width)
            .putFloat(HP_H, calibration.hpBarRegion.height)

            .putFloat(TEAM_X, calibration.teamIconsRegion.x)
            .putFloat(TEAM_Y, calibration.teamIconsRegion.y)
            .putFloat(TEAM_W, calibration.teamIconsRegion.width)
            .putFloat(TEAM_H, calibration.teamIconsRegion.height)

            .putFloat(MOVE_X, calibration.moveBannerRegion.x)
            .putFloat(MOVE_Y, calibration.moveBannerRegion.y)
            .putFloat(MOVE_W, calibration.moveBannerRegion.width)
            .putFloat(MOVE_H, calibration.moveBannerRegion.height)

            .putFloat("countdown_x", calibration.countdownRegion.x)
            .putFloat("countdown_y", calibration.countdownRegion.y)
            .putFloat("countdown_w", calibration.countdownRegion.width)
            .putFloat("countdown_h", calibration.countdownRegion.height)

            .putFloat("you_win_x", calibration.youWinRegion.x)
            .putFloat("you_win_y", calibration.youWinRegion.y)
            .putFloat("you_win_w", calibration.youWinRegion.width)
            .putFloat("you_win_h", calibration.youWinRegion.height)

            .putFloat("good_effort_x", calibration.goodEffortRegion.x)
            .putFloat("good_effort_y", calibration.goodEffortRegion.y)
            .putFloat("good_effort_w", calibration.goodEffortRegion.width)
            .putFloat("good_effort_h", calibration.goodEffortRegion.height)

            .putFloat("opponent_shield_x", calibration.opponentShieldsRegion.x)
            .putFloat("opponent_shield_y", calibration.opponentShieldsRegion.y)
            .putFloat("opponent_shield_w", calibration.opponentShieldsRegion.width)
            .putFloat("opponent_shield_h", calibration.opponentShieldsRegion.height)

            .putFloat("player_team_info_x", calibration.playerTeamInfoRegion.x)
            .putFloat("player_team_info_y", calibration.playerTeamInfoRegion.y)
            .putFloat("player_team_info_w", calibration.playerTeamInfoRegion.width)
            .putFloat("player_team_info_h", calibration.playerTeamInfoRegion.height)

            .putFloat("announcement_x", calibration.announcementRegion.x)
            .putFloat("announcement_y", calibration.announcementRegion.y)
            .putFloat("announcement_w", calibration.announcementRegion.width)
            .putFloat("announcement_h", calibration.announcementRegion.height)

            .putFloat("opponent_team_info_x", calibration.opponentTeamInfoRegion.x)
            .putFloat("opponent_team_info_y", calibration.opponentTeamInfoRegion.y)
            .putFloat("opponent_team_info_w", calibration.opponentTeamInfoRegion.width)
            .putFloat("opponent_team_info_h", calibration.opponentTeamInfoRegion.height)

            .putFloat("trainer_active_type_x", calibration.trainerActiveTypeRegion.x)
            .putFloat("trainer_active_type_y", calibration.trainerActiveTypeRegion.y)
            .putFloat("trainer_active_type_w", calibration.trainerActiveTypeRegion.width)
            .putFloat("trainer_active_type_h", calibration.trainerActiveTypeRegion.height)

            .putFloat("opponent_active_type_x", calibration.opponentActiveTypeRegion.x)
            .putFloat("opponent_active_type_y", calibration.opponentActiveTypeRegion.y)
            .putFloat("opponent_active_type_w", calibration.opponentActiveTypeRegion.width)
            .putFloat("opponent_active_type_h", calibration.opponentActiveTypeRegion.height)

            .putFloat("trainer_hp_x", calibration.trainerHpRegion.x)
            .putFloat("trainer_hp_y", calibration.trainerHpRegion.y)
            .putFloat("trainer_hp_w", calibration.trainerHpRegion.width)
            .putFloat("trainer_hp_h", calibration.trainerHpRegion.height)

            .putFloat("opponent_hp_x", calibration.opponentHpRegion.x)
            .putFloat("opponent_hp_y", calibration.opponentHpRegion.y)
            .putFloat("opponent_hp_w", calibration.opponentHpRegion.width)
            .putFloat("opponent_hp_h", calibration.opponentHpRegion.height)

            .putFloat("charge_move_execution_x", calibration.chargeMoveExecutionRegion.x)
            .putFloat("charge_move_execution_y", calibration.chargeMoveExecutionRegion.y)
            .putFloat("charge_move_execution_w", calibration.chargeMoveExecutionRegion.width)
            .putFloat("charge_move_execution_h", calibration.chargeMoveExecutionRegion.height)

            .putFloat("trainer_charge_move_controls_x", calibration.trainerChargeMoveControlsRegion.x)
            .putFloat("trainer_charge_move_controls_y", calibration.trainerChargeMoveControlsRegion.y)
            .putFloat("trainer_charge_move_controls_w", calibration.trainerChargeMoveControlsRegion.width)
            .putFloat("trainer_charge_move_controls_h", calibration.trainerChargeMoveControlsRegion.height)

            .putFloat("trainer_inactive_pokemon_x", calibration.trainerInactivePokemonRegion.x)
            .putFloat("trainer_inactive_pokemon_y", calibration.trainerInactivePokemonRegion.y)
            .putFloat("trainer_inactive_pokemon_w", calibration.trainerInactivePokemonRegion.width)
            .putFloat("trainer_inactive_pokemon_h", calibration.trainerInactivePokemonRegion.height)

            .putFloat("match_outcome_x", calibration.matchOutcomeRegion.x)
            .putFloat("match_outcome_y", calibration.matchOutcomeRegion.y)
            .putFloat("match_outcome_w", calibration.matchOutcomeRegion.width)
            .putFloat("match_outcome_h", calibration.matchOutcomeRegion.height)

            .apply()
    }

    fun load(): BattleCalibration {
        val enemyWidth = prefs.getFloat(ENEMY_W, 0f)
        val enemyRegion = if (enemyWidth > 0f) {
            AnchorRegion(
                x = prefs.getFloat(ENEMY_X, 0.75f),
                y = prefs.getFloat(ENEMY_Y, 0.10f),
                width = enemyWidth,
                height = prefs.getFloat(ENEMY_H, 0.04f)
            )
        } else {
            AnchorRegion(x = 0.75f, y = 0.10f, width = 0.20f, height = 0.04f)
        }

        val hpWidth = prefs.getFloat(HP_W, 0f)
        val hpRegion = if (hpWidth > 0f) {
            AnchorRegion(
                x = prefs.getFloat(HP_X, 0.65f),
                y = prefs.getFloat(HP_Y, 0.14f),
                width = hpWidth,
                height = prefs.getFloat(HP_H, 0.02f)
            )
        } else {
            AnchorRegion(x = 0.65f, y = 0.14f, width = 0.30f, height = 0.02f)
        }

        val teamWidth = prefs.getFloat(TEAM_W, 0f)
        val teamRegion = if (teamWidth > 0f) {
            AnchorRegion(
                x = prefs.getFloat(TEAM_X, 0.65f),
                y = prefs.getFloat(TEAM_Y, 0.16f),
                width = teamWidth,
                height = prefs.getFloat(TEAM_H, 0.04f)
            )
        } else {
            AnchorRegion(x = 0.65f, y = 0.16f, width = 0.30f, height = 0.04f)
        }

        val moveWidth = prefs.getFloat(MOVE_W, 0f)
        val moveRegion = if (moveWidth > 0f) {
            AnchorRegion(
                x = prefs.getFloat(MOVE_X, 0.000926f),
                y = prefs.getFloat(MOVE_Y, 0.288618f),
                width = moveWidth,
                height = prefs.getFloat(MOVE_H, 0.065041f)
            )
        } else {
            AnchorRegion(
                x = 0.000926f,
                y = 0.288618f,
                width = 0.999074f,
                height = 0.065041f
            )
        }

        val countdownWidth = prefs.getFloat("countdown_w", 0f)
        val countdownRegion = if (countdownWidth > 0f && countdownWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("countdown_x", 0.25f).coerceIn(0f, 1f),
                y = prefs.getFloat("countdown_y", 0.25f).coerceIn(0f, 1f),
                width = countdownWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("countdown_h", 0.30f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(x = 0.25f, y = 0.25f, width = 0.50f, height = 0.30f)
        }

        val youWinWidth = prefs.getFloat("you_win_w", 0f)
        val youWinRegion = if (youWinWidth > 0f && youWinWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("you_win_x", 0.1389f).coerceIn(0f, 1f),
                y = prefs.getFloat("you_win_y", 0.4750f).coerceIn(0f, 1f),
                width = youWinWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("you_win_h", 0.0563f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 0.1389f,
                y = 0.4750f,
                width = 0.7037f,
                height = 0.0563f
            )
        }

        val goodEffortWidth = prefs.getFloat("good_effort_w", 0f)
        val goodEffortRegion = if (goodEffortWidth > 0f && goodEffortWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("good_effort_x", 0.1944f).coerceIn(0f, 1f),
                y = prefs.getFloat("good_effort_y", 0.4333f).coerceIn(0f, 1f),
                width = goodEffortWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("good_effort_h", 0.1333f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 0.1944f,
                y = 0.4333f,
                width = 0.5926f,
                height = 0.1333f
            )
        }

        val opponentShieldWidth = prefs.getFloat("opponent_shield_w", 0f)
        val opponentShieldsRegion = if (opponentShieldWidth > 0f && opponentShieldWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("opponent_shield_x", 0.7037f).coerceIn(0f, 1f),
                y = prefs.getFloat("opponent_shield_y", 0.1208f).coerceIn(0f, 1f),
                width = opponentShieldWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("opponent_shield_h", 0.0167f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 0.7037f,
                y = 0.1208f,
                width = 0.0787f,
                height = 0.0167f
            )
        }

        val playerTeamInfoWidth = prefs.getFloat("player_team_info_w", 0f)
        val playerTeamInfoRegion = if (playerTeamInfoWidth > 0f && playerTeamInfoWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("player_team_info_x", 20f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("player_team_info_y", 225f / 2400f).coerceIn(0f, 1f),
                width = playerTeamInfoWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("player_team_info_h", (350f - 225f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 20f / 1080f,
                y = 225f / 2400f,
                width = (445f - 20f) / 1080f,
                height = (350f - 225f) / 2400f
            )
        }

        val announcementWidth = prefs.getFloat("announcement_w", 0f)
        val announcementRegion = if (announcementWidth > 0f && announcementWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("announcement_x", 0f).coerceIn(0f, 1f),
                y = prefs.getFloat("announcement_y", 710f / 2400f).coerceIn(0f, 1f),
                width = announcementWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("announcement_h", (870f - 710f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 0f / 1080f,
                y = 710f / 2400f,
                width = 1080f / 1080f,
                height = (870f - 710f) / 2400f
            )
        }

        val opponentTeamInfoWidth = prefs.getFloat("opponent_team_info_w", 0f)
        val opponentTeamInfoRegion = if (opponentTeamInfoWidth > 0f && opponentTeamInfoWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("opponent_team_info_x", 645f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("opponent_team_info_y", 225f / 2400f).coerceIn(0f, 1f),
                width = opponentTeamInfoWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("opponent_team_info_h", (350f - 225f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 645f / 1080f,
                y = 225f / 2400f,
                width = (1060f - 645f) / 1080f,
                height = (350f - 225f) / 2400f
            )
        }

        val trainerActiveTypeWidth = prefs.getFloat("trainer_active_type_w", 0f)
        val trainerActiveTypeRegion = if (trainerActiveTypeWidth > 0f && trainerActiveTypeWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("trainer_active_type_x", 20f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("trainer_active_type_y", 165f / 2400f).coerceIn(0f, 1f),
                width = trainerActiveTypeWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("trainer_active_type_h", (213f - 165f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 20f / 1080f,
                y = 165f / 2400f,
                width = (125f - 20f) / 1080f,
                height = (213f - 165f) / 2400f
            )
        }

        val opponentActiveTypeWidth = prefs.getFloat("opponent_active_type_w", 0f)
        val opponentActiveTypeRegion = if (opponentActiveTypeWidth > 0f && opponentActiveTypeWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("opponent_active_type_x", 640f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("opponent_active_type_y", 165f / 2400f).coerceIn(0f, 1f),
                width = opponentActiveTypeWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("opponent_active_type_h", (213f - 165f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 640f / 1080f,
                y = 165f / 2400f,
                width = (1060f - 640f) / 1080f,
                height = (213f - 165f) / 2400f
            )
        }

        val trainerHpWidth = prefs.getFloat("trainer_hp_w", 0f)
        val trainerHpRegion = if (trainerHpWidth > 0f && trainerHpWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("trainer_hp_x", 25f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("trainer_hp_y", 350f / 2400f).coerceIn(0f, 1f),
                width = trainerHpWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("trainer_hp_h", (1940f - 350f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 25f / 1080f,
                y = 350f / 2400f,
                width = (539f - 25f) / 1080f,
                height = (1940f - 350f) / 2400f
            )
        }

        val opponentHpWidth = prefs.getFloat("opponent_hp_w", 0f)
        val opponentHpRegion = if (opponentHpWidth > 0f && opponentHpWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("opponent_hp_x", 540f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("opponent_hp_y", 350f / 2400f).coerceIn(0f, 1f),
                width = opponentHpWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("opponent_hp_h", (1940f - 350f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 540f / 1080f,
                y = 350f / 2400f,
                width = (1055f - 540f) / 1080f,
                height = (1940f - 350f) / 2400f
            )
        }

        val chargeMoveExecutionWidth = prefs.getFloat("charge_move_execution_w", 0f)
        val chargeMoveExecutionRegion = if (chargeMoveExecutionWidth > 0f && chargeMoveExecutionWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("charge_move_execution_x", 5f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("charge_move_execution_y", 500f / 2400f).coerceIn(0f, 1f),
                width = chargeMoveExecutionWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("charge_move_execution_h", (1940f - 500f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 5f / 1080f,
                y = 500f / 2400f,
                width = (1075f - 5f) / 1080f,
                height = (1940f - 500f) / 2400f
            )
        }

        val trainerChargeMoveControlsWidth = prefs.getFloat("trainer_charge_move_controls_w", 0f)
        val trainerChargeMoveControlsRegion = if (trainerChargeMoveControlsWidth > 0f && trainerChargeMoveControlsWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("trainer_charge_move_controls_x", 100f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("trainer_charge_move_controls_y", 1940f / 2400f).coerceIn(0f, 1f),
                width = trainerChargeMoveControlsWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("trainer_charge_move_controls_h", (2245f - 1940f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 100f / 1080f,
                y = 1940f / 2400f,
                width = (980f - 100f) / 1080f,
                height = (2245f - 1940f) / 2400f
            )
        }

        val trainerInactivePokemonWidth = prefs.getFloat("trainer_inactive_pokemon_w", 0f)
        val trainerInactivePokemonRegion = if (trainerInactivePokemonWidth > 0f && trainerInactivePokemonWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("trainer_inactive_pokemon_x", 890f / 1080f).coerceIn(0f, 1f),
                y = prefs.getFloat("trainer_inactive_pokemon_y", 1370f / 2400f).coerceIn(0f, 1f),
                width = trainerInactivePokemonWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("trainer_inactive_pokemon_h", (1750f - 1370f) / 2400f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 890f / 1080f,
                y = 1370f / 2400f,
                width = (1050f - 890f) / 1080f,
                height = (1750f - 1370f) / 2400f
            )
        }

        val matchOutcomeWidth = prefs.getFloat("match_outcome_w", 0f)
        val matchOutcomeRegion = if (matchOutcomeWidth > 0f && matchOutcomeWidth <= 1.0f) {
            AnchorRegion(
                x = prefs.getFloat("match_outcome_x", 0.2f).coerceIn(0f, 1f),
                y = prefs.getFloat("match_outcome_y", 0.4f).coerceIn(0f, 1f),
                width = matchOutcomeWidth.coerceIn(0.01f, 1f),
                height = prefs.getFloat("match_outcome_h", 0.2f).coerceIn(0.01f, 1f)
            )
        } else {
            AnchorRegion(
                x = 0.2f,
                y = 0.4f,
                width = 0.6f,
                height = 0.2f
            )
        }

        return BattleCalibration(
            enemyNameRegion = enemyRegion,
            hpBarRegion = hpRegion,
            teamIconsRegion = teamRegion,
            moveBannerRegion = moveRegion,
            countdownRegion = countdownRegion,
            youWinRegion = youWinRegion,
            goodEffortRegion = goodEffortRegion,
            opponentShieldsRegion = opponentShieldsRegion,
            playerTeamInfoRegion = playerTeamInfoRegion,
            announcementRegion = announcementRegion,
            opponentTeamInfoRegion = opponentTeamInfoRegion,
            trainerActiveTypeRegion = trainerActiveTypeRegion,
            opponentActiveTypeRegion = opponentActiveTypeRegion,
            trainerHpRegion = trainerHpRegion,
            opponentHpRegion = opponentHpRegion,
            chargeMoveExecutionRegion = chargeMoveExecutionRegion,
            trainerChargeMoveControlsRegion = trainerChargeMoveControlsRegion,
            trainerInactivePokemonRegion = trainerInactivePokemonRegion,
            matchOutcomeRegion = matchOutcomeRegion
        )
    }
}
