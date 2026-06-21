package com.example.overdex

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