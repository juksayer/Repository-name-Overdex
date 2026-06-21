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
    }

    fun save(calibration: BattleCalibration) {

        prefs.edit()
            .putFloat(ENEMY_X, calibration.enemyNameRegion.x)
            .putFloat(ENEMY_Y, calibration.enemyNameRegion.y)
            .putFloat(ENEMY_W, calibration.enemyNameRegion.width)
            .putFloat(ENEMY_H, calibration.enemyNameRegion.height)
            .apply()
    }

    fun load(): BattleCalibration {

        val enemyRegion = AnchorRegion(
            x = prefs.getFloat(ENEMY_X, 0f),
            y = prefs.getFloat(ENEMY_Y, 0f),
            width = prefs.getFloat(ENEMY_W, 0f),
            height = prefs.getFloat(ENEMY_H, 0f)
        )

        return BattleCalibration(
            enemyNameRegion = enemyRegion
        )
    }
}