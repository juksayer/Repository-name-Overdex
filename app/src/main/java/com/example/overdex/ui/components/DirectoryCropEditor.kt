package com.example.overdex.ui.components

import androidx.compose.runtime.*
import com.example.overdex.battle.preview.*
import com.example.overdex.data.BattleCalibration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The profile and geometry against which a position-only edit was started. */
data class DirectoryCalibrationSnapshot(val calibration: BattleCalibration, val profileId: String?)

@Stable
class DirectoryCropEditor {
    var controls by mutableStateOf(DirectoryCropEditState())
        private set
    var frame by mutableStateOf<DirectoryCalibrationFrame?>(null)
        internal set
    var snapshot by mutableStateOf<DirectoryCalibrationSnapshot?>(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var saving by mutableStateOf(false)
        internal set
    var notice by mutableStateOf<String?>(null)
        internal set
    internal var saveDraft: () -> Unit = {}
    internal var reloadOnCancel = false
    internal var refreshVersion by mutableIntStateOf(0)
    val focused: Boolean get() = controls.selectedSlot != null
    val editing: Boolean get() = controls.isEditing

    fun enter(atLastVisibleEntry: Boolean): Boolean {
        controls = controls.enterFromDirectory(atLastVisibleEntry)
        return focused
    }
    fun select(slot: DirectoryCropSlot) {
        if (!saving) { controls = controls.select(slot); notice = null }
    }
    fun direction(direction: DirectoryCropEditDirection): Boolean {
        if (!focused) return false
        if (!saving) { controls = controls.navigate(direction).state; notice = null }
        return true
    }
    fun confirm(): Boolean {
        if (!focused) return false
        if (saving || loading) return true
        if (editing) saveDraft() else {
            val source = frame?.bitmap
            val region = controls.selectedSlot?.let { snapshot?.calibration?.directoryCropRegion(it) }
            if (source == null || source.isRecycled || region == null) {
                notice = "No full battle image saved yet. Record a battle to edit here."
            } else {
                controls = controls.beginEdit(region, source.width, source.height)
                notice = null
            }
        }
        return true
    }
    fun back(): Boolean {
        if (!focused) return false
        if (!saving) {
            val cancelledDraft = editing
            controls = controls.cancel().state
            notice = null
            if (cancelledDraft && reloadOnCancel) { refreshVersion++; reloadOnCancel = false }
        }
        return true
    }
    fun move(dx: Float, dy: Float) {
        if (!saving) controls = controls.moveByNormalized(dx, dy)
    }
    internal fun saved(next: DirectoryCropEditState, calibration: BattleCalibration) {
        controls = next
        snapshot = snapshot?.copy(calibration = calibration)
    }
}

/** Source images are decoded once on entry, never on each arrow press or feed poll. */
@Composable
fun rememberDirectoryCropEditor(
    enabled: Boolean,
    loadFrame: suspend () -> DirectoryCalibrationFrame?,
    loadCalibration: () -> DirectoryCalibrationSnapshot,
    saveCalibration: suspend (DirectoryCalibrationSnapshot, DirectoryCropEditSave) -> Boolean,
): DirectoryCropEditor {
    val editor = remember { DirectoryCropEditor() }
    val currentLoadFrame by rememberUpdatedState(loadFrame)
    val currentLoadCalibration by rememberUpdatedState(loadCalibration)
    val currentSave by rememberUpdatedState(saveCalibration)
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled, editor.focused, editor.refreshVersion, !enabled && editor.saving) {
        if (!enabled || !editor.focused) {
            if (!editor.saving) editor.controlsReset()
            return@LaunchedEffect
        }
        editor.loading = true
        try {
            editor.snapshot = withContext(Dispatchers.IO) { currentLoadCalibration() }
            if (editor.frame == null) editor.frame = currentLoadFrame()
            if (editor.frame == null) editor.notice = "No full battle image saved yet. Record a battle to edit here."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { editor.notice = "Could not load the battle reference. Return to the directory and try again." }
        finally { editor.loading = false }
    }
    editor.saveDraft = {
        val expected = editor.snapshot
        val transition = editor.controls.confirm()
        val request = transition.save
        if (expected != null && request != null && !editor.saving) {
            // Claim the write before dispatch so repeated A cannot enqueue two saves.
            editor.saving = true
            scope.launch {
                try {
                    if (currentSave(expected, request)) {
                        editor.saved(transition.state, expected.calibration.withDirectoryCropRegion(request.slot, request.region))
                        editor.notice = "Position saved"
                    } else {
                        editor.reloadOnCancel = true
                        editor.notice = "Could not save. Calibration may have changed; cancel and try again."
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { editor.notice = "Could not save this position. Your draft is still open." }
                finally { editor.saving = false }
            }
        }
    }
    return editor
}

private fun DirectoryCropEditor.controlsReset() {
    // Returning to the directory releases the optional full reference; archived previews remain unchanged.
    if (focused) back()
    if (focused) back()
    frame = null
    snapshot = null
    notice = null
    loading = false
}
