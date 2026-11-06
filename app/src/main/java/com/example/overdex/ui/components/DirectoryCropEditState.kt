package com.example.overdex.ui.components

import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.model.AnchorRegion

enum class DirectoryCropEditDirection { UP, DOWN, LEFT, RIGHT }

/** One explicit save request. Applying it to a device profile belongs to the caller. */
data class DirectoryCropEditSave(val slot: DirectoryCropSlot, val region: AnchorRegion)

data class DirectoryCropEditTransition(
    val state: DirectoryCropEditState,
    val save: DirectoryCropEditSave? = null,
    val returnToDirectory: Boolean = false,
)

/** Position-only draft measured against the full original source, never the LCD or cropped PNG. */
data class DirectoryCropPositionDraft(
    val original: AnchorRegion,
    val region: AnchorRegion,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    val dirty: Boolean get() = region != original

    fun moveBySourcePixels(dx: Float, dy: Float): DirectoryCropPositionDraft =
        moveByNormalized(dx / sourceWidth, dy / sourceHeight)

    /** Use the fitted full-source image size, excluding any letterboxing. */
    fun moveByPreviewPixels(
        dx: Float, dy: Float, displayedSourceWidthPixels: Float, displayedSourceHeightPixels: Float,
    ): DirectoryCropPositionDraft {
        if (!displayedSourceWidthPixels.isFinite() || !displayedSourceHeightPixels.isFinite() ||
            displayedSourceWidthPixels <= 0f || displayedSourceHeightPixels <= 0f) return this
        return moveByNormalized(dx / displayedSourceWidthPixels, dy / displayedSourceHeightPixels)
    }

    fun moveByNormalized(dx: Float, dy: Float): DirectoryCropPositionDraft {
        if (!dx.isFinite() || !dy.isFinite()) return this
        return copy(region = region.copy(
            x = (region.x + dx).coerceIn(0f, 1f - region.width),
            y = (region.y + dy).coerceIn(0f, 1f - region.height),
        ))
    }

    companion object {
        fun create(region: AnchorRegion, sourceWidth: Int, sourceHeight: Int): DirectoryCropPositionDraft? {
            if (sourceWidth <= 0 || sourceHeight <= 0 ||
                !region.x.isFinite() || !region.y.isFinite() ||
                !region.width.isFinite() || !region.height.isFinite() ||
                region.width <= 0f || region.width > 1f || region.height <= 0f || region.height > 1f) return null
            return DirectoryCropPositionDraft(region, region.copy(
                x = region.x.coerceIn(0f, 1f - region.width),
                y = region.y.coerceIn(0f, 1f - region.height),
            ), sourceWidth, sourceHeight)
        }
    }
}

/**
 * Pure Directory/LCD focus and position editing. No image loading, profile I/O, or evidence changes.
 * Selection follows the seven displayed slots in row-major order and clamps like InstrumentTree.
 */
