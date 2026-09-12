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
    val moveBannerRegion: AnchorRegion = AnchorRegion(
        x = 0.000926f,
        y = 0.288618f,
        width = 0.999074f,
        height = 0.065041f
    ),
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
    ),
    val opponentShieldsRegion: AnchorRegion = AnchorRegion(
        x = 0.7037f,
        y = 0.1208f,
        width = 0.0787f,
        height = 0.0167f
    ),
    val playerTeamInfoRegion: AnchorRegion = AnchorRegion(
        x = 20f / 1080f,
        y = 225f / 2400f,
        width = (445f - 20f) / 1080f,
        height = (350f - 225f) / 2400f
    ),
    val announcementRegion: AnchorRegion = AnchorRegion(
        x = 0f / 1080f,
        y = 710f / 2400f,
        width = 1080f / 1080f,
        height = (870f - 710f) / 2400f
    ),
    val opponentTeamInfoRegion: AnchorRegion = AnchorRegion(
        x = 645f / 1080f,
        y = 225f / 2400f,
        width = (1060f - 645f) / 1080f,
        height = (350f - 225f) / 2400f
    ),
    val trainerActiveTypeRegion: AnchorRegion = AnchorRegion(
        x = 20f / 1080f,
        y = 165f / 2400f,
        width = (125f - 20f) / 1080f,
        height = (213f - 165f) / 2400f
    ),
    val opponentActiveTypeRegion: AnchorRegion = AnchorRegion(
        x = 640f / 1080f,
        y = 165f / 2400f,
        width = (1060f - 640f) / 1080f,
        height = (213f - 165f) / 2400f
    ),
    val trainerHpRegion: AnchorRegion = AnchorRegion(
        x = 25f / 1080f,
        y = 350f / 2400f,
        width = (539f - 25f) / 1080f,
        height = (1940f - 350f) / 2400f
    ),
    val opponentHpRegion: AnchorRegion = AnchorRegion(
        x = 540f / 1080f,
        y = 350f / 2400f,
        width = (1055f - 540f) / 1080f,
        height = (1940f - 350f) / 2400f
    ),
    val chargeMoveExecutionRegion: AnchorRegion = AnchorRegion(
        x = 5f / 1080f,
        y = 500f / 2400f,
        width = (1075f - 5f) / 1080f,
        height = (1940f - 500f) / 2400f
    ),
    val trainerChargeMoveControlsRegion: AnchorRegion = AnchorRegion(
        x = 100f / 1080f,
        y = 1940f / 2400f,
        width = (980f - 100f) / 1080f,
        height = (2245f - 1940f) / 2400f
    ),
    val trainerInactivePokemonRegion: AnchorRegion = AnchorRegion(
        x = 890f / 1080f,
        y = 1370f / 2400f,
        width = (1050f - 890f) / 1080f,
        height = (1750f - 1370f) / 2400f
    ),
    val matchOutcomeRegion: AnchorRegion = AnchorRegion(
        x = 0.2f,
        y = 0.4f,
        width = 0.6f,
        height = 0.2f
    )
) {
    fun isCalibrated(): Boolean {
        return enemyNameRegion.width > 0f
    }
}
