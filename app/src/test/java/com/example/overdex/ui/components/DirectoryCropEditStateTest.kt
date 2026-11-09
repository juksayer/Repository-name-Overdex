package com.example.overdex.ui.components

import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class DirectoryCropEditStateTest {
    private val initial = AnchorRegion(.2f, .3f, .25f, .1f)
    private fun editing() = DirectoryCropEditState().enterFromDirectory(true).beginEdit(initial, 1080, 2400)

    @Test fun `only Down past last directory row enters LCD and slots follow visible row order`() {
        val directory = DirectoryCropEditState()
        assertEquals(directory, directory.enterFromDirectory(false))
        assertEquals(DirectoryCropSlot.PLAYER_SPECIES, directory.enterFromDirectory(true).selectedSlot)
        var state = directory.enterFromDirectory(true)
        val visited = mutableListOf(state.selectedSlot)
        repeat(7) {
            val moved = state.navigate(DirectoryCropEditDirection.DOWN)
            assertFalse(moved.returnToDirectory)
            state = moved.state
            visited += state.selectedSlot
        }
        assertEquals(listOf(DirectoryCropSlot.PLAYER_SPECIES, DirectoryCropSlot.OPPONENT_SPECIES,
            DirectoryCropSlot.PLAYER_HP, DirectoryCropSlot.OPPONENT_HP, DirectoryCropSlot.PLAYER_BALLS,
            DirectoryCropSlot.OPPONENT_BALLS, DirectoryCropSlot.ANNOUNCEMENT, DirectoryCropSlot.CHARGED_IMPACT_HP), visited)
        assertEquals(state, state.navigate(DirectoryCropEditDirection.DOWN).state)
        assertEquals(DirectoryCropSlot.ANNOUNCEMENT, state.navigate(DirectoryCropEditDirection.LEFT).state.selectedSlot)
    }

    @Test fun `Up from first slot and B from selected return directory without a save`() {
        val selected = DirectoryCropEditState().enterFromDirectory(true)
        for (transition in listOf(selected.navigate(DirectoryCropEditDirection.UP),
            selected.select(DirectoryCropSlot.OPPONENT_HP).cancel())) {
            assertTrue(transition.returnToDirectory)
            assertNull(transition.state.selectedSlot)
            assertNull(transition.save)
        }
        assertEquals(selected, selected.navigate(DirectoryCropEditDirection.LEFT).state)
        assertEquals(DirectoryCropEditState(), DirectoryCropEditState().cancel().state)
    }

    @Test fun `each arrow moves one original source pixel while keeping the editing slot and size`() {
        val state = editing()
        for ((direction, delta) in listOf(
            DirectoryCropEditDirection.RIGHT to (1f / 1080 to 0f),
            DirectoryCropEditDirection.LEFT to (-1f / 1080 to 0f),
            DirectoryCropEditDirection.UP to (0f to -1f / 2400),
            DirectoryCropEditDirection.DOWN to (0f to 1f / 2400),
        )) {
            val moved = state.navigate(direction)
            val region = moved.state.draft!!.region
            assertEquals(initial.x + delta.first, region.x, 0.0000001f)
            assertEquals(initial.y + delta.second, region.y, 0.0000001f)
            assertEquals(initial.width, region.width)
            assertEquals(initial.height, region.height)
            assertEquals(state.selectedSlot, moved.state.selectedSlot)
            assertFalse(moved.returnToDirectory)
            assertTrue(moved.state.dirty)
        }
        assertEquals(state, state.select(DirectoryCropSlot.OPPONENT_HP))
    }

    @Test fun `scaled drag uses fitted full source bounds and not source pixels or LCD size`() {
        val full = editing().moveByPreviewPixels(20f, -30f, 1080f, 2400f)
        val half = editing().moveByPreviewPixels(10f, -15f, 540f, 1200f)
        val halfRegion = requireNotNull(half.draft).region
        assertEquals(full.draft!!.region, halfRegion)
        assertEquals(initial.x + 20f / 1080, halfRegion.x, 0.0000001f)
        assertEquals(initial.y - 30f / 2400, halfRegion.y, 0.0000001f)
        assertEquals(initial.width, halfRegion.width)
        assertEquals(initial.height, halfRegion.height)
    }

    @Test fun `movement clamps the entire crop inside source without changing its dimensions`() {
        val state = editing()
        val topLeft = state.moveByNormalized(-10f, -10f).draft!!.region
        val bottomRight = state.moveByNormalized(10f, 10f).draft!!.region
        assertEquals(initial.copy(x = 0f, y = 0f), topLeft)
        assertEquals(initial.copy(x = 1f - initial.width, y = 1f - initial.height), bottomRight)
        val edge = DirectoryCropEditState().select(DirectoryCropSlot.ANNOUNCEMENT)
            .beginEdit(AnchorRegion(0f, 0f, 1f, 1f), 1080, 2400)
        assertEquals(edge, edge.navigate(DirectoryCropEditDirection.RIGHT).state)
    }

    @Test fun `B discards unsaved movement and A alone emits a save preserving crop size and selected slot`() {
        val editing = editing().moveByNormalized(.1f, .1f)
        val canceled = editing.cancel()
        assertNull(canceled.save)
        assertFalse(canceled.returnToDirectory)
        assertEquals(editing.selectedSlot, canceled.state.selectedSlot)
        assertFalse(canceled.state.isEditing)
        assertFalse(canceled.state.dirty)
        assertEquals(initial, canceled.state.beginEdit(initial, 1080, 2400).draft!!.region)
        val saved = editing.confirm()
        val request = requireNotNull(saved.save)
        assertEquals(DirectoryCropEditSave(DirectoryCropSlot.PLAYER_SPECIES, editing.draft!!.region), request)
        assertEquals(initial.width, request.region.width)
        assertEquals(initial.height, request.region.height)
        assertEquals(editing.selectedSlot, saved.state.selectedSlot)
        assertFalse(saved.state.isEditing)
        assertFalse(saved.returnToDirectory)
        assertTrue("Pure transition leaves the original draft available if saving fails", editing.isEditing)
        assertNull(saved.state.confirm().save)
    }

    @Test fun `invalid dimensions and nonfinite geometry do not enter or corrupt editing`() {
        val selected = DirectoryCropEditState().enterFromDirectory(true)
        for (region in listOf(initial.copy(width = 0f), initial.copy(height = -1f),
            initial.copy(width = 1.1f), initial.copy(height = Float.NaN), initial.copy(x = Float.POSITIVE_INFINITY))) {
            assertEquals(selected, selected.beginEdit(region, 1080, 2400))
        }
        assertEquals(selected, selected.beginEdit(initial, 0, 2400))
        assertEquals(selected, selected.beginEdit(initial, 1080, -1))
        val editing = editing()
        assertEquals(editing, editing.moveByNormalized(Float.NaN, 0f))
        assertEquals(editing, editing.moveByPreviewPixels(1f, 1f, 0f, 1200f))
        assertEquals(editing, editing.moveByPreviewPixels(1f, 1f, 540f, Float.POSITIVE_INFINITY))
        val clamped = selected.beginEdit(initial.copy(x = -1f, y = 2f), 1080, 2400)
        assertEquals(initial.copy(x = 0f, y = 1f - initial.height), clamped.draft!!.region)
        assertTrue(clamped.dirty)
    }

    @Test fun `each preview maps to exactly its own calibration region`() {
        assertEquals(listOf(CalibrationRegion.PLAYER_SPECIES_NAME, CalibrationRegion.OPPONENT_SPECIES_NAME,
            CalibrationRegion.TRAINER_HP, CalibrationRegion.OPPONENT_HP, CalibrationRegion.PLAYER_POKE_BALLS,
            CalibrationRegion.OPPONENT_POKE_BALLS, CalibrationRegion.ANNOUNCEMENT, CalibrationRegion.CHARGED_IMPACT_HP),
            DirectoryCropEditState.slots.map { DirectoryCropEditState.calibrationRegion(it) })
    }
}