data class DirectoryCropEditState(
    val selectedSlot: DirectoryCropSlot? = null,
    val draft: DirectoryCropPositionDraft? = null,
) {
    init { require(draft == null || selectedSlot != null) }

    val isEditing: Boolean get() = draft != null
    val dirty: Boolean get() = draft?.dirty == true

    /** Optional focus helper; ordinary directory Down no longer calls this transition. */
    fun enterFromDirectory(atLastVisibleEntry: Boolean): DirectoryCropEditState =
        if (selectedSlot == null && atLastVisibleEntry) copy(selectedSlot = slots.first()) else this

    /** Direct LCD selection cannot silently abandon an unsaved draft. */
    fun select(slot: DirectoryCropSlot): DirectoryCropEditState =
        if (isEditing) this else copy(selectedSlot = slot)

    fun navigate(direction: DirectoryCropEditDirection): DirectoryCropEditTransition {
        draft?.let { editing ->
            val (dx, dy) = when (direction) {
                DirectoryCropEditDirection.UP -> 0f to -1f
                DirectoryCropEditDirection.DOWN -> 0f to 1f
                DirectoryCropEditDirection.LEFT -> -1f to 0f
                DirectoryCropEditDirection.RIGHT -> 1f to 0f
            }
            return DirectoryCropEditTransition(copy(draft = editing.moveBySourcePixels(dx, dy)))
        }
        val slot = selectedSlot ?: return DirectoryCropEditTransition(this)
        if (slot == slots.first() && direction == DirectoryCropEditDirection.UP) {
            return DirectoryCropEditTransition(DirectoryCropEditState(), returnToDirectory = true)
        }
        val delta = when (direction) {
            DirectoryCropEditDirection.UP, DirectoryCropEditDirection.LEFT -> -1
            DirectoryCropEditDirection.DOWN, DirectoryCropEditDirection.RIGHT -> 1
        }
        return DirectoryCropEditTransition(copy(selectedSlot = slots[(slots.indexOf(slot) + delta).coerceIn(slots.indices)]))
    }

    fun beginEdit(region: AnchorRegion, sourceWidth: Int, sourceHeight: Int): DirectoryCropEditState {
        if (selectedSlot == null || isEditing) return this
        val editing = DirectoryCropPositionDraft.create(region, sourceWidth, sourceHeight) ?: return this
        return copy(draft = editing)
    }

    fun moveByNormalized(dx: Float, dy: Float): DirectoryCropEditState =
        draft?.let { copy(draft = it.moveByNormalized(dx, dy)) } ?: this

    fun moveByPreviewPixels(
        dx: Float, dy: Float, displayedSourceWidthPixels: Float, displayedSourceHeightPixels: Float,
    ): DirectoryCropEditState = draft?.let { copy(draft = it.moveByPreviewPixels(
        dx, dy, displayedSourceWidthPixels, displayedSourceHeightPixels)) } ?: this

    /** A saves the draft and leaves its slot selected. Keep the old state if persistence fails. */
    fun confirm(): DirectoryCropEditTransition {
        val editing = draft ?: return DirectoryCropEditTransition(this)
        return DirectoryCropEditTransition(copy(draft = null),
            save = DirectoryCropEditSave(requireNotNull(selectedSlot), editing.region))
    }

    /** B discards a draft first; a second B returns focus to the directory. */
    fun cancel(): DirectoryCropEditTransition = when {
        isEditing -> DirectoryCropEditTransition(copy(draft = null))
        selectedSlot != null -> DirectoryCropEditTransition(DirectoryCropEditState(), returnToDirectory = true)
        else -> DirectoryCropEditTransition(this)
    }

    companion object {
        val slots: List<DirectoryCropSlot> = listOf(
            DirectoryCropSlot.PLAYER_SPECIES, DirectoryCropSlot.OPPONENT_SPECIES,
            DirectoryCropSlot.PLAYER_HP, DirectoryCropSlot.OPPONENT_HP,
            DirectoryCropSlot.PLAYER_BALLS, DirectoryCropSlot.OPPONENT_BALLS,
            DirectoryCropSlot.ANNOUNCEMENT,
        )

        fun calibrationRegion(slot: DirectoryCropSlot): CalibrationRegion = when (slot) {
            DirectoryCropSlot.PLAYER_SPECIES -> CalibrationRegion.PLAYER_SPECIES_NAME
            DirectoryCropSlot.OPPONENT_SPECIES -> CalibrationRegion.OPPONENT_SPECIES_NAME
            DirectoryCropSlot.PLAYER_HP -> CalibrationRegion.TRAINER_HP
            DirectoryCropSlot.OPPONENT_HP -> CalibrationRegion.OPPONENT_HP
            DirectoryCropSlot.PLAYER_BALLS -> CalibrationRegion.PLAYER_POKE_BALLS
            DirectoryCropSlot.OPPONENT_BALLS -> CalibrationRegion.OPPONENT_POKE_BALLS
            DirectoryCropSlot.ANNOUNCEMENT -> CalibrationRegion.ANNOUNCEMENT
        }
    }
}
