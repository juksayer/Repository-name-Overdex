package com.example.overdex

import android.content.Context

class CalibrationManager(context: Context) {

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

            .apply()
    }

    fun load(): BattleCalibration {

        val enemyRegion = AnchorRegion(
            x = prefs.getFloat(ENEMY_X, 0f),
            y = prefs.getFloat(ENEMY_Y, 0f),
            width = prefs.getFloat(ENEMY_W, 0f),
            height = prefs.getFloat(ENEMY_H, 0f)
        )

        val hpRegion = AnchorRegion(
            x = prefs.getFloat(HP_X, 0f),
            y = prefs.getFloat(HP_Y, 0f),
            width = prefs.getFloat(HP_W, 0f),
            height = prefs.getFloat(HP_H, 0f)
        )

        val teamRegion = AnchorRegion(
            x = prefs.getFloat(TEAM_X, 0f),
            y = prefs.getFloat(TEAM_Y, 0f),
            width = prefs.getFloat(TEAM_W, 0f),
            height = prefs.getFloat(TEAM_H, 0f)
        )

        val moveRegion = AnchorRegion(
            x = prefs.getFloat(MOVE_X, 0f),
            y = prefs.getFloat(MOVE_Y, 0f),
            width = prefs.getFloat(MOVE_W, 0f),
            height = prefs.getFloat(MOVE_H, 0f)
        )

        return BattleCalibration(
            enemyNameRegion = enemyRegion,
            hpBarRegion = hpRegion,
            teamIconsRegion = teamRegion,
            moveBannerRegion = moveRegion
        )
    }
}