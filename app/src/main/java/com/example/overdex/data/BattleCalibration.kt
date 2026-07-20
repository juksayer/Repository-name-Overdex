package com.example.overdex

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
    val moveBannerRegion: AnchorRegion = AnchorRegion()
) {
    fun isCalibrated(): Boolean {
        return enemyNameRegion.width > 0f
    }
}