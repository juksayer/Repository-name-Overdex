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
    // Measured from the 1080 x 2280 captured frame: this contains the central
    // 3 / 2 / 1 / GO glyph while leaving the team badges and battlefield out.
    val countdownRegion: AnchorRegion = DEFAULT_COUNTDOWN_REGION,
    // VS occupies the central transition surface, distinct from the upper glyph.
    val vsScreenRegion: AnchorRegion = DEFAULT_VS_SCREEN_REGION,
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
    // Purpose-specific crops inside the opponent badge. They are independently
    // calibrated so a text adjustment cannot disturb shield or ball evidence.
    val opponentSpeciesNameRegion: AnchorRegion = AnchorRegion(
        // Mirrored player-name strip: source X 785–1038, Y 250–275.
        // It contains even long, right-aligned opponent names without CP or balls.
        x = 785f / 1080f, y = 250f / 2400f,
        width = (1038f - 785f) / 1080f, height = (275f - 250f) / 2400f
    ),
    val opponentPokeBallsRegion: AnchorRegion = AnchorRegion(
        x = 875f / 1080f, y = 280f / 2400f,
        width = (1060f - 875f) / 1080f, height = (340f - 280f) / 2400f
    ),
    val trainerActiveTypeRegion: AnchorRegion = AnchorRegion(
        x = 20f / 1080f,
        y = 145f / 2400f,
        width = (135f - 20f) / 1080f,
        height = (205f - 145f) / 2400f
    ),
    val opponentActiveTypeRegion: AnchorRegion = AnchorRegion(
        // Two badges plus a modest margin, matching the player-side default.
        x = 795f / 1080f,
        y = 145f / 2400f,
        width = (925f - 795f) / 1080f,
        height = (205f - 145f) / 2400f
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
    val battlePartyTabsRegion: AnchorRegion = AnchorRegion(x = 0f, y = 100f / 2400f, width = 1f, height = 150f / 2400f),
    val outOfBattleMenuRegion: AnchorRegion = AnchorRegion(
        x = 645f / 1080f, y = 350f / 2400f,
        width = (1060f - 645f) / 1080f, height = (550f - 350f) / 2400f
    ),
    val matchOutcomeRegion: AnchorRegion = AnchorRegion(
        x = 0.2f,
        y = 0.4f,
        width = 0.6f,
        height = 0.2f
    )
) {
    companion object {
        /** Previous shipped box, retained only to migrate it without overwriting user work. */
        val PREVIOUS_DEFAULT_COUNTDOWN_REGION = AnchorRegion(
            x = 330f / 1080f, y = 110f / 2280f,
            width = (760f - 330f) / 1080f, height = (510f - 110f) / 2280f
        )

        /** 3 / 2 / 1 / GO occupies the central battlefield, below the team badges. */
        val DEFAULT_COUNTDOWN_REGION = AnchorRegion(x = 0.33f, y = 0.35f, width = 0.34f, height = 0.30f)

        /** Previous broad VS surface, retained only to migrate it without overwriting user work. */
        val PREVIOUS_DEFAULT_VS_SCREEN_REGION = AnchorRegion(x = 0.25f, y = 0.25f, width = 0.50f, height = 0.30f)

        /** The VS disc and its immediate visual surround, not the two trainer portraits. */
        val DEFAULT_VS_SCREEN_REGION = AnchorRegion(x = 0.38f, y = 0.42f, width = 0.24f, height = 0.16f)

        /** The pre-contract fallback that never contained the real countdown glyph. */
        val LEGACY_COUNTDOWN_REGION = PREVIOUS_DEFAULT_VS_SCREEN_REGION
    }

    fun isCalibrated(): Boolean {
        return enemyNameRegion.width > 0f
    }
}
