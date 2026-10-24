package com.example.overdex.battle.observation

import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import kotlin.math.abs

/**
 * Recover a displaced resource row from the same side's native name row.
 * Every box keeps its own calibration: a usable assigned box wins, and a
 * translated box must independently contain the expected badge/ball/shield
 * shapes. This is capture geometry only and never writes a calibration profile.
 */
internal object TeamBadgeCropLocator {
    fun locate(region: BattleRegionId, calibration: BattleCalibration, frame: CapturedVisualFrame): AnchorRegion {
        val assigned = region.regionIn(calibration)
        val name = when (region) {
            BattleRegionId.PLAYER_TEAM_INFO, BattleRegionId.PLAYER_POKE_BALLS, BattleRegionId.PLAYER_SHIELDS ->
                calibration.playerSpeciesNameRegion
            BattleRegionId.OPPONENT_TEAM_INFO, BattleRegionId.OPPONENT_POKE_BALLS, BattleRegionId.OPPONENT_SHIELDS ->
                calibration.opponentSpeciesNameRegion
            else -> return assigned
        }
        if (frame.canObserve(assigned) && containsExpectedShape(region, assigned, frame)) return assigned
        val located = SpeciesBadgeTextLocator.locate(frame.bitmap, name,
            listOfNotNull(frame.overlayRegion, frame.retainedBadgeRegion))
        val offset = located.y - name.y
        if (abs(offset) * frame.bitmap.height < 1f ||
            !SpeciesCaptureVisibility.canRead(located, frame.overlayRegion) || !frame.canObserve(located)) return assigned
        val translated = assigned.copy(y = assigned.y + offset)
        if (translated.y < 0f || translated.y + translated.height > 1f || !frame.canObserve(translated)) return assigned
        return if (containsExpectedShape(region, translated, frame)) translated else assigned
    }

    /** The HUD's teal text and our retained image cannot establish a native badge. */
    fun nativeBadgePresent(frame: CapturedVisualFrame, area: AnchorRegion): Boolean {
        if (!frame.canObserve(area)) return false
        val bitmap = frame.bitmap
        val hud = frame.overlayRegion?.let { BattleCropResolver.resolveRect(it, bitmap.width, bitmap.height) }
        return TeamBadgePresenceDetector.present(bitmap.width, bitmap.height, area) { x, y ->
            if (hud?.contains(x, y) == true) 0 else bitmap.getPixel(x, y)
        }
    }

    private fun containsExpectedShape(region: BattleRegionId, area: AnchorRegion, frame: CapturedVisualFrame): Boolean {
        if (region == BattleRegionId.PLAYER_TEAM_INFO || region == BattleRegionId.OPPONENT_TEAM_INFO) {
            return nativeBadgePresent(frame, area)
        }
        val bitmap = frame.bitmap
        val rect = BattleCropResolver.resolveRect(area, bitmap.width, bitmap.height) ?: return false
        val provenance = BattleCropProvenance(region.name, bitmap.width, bitmap.height,
            BattleCropBounds(rect.left, rect.top, rect.right, rect.bottom))
        val pixel: (Int, Int) -> Int = { x, y -> bitmap.getPixel(rect.left + x, rect.top + y) }
        return containsResourceShape(region, provenance, frame.overlayRegion, pixel)
    }

    internal fun containsResourceShape(
        region: BattleRegionId,
        provenance: BattleCropProvenance,
        overlayRegion: AnchorRegion?,
        pixel: (Int, Int) -> Int
    ): Boolean {
        val width = provenance.bounds.right - provenance.bounds.left
        val height = provenance.bounds.bottom - provenance.bounds.top
        // Use the same visibility contract as the resource witness. Otherwise HUD
        // cards can pass this check, prevent relocation, then be rejected downstream.
        val overlayBounds = provenance.overlayBounds(overlayRegion)
        return when (region) {
            BattleRegionId.PLAYER_POKE_BALLS, BattleRegionId.OPPONENT_POKE_BALLS ->
                OpponentBattleResourceCounter.pokeBallCount(width, height, overlayBounds, pixel) != null
            BattleRegionId.PLAYER_SHIELDS, BattleRegionId.OPPONENT_SHIELDS ->
                OpponentBattleResourceCounter.shieldCount(width, height, overlayBounds, pixel) != null
            else -> false
        }
    }
}
