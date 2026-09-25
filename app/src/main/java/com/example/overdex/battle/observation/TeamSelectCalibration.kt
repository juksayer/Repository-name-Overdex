package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.AnchorRegion

/**
 * Calibrated geography for Pokémon GO's pre-battle Team Select surface.
 * This is session setup, deliberately separate from BattleCalibration's live HUD.
 *
 * Defaults were measured from a 1080 x 2400 published frame.
 */
data class TeamSelectCalibration(
    val rosterSlot1: AnchorRegion,
    val rosterSlot2: AnchorRegion,
    val rosterSlot3: AnchorRegion,
    val leagueBadge: AnchorRegion,
    val leagueText: AnchorRegion,
    val restrictions: AnchorRegion
) {
    companion object {
        val measured1080x2400 = TeamSelectCalibration(
            rosterSlot1 = region(150, 1630, 350, 1835),
            rosterSlot2 = region(435, 1630, 640, 1835),
            rosterSlot3 = region(725, 1630, 930, 1835),
            leagueBadge = region(410, 525, 675, 770),
            leagueText = region(150, 830, 930, 880),
            restrictions = region(150, 930, 930, 975)
        )

        private fun region(left: Int, top: Int, right: Int, bottom: Int) = AnchorRegion(
            x = left / 1080f,
            y = top / 2400f,
            width = (right - left) / 1080f,
            height = (bottom - top) / 2400f
        )
    }
}

/** One purpose-specific view of a Team Select region. */
data class TeamSelectCropContract(
    val cropName: String,
    val region: (TeamSelectCalibration) -> AnchorRegion,
    val minimumSize: Int = 32
) {
    fun resolve(calibration: TeamSelectCalibration, source: Bitmap): ResolvedBattleCrop? =
        BattleCropResolver.resolve(cropName, region(calibration), source, minimumSize)
}

object TeamSelectCropContracts {
    val playerRosterSlot1 = TeamSelectCropContract("TeamSelectPlayerRosterSlot1Crop", { it.rosterSlot1 })
    val playerRosterSlot2 = TeamSelectCropContract("TeamSelectPlayerRosterSlot2Crop", { it.rosterSlot2 })
    val playerRosterSlot3 = TeamSelectCropContract("TeamSelectPlayerRosterSlot3Crop", { it.rosterSlot3 })
    val leagueBadge = TeamSelectCropContract("TeamSelectLeagueBadgeCrop", { it.leagueBadge })
    val leagueText = TeamSelectCropContract("TeamSelectLeagueTextCrop", { it.leagueText })
    val restrictions = TeamSelectCropContract("TeamSelectRestrictionsCrop", { it.restrictions })
}
