package com.example.overdex.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.overdex.battle.preview.DirectoryCropSlot
import com.example.overdex.model.navigation.InstrumentCommand

/** Calibration is an explicit LCD visit; ordinary tree navigation never claims crop focus. */
@Stable
class DirectoryLcdController {
    var calibrationOpen by mutableStateOf(false)
        private set

    fun activate(command: InstrumentCommand, editor: DirectoryCropEditor): Boolean {
        if (command != InstrumentCommand.OpenDirectoryCalibration) return false
        open(editor)
        return true
    }

    fun holdA(command: InstrumentCommand?, editor: DirectoryCropEditor): Boolean {
        if (command != InstrumentCommand.LaunchDroidball) return false
        open(editor)
        return true
    }

    private fun open(editor: DirectoryCropEditor) {
        if (!calibrationOpen && !editor.saving) {
            calibrationOpen = true
            editor.select(DirectoryCropSlot.PLAYER_SPECIES)
        }
    }

    fun direction(direction: DirectoryCropEditDirection, editor: DirectoryCropEditor): Boolean {
        if (!calibrationOpen) return false
        editor.direction(direction)
        if (!editor.focused && !editor.saving) calibrationOpen = false
        return true
    }

    fun confirm(editor: DirectoryCropEditor): Boolean {
        if (!calibrationOpen) return false
        if (!editor.focused && !editor.saving) editor.select(DirectoryCropSlot.PLAYER_SPECIES)
        else editor.confirm()
        return true
    }

    /** Cancel a draft first; leaving crop focus also restores the selection preview. */
    fun back(editor: DirectoryCropEditor): Boolean {
        if (!calibrationOpen) return false
        editor.back()
        if (!editor.focused && !editor.saving) calibrationOpen = false
        return true
    }

    /** A different workflow may open only after an explicit draft cancel or successful save. */
    fun close(editor: DirectoryCropEditor): Boolean {
        if (editor.editing || editor.saving) return false
        if (editor.focused) editor.back()
        calibrationOpen = false
        return true
    }
}
