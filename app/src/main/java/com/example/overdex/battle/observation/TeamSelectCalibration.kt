package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.content.Context
import android.os.Build
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
    val rosterName1: AnchorRegion,
    val rosterName2: AnchorRegion,
    val rosterName3: AnchorRegion,
    val leagueBadge: AnchorRegion,
    val leagueText: AnchorRegion,
    val restrictions: AnchorRegion,
    val useThisParty: AnchorRegion
) {
    companion object {
        val measured1080x2400 = TeamSelectCalibration(
            rosterSlot1 = region(150, 1630, 350, 1835),
            rosterSlot2 = region(435, 1630, 640, 1835),
            rosterSlot3 = region(725, 1630, 930, 1835),
            // Purpose-specific OCR apertures below the sprite cards. Card
            // crops establish the surface; these strips read only names.
            rosterName1 = region(105, 1840, 385, 1940),
            rosterName2 = region(390, 1840, 680, 1940),
            rosterName3 = region(680, 1840, 975, 1940),
            // Bounding rectangle for the triangular league shield measured on
            // the Moondrop 1080 x 2400 display. The lower corners are empty by
            // design; the classifier samples only the triangle interior.
            leagueBadge = region(400, 475, 675, 740),
            leagueText = region(150, 830, 930, 880),
            restrictions = region(150, 930, 930, 975),
            // The complete green control, including its white label. Keeping
            // the surrounding white card out of this aperture makes the
            // control's color and OCR independent Team Select evidence.
            useThisParty = region(245, 1960, 835, 2140)
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
    val playerRosterName1 = TeamSelectCropContract("TeamSelectPlayerRosterName1Crop", { it.rosterName1 }, minimumSize = 16)
    val playerRosterName2 = TeamSelectCropContract("TeamSelectPlayerRosterName2Crop", { it.rosterName2 }, minimumSize = 16)
    val playerRosterName3 = TeamSelectCropContract("TeamSelectPlayerRosterName3Crop", { it.rosterName3 }, minimumSize = 16)
    val leagueBadge = TeamSelectCropContract("TeamSelectLeagueBadgeCrop", { it.leagueBadge })
    // The 50-pixel source row is about 31 pixels high in the 62.5% capture
    // stream. It is valid text evidence; OCR enlarges its working copy.
    val leagueText = TeamSelectCropContract("TeamSelectLeagueTextCrop", { it.leagueText }, minimumSize = 16)
    val restrictions = TeamSelectCropContract("TeamSelectRestrictionsCrop", { it.restrictions }, minimumSize = 16)
    val useThisParty = TeamSelectCropContract("TeamSelectUseThisPartyCrop", { it.useThisParty })
}

/**
 * Device-local Team Select profile. It lives in app data rather than the APK,
 * so a normal update never replaces a user's Draggy Box adjustments.
 */
class TeamSelectCalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("team_select_calibration", Context.MODE_PRIVATE)

    fun hasSavedProfile(): Boolean = prefs.contains(SAVED_AT)

    fun save(calibration: TeamSelectCalibration, publishedWidth: Int = 1080, publishedHeight: Int = 2400): Boolean {
        val editor = prefs.edit()
        fields(calibration).forEach { (name, region) ->
            editor.putFloat("${name}_x", region.x)
            editor.putFloat("${name}_y", region.y)
            editor.putFloat("${name}_w", region.width)
            editor.putFloat("${name}_h", region.height)
        }
        return editor
            .putInt(VERSION, PROFILE_VERSION)
            .putLong(SAVED_AT, System.currentTimeMillis())
            .putString(DEVICE_MODEL, Build.MODEL.orEmpty())
            .putInt(PUBLISHED_WIDTH, publishedWidth)
            .putInt(PUBLISHED_HEIGHT, publishedHeight)
            .putString(ORIENTATION, "PORTRAIT")
            .putString(ORIGIN, "MANUAL")
            .commit()
    }

    fun load(): TeamSelectCalibration {
        val defaults = TeamSelectCalibration.measured1080x2400
        fun region(name: String, fallback: AnchorRegion): AnchorRegion {
            val width = prefs.getFloat("${name}_w", fallback.width)
            val height = prefs.getFloat("${name}_h", fallback.height)
            val x = prefs.getFloat("${name}_x", fallback.x).coerceIn(0f, 0.99f)
            val y = prefs.getFloat("${name}_y", fallback.y).coerceIn(0f, 0.99f)
            return AnchorRegion(
                x = x,
                y = y,
                width = width.coerceIn(0.01f, (1f - x).coerceAtLeast(0.01f)),
                height = height.coerceIn(0.01f, (1f - y).coerceAtLeast(0.01f))
            )
        }
        return TeamSelectCalibration(
            rosterSlot1 = region("roster_1", defaults.rosterSlot1),
            rosterSlot2 = region("roster_2", defaults.rosterSlot2),
            rosterSlot3 = region("roster_3", defaults.rosterSlot3),
            rosterName1 = region("roster_name_1", defaults.rosterName1),
            rosterName2 = region("roster_name_2", defaults.rosterName2),
            rosterName3 = region("roster_name_3", defaults.rosterName3),
            leagueBadge = region("league_badge", defaults.leagueBadge),
            leagueText = region("league_text", defaults.leagueText),
            restrictions = region("restrictions", defaults.restrictions),
            useThisParty = region("use_this_party", defaults.useThisParty)
        )
    }

    private fun fields(calibration: TeamSelectCalibration): Map<String, AnchorRegion> = linkedMapOf(
        "roster_1" to calibration.rosterSlot1,
        "roster_2" to calibration.rosterSlot2,
        "roster_3" to calibration.rosterSlot3,
        "roster_name_1" to calibration.rosterName1,
        "roster_name_2" to calibration.rosterName2,
        "roster_name_3" to calibration.rosterName3,
        "league_badge" to calibration.leagueBadge,
        "league_text" to calibration.leagueText,
        "restrictions" to calibration.restrictions,
        "use_this_party" to calibration.useThisParty
    )

    private companion object {
        const val PROFILE_VERSION = 2
        const val VERSION = "profile_version"
        const val SAVED_AT = "saved_at_millis"
        const val DEVICE_MODEL = "device_model"
        const val PUBLISHED_WIDTH = "published_width"
        const val PUBLISHED_HEIGHT = "published_height"
        const val ORIENTATION = "orientation"
        const val ORIGIN = "origin"
    }
}
