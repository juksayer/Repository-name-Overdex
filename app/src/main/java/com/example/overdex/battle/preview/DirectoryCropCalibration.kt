package com.example.overdex.battle.preview

import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import kotlin.math.floor
import kotlin.math.roundToInt

/** The saved territory for one Directory LCD crop, independent of its archived image geometry. */
fun BattleCalibration.directoryCropRegion(slot: DirectoryCropSlot): AnchorRegion = when (slot) {
    DirectoryCropSlot.PLAYER_SPECIES -> playerSpeciesNameRegion
    DirectoryCropSlot.OPPONENT_SPECIES -> opponentSpeciesNameRegion
    DirectoryCropSlot.PLAYER_HP -> trainerHpRegion
    DirectoryCropSlot.OPPONENT_HP -> opponentHpRegion
    DirectoryCropSlot.PLAYER_BALLS -> playerPokeBallsRegion
    DirectoryCropSlot.OPPONENT_BALLS -> opponentPokeBallsRegion
    DirectoryCropSlot.ANNOUNCEMENT -> announcementRegion
    DirectoryCropSlot.CHARGED_IMPACT_HP -> chargedImpactHpRegion
}

/**
 * Apply a position-only edit to exactly one saved territory. Invalid or resized requests leave the
 * profile unchanged; finite positions are clamped without changing the original width or height.
 * The caller checks for a concurrent change before applying this pure copy to its latest profile.
 */
fun BattleCalibration.withDirectoryCropRegion(slot: DirectoryCropSlot, updated: AnchorRegion): BattleCalibration {
    val original = directoryCropRegion(slot)
    if (!updated.validDirectoryCropExtent() ||
        updated.width != original.width || updated.height != original.height) return this
    val bounded = updated.copy(
        x = updated.x.coerceIn(0f, 1f - original.width),
        y = updated.y.coerceIn(0f, 1f - original.height),
    )
    return when (slot) {
        DirectoryCropSlot.PLAYER_SPECIES -> copy(playerSpeciesNameRegion = bounded)
        DirectoryCropSlot.OPPONENT_SPECIES -> copy(opponentSpeciesNameRegion = bounded)
        DirectoryCropSlot.PLAYER_HP -> copy(trainerHpRegion = bounded)
        DirectoryCropSlot.OPPONENT_HP -> copy(opponentHpRegion = bounded)
        DirectoryCropSlot.PLAYER_BALLS -> copy(playerPokeBallsRegion = bounded)
        DirectoryCropSlot.OPPONENT_BALLS -> copy(opponentPokeBallsRegion = bounded)
        DirectoryCropSlot.ANNOUNCEMENT -> copy(announcementRegion = bounded)
        DirectoryCropSlot.CHARGED_IMPACT_HP -> copy(chargedImpactHpRegion = bounded)
    }
}

/** Exclusive right/bottom source-pixel bounds for presentation, with no Android graphics dependency. */
data class DirectoryCropPixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Draw the same full-region pixels as BattleCropResolver: floor the left/top, round the right/bottom.
 * This only projects saved/draft geometry for display; it does not create or reinterpret evidence.
 */
fun directoryCropPixelBounds(region: AnchorRegion, sourceWidth: Int, sourceHeight: Int): DirectoryCropPixelBounds? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || !region.validDirectoryCropExtent() ||
        region.x < 0f || region.y < 0f ||
        region.x + region.width > 1.000001f || region.y + region.height > 1.000001f) return null
    val bounds = DirectoryCropPixelBounds(
        floor(region.x * sourceWidth).toInt().coerceIn(0, sourceWidth),
        floor(region.y * sourceHeight).toInt().coerceIn(0, sourceHeight),
        ((region.x + region.width) * sourceWidth).roundToInt().coerceIn(0, sourceWidth),
        ((region.y + region.height) * sourceHeight).roundToInt().coerceIn(0, sourceHeight),
    )
    return bounds.takeIf { it.width > 0 && it.height > 0 }
}

private fun AnchorRegion.validDirectoryCropExtent(): Boolean =
    x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() &&
        width > 0f && width <= 1f && height > 0f && height <= 1f
