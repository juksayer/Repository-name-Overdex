package com.example.overdex.data

import com.example.overdex.model.AnchorRegion
/**
 * Defines the user-calibrated regions for different battle UI elements.
 *
 * These regions are used to offset the standard observation regions for
 * devices with unusual aspect ratios or UI scales.
 */
data class BattleCalibration(
    val enemyNameRegion: AnchorRegion = AnchorRegion(),
    val hpBarRegion: AnchorRegion = AnchorRegion(),
    val teamIconsRegion: AnchorRegion = AnchorRegion(),
    val moveBannerRegion: AnchorRegion = AnchorRegion(),
    val countdownRegion: AnchorRegion = AnchorRegion(),
    val youWinRegion: AnchorRegion = AnchorRegion(
        x = 0.1389f,
        y = 0.4750f,
        width = 0.7037f,
        height = 0.0563f
    ),
    val goodEffortRegion: AnchorRegion = AnchorRegion(
        x = 0.1944f,
        y = 0.4333f,
        width = 0.5926f,
        height = 0.1333f
    )
) {
    fun isCalibrated(): Boolean {
        return enemyNameRegion.width > 0f
    }
}