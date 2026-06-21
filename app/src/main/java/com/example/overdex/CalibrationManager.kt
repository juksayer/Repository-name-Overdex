package com.example.overdex

import android.content.Context

class CalibrationManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("overmon_calibration", Context.MODE_PRIVATE)

    fun save(calibration: BattleCalibration) {
        // We'll implement this after the refactor compiles.
    }

    fun load(): BattleCalibration {
        return BattleCalibration()
    }
}