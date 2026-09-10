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
                height = prefs.getFloat("good_effort_h", 0.1333f).coerceIn(0f, 1f)
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

        Log.d(
            "CALIBRATION",
            "Countdown: x=${countdownRegion.x}, y=${countdownRegion.y}, w=${countdownRegion.width}, h=${countdownRegion.height}"
        )
        return BattleCalibration(
            enemyNameRegion = enemyRegion,
            hpBarRegion = hpRegion,
            teamIconsRegion = teamRegion,
            moveBannerRegion = moveRegion,
            countdownRegion = countdownRegion,
            youWinRegion = youWinRegion,
            goodEffortRegion = goodEffortRegion,
            opponentShieldsRegion = opponentShieldsRegion
        )
    }
}